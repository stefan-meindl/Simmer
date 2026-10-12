package com.simmer.app.domain

import com.simmer.app.domain.model.Ingredient
import com.simmer.app.domain.model.Recipe
import com.simmer.app.domain.model.Selection
import com.simmer.app.domain.model.Variation

// ---------------------------------------------------------------------------
// Variations
// ---------------------------------------------------------------------------

/**
 * Label shown on an add-in CheckRow: "+ <amount>" scaled to [servings].
 * The amount is that of the add-in's ingredient at the recipe's base.
 */
fun addInAmount(
    recipe: Recipe,
    variation: Variation,
    servings: Int,
    units: String = "metric",
    spices: String = "spoons",
    selection: Selection = defaults(recipe),
    lang: String = "en",
): String {
    // Resolve with the add-in on so rounding and units match the ingredient list.
    val on = selection.copy(add = selection.add + (variation.id to true))
    val ingredient = resolve(recipe, servings, on, units, spices, lang).ingredients.first { it.id == variation.ing }
    return "+ " + ingredient.amount
}

/**
 * True when [ing] is only shown because the user picked a non-default swap
 * option or switched an add-in on ("Your pick" tag).
 */
fun isYourPick(recipe: Recipe, ingredient: Ingredient, selection: Selection): Boolean {
    val condition = ingredient.ifCondition ?: return false
    if (condition.contains('!')) return false
    return recipe.variations.any { variation ->
        if (variation.kind == "add") condition == "add:${variation.id}"
        else selection.choice[variation.id] != variation.def &&
            condition == "${variation.id}=${selection.choice[variation.id]}"
    }
}

// ---------------------------------------------------------------------------
// Servings and progress
// ---------------------------------------------------------------------------

const val MIN_SERVINGS = 1
const val MAX_SERVINGS = 12

/** Applies [delta] to [current], clamped to [MIN_SERVINGS]..[MAX_SERVINGS]. */
fun stepServings(current: Int, delta: Int): Int =
    (current + delta).coerceIn(MIN_SERVINGS, MAX_SERVINGS)

enum class SegmentState { Done, Current, Todo }

/** One segment of the recipe-flow progress indicator. */
data class ProgressSegment(val phase: Int, val state: SegmentState) {
    /** Only completed segments can be tapped (to go back). */
    val tappable: Boolean get() = state == SegmentState.Done
}

/**
 * Phase ids (1 Servings, 2 Preparation, 3 Cooking) shown in the flow chrome. Phase 2 is left out
 * when [skipsMethods] (no Method has a choice, see hasSelectableMethod).
 */
fun flowPhases(skipsMethods: Boolean = false): List<Int> =
    if (skipsMethods) listOf(1, 3) else listOf(1, 2, 3)

fun flowPhaseCount(skipsMethods: Boolean = false): Int = flowPhases(skipsMethods).size

/** 1-based position of [phase] among the shown phases ("Step n of m"). */
fun flowPhaseNumber(phase: Int, skipsMethods: Boolean = false): Int =
    flowPhases(skipsMethods).indexOf(phase) + 1

fun progressSegments(current: Int, skipsMethods: Boolean = false): List<ProgressSegment> =
    flowPhases(skipsMethods).map { p ->
        ProgressSegment(
            phase = p,
            state = when {
                p < current -> SegmentState.Done
                p == current -> SegmentState.Current
                else -> SegmentState.Todo
            },
        )
    }
