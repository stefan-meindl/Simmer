package com.simmer.app.domain

import com.simmer.app.domain.model.Recipe
import com.simmer.app.domain.model.Selection

/** Servings and selection saved when a recipe is added to the Shopping list (a Planned Recipe). */
data class PlannedChoice(val servings: Int, val selection: Selection)

/** One Shopping list entry derived from a recipe ingredient. */
data class ShoppingEntry(
    val ingredientId: String,
    val name: String,
    val amount: String,
    val aisle: String,
    /** Title of the source recipe. */
    val note: String,
)

/**
 * Immutable state of recipe flow phase 1.
 *
 * [added] is the choice last saved with "Add to list"; the screen shows
 * "On your list" only while the current choice equals it.
 */
data class Phase1State(
    val recipe: Recipe,
    val servings: Int,
    val selection: Selection,
    val added: PlannedChoice?,
) {
    val isOnList: Boolean get() = added == PlannedChoice(servings, selection)

    fun withServings(n: Int) = copy(servings = n.coerceIn(MIN_SERVINGS, MAX_SERVINGS))

    fun withSwap(variationId: String, value: String) =
        copy(selection = selection.copy(choice = selection.choice + (variationId to value)))

    fun withAddIn(variationId: String, on: Boolean) =
        copy(selection = selection.copy(add = selection.add + (variationId to on)))

    fun markAdded() = copy(added = PlannedChoice(servings, selection))

    fun resolved(units: String = "metric", spices: String = "spoons", lang: String = "en") =
        resolve(recipe, servings, selection, units, spices, lang)

    /** Aisle-having ingredients for the current choice, grouped by aisle (first-seen order). */
    fun shoppingEntries(units: String = "metric", spices: String = "spoons"): List<ShoppingEntry> =
        resolved(units, spices).ingredients
            .filter { it.aisle.isNotBlank() }
            .map { ShoppingEntry(it.id, it.name, it.amount, it.aisle, recipe.title) }
            .groupBy { it.aisle }
            .values.flatten()

    companion object {
        fun initial(recipe: Recipe, planned: PlannedChoice?) = Phase1State(
            recipe = recipe,
            servings = planned?.servings ?: recipe.base,
            selection = planned?.selection ?: defaults(recipe),
            added = planned,
        )
    }
}
