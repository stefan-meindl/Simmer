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
 * "Remove from list" only while the current choice equals it.
 */
data class Phase1State(
    val recipe: Recipe,
    val servings: Int,
    val selection: Selection,
    val added: PlannedChoice?,
) {
    /** An add-in toggled off counts the same as one never toggled. */
    val isOnList: Boolean
        get() = added?.let { it.servings == servings && it.selection.normalised() == selection.normalised() } == true

    /** Callers clamp via [stepServings]. */
    fun withServings(servings: Int) = copy(servings = servings)

    fun withSwap(variationId: String, value: String) =
        copy(selection = selection.copy(choice = selection.choice + (variationId to value)))

    /** The servings and Selection as a [PlannedChoice]. */
    fun toChoice() = PlannedChoice(servings, selection)

    /** True when [other] cooks differently, so Cooking Progress made on this state no longer applies. */
    fun changesCookingPlan(other: Phase1State): Boolean =
        servings != other.servings || selection.normalised() != other.selection.normalised()

    fun withAddIn(variationId: String, on: Boolean) =
        copy(selection = selection.copy(add = selection.add + (variationId to on)))

    fun resolved(units: String = "metric", spices: String = "spoons", lang: String = "en") =
        resolve(recipe, servings, selection, units, spices, lang)

    /** Aisle-having ingredients for the current choice, grouped by aisle (first-seen order). */
    fun shoppingEntries(
        units: String = "metric",
        spices: String = "spoons",
        lang: String = "en",
    ): List<ShoppingEntry> =
        resolved(units, spices, lang).ingredients
            .filter { it.aisle.isNotBlank() }
            .map { ShoppingEntry(it.id, it.name, it.amount, it.aisle, recipe.title) }
            .groupBy { it.aisle }
            .values.flatten()

    companion object {
        /** [draft] is an unsaved in-progress choice (restored after process death); it wins over [planned]. */
        fun initial(recipe: Recipe, planned: PlannedChoice?, draft: PlannedChoice? = null): Phase1State {
            val start = draft ?: planned
            return Phase1State(
                recipe = recipe,
                servings = start?.servings ?: recipe.base,
                selection = start?.selection ?: defaults(recipe),
                added = planned,
            )
        }
    }
}

private fun Selection.normalised() = copy(add = add.filterValues { it })
