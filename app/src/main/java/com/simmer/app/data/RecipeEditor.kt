package com.simmer.app.data

import com.simmer.app.domain.KitchenMode
import com.simmer.app.domain.RecipeDraft
import com.simmer.app.domain.model.Recipe
import com.simmer.app.domain.parseChatbotReply
import com.simmer.app.domain.stubConvert
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

/**
 * The Add recipe flow's writes: Convert puts a new recipe in the [RecipeDraftStore], [discard] takes it
 * back out (Edit text, leaving the screen), Save recipe stores it as a User Recipe.
 */
@Singleton
class AddRecipeSession @Inject constructor(
    private val recipes: RecipeWriter,
    private val drafts: RecipeDraftStore,
) {
    /** Replaces the draft with the conversion of [text] as recipe [id]; false (no draft) when it cannot be converted. */
    fun convert(text: String, id: String): Boolean {
        val recipe = stubConvert(text, id)
        drafts.set(recipe)
        return recipe != null
    }

    /** Puts the chatbot [reply] in the draft store as recipe [id]; false (draft untouched) when it holds no usable recipe. */
    fun addFromReply(reply: String, id: String, appliances: Set<String>, kitchenMode: KitchenMode): Boolean {
        val recipe = parseChatbotReply(reply, id, appliances, kitchenMode) ?: return false
        drafts.set(recipe)
        return true
    }

    /** Drops the draft; the pasted text is not kept here. */
    fun discard() = drafts.set(null)

    /** Stores the draft (with any edits) as a User Recipe and clears it; null when there is no draft. */
    suspend fun save(): Recipe? {
        val draft = drafts.current.value ?: return null
        val recipe = draft.copy(mine = true, edited = false)
        recipes.save(recipe)
        drafts.set(null)
        return recipe
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
