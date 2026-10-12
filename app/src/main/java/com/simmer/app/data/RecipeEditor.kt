package com.simmer.app.data

import com.simmer.app.domain.RecipeDraft
import com.simmer.app.domain.model.Recipe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Where recipes are stored; [RecipeRepository] in the app. */
interface RecipeWriter {
    suspend fun get(id: String): Recipe?
    suspend fun save(recipe: Recipe)
    suspend fun delete(id: String)
    suspend fun restoreBuiltIn(id: String)
}

/**
 * The Add recipe review's unsaved recipe. Edit recipe opened without a recipe id works on this
 * recipe and writes the result back, so the review shows the updated draft.
 */
@Singleton
class RecipeDraftStore @Inject constructor() {
    private val state = MutableStateFlow<Recipe?>(null)
    val current: StateFlow<Recipe?> = state

    fun set(recipe: Recipe?) {
        state.value = recipe
    }
}

/** What Edit recipe's Save changes, Restore original and Delete recipe do to stored state. */
@Singleton
class RecipeEditor @Inject constructor(
    private val recipes: RecipeWriter,
    private val shopping: ShoppingRepository,
    private val drafts: RecipeDraftStore,
) {
    /** The form for recipe [id], or for the Add recipe draft when [id] is null; null when there is none. */
    suspend fun open(id: String?): RecipeDraft? =
        if (id == null) drafts.current.value?.let { RecipeDraft.from(it).copy(stored = false) }
        else recipes.get(id)?.let { RecipeDraft.from(it) }

    /**
     * Built-in recipes are saved as a local override (`edited = true`); User Recipes are replaced
     * in place. A draft goes back to the [RecipeDraftStore].
     */
    suspend fun save(draft: RecipeDraft) {
        val recipe = draft.toRecipe()
        if (draft.stored) recipes.save(recipe.copy(edited = !recipe.mine)) else drafts.set(recipe)
    }

    /** Removes a built-in recipe's override, bringing the original back. */
    suspend fun restoreOriginal(id: String) {
        val recipe = recipes.get(id) ?: return
        if (!recipe.mine) recipes.restoreBuiltIn(id)
    }

    /** Deletes a User Recipe with its Planned state and Shopping list items. Built-ins are never deleted. */
    suspend fun delete(id: String) {
        val recipe = recipes.get(id) ?: return
        if (!recipe.mine) return
        recipes.delete(id)
        shopping.removeRecipe(id)
    }
}
