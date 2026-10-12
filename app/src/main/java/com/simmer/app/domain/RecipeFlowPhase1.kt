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
    r: Recipe,
    v: Variation,
    servings: Int,
    units: String = "metric",
    spices: String = "spoons",
): String {
    val ing = r.ingredients.first { it.id == v.ing }
    val scaled = ing.quantity * servings / r.base
    return "+ " + fmtQty(scaled, ing.unit, ing, units, spices)
}

/**
 * True when [ing] is only shown because the user picked a non-default swap
 * option or switched an add-in on ("Your pick" tag).
 */
fun isYourPick(r: Recipe, ing: Ingredient, sel: Selection): Boolean {
    val c = ing.ifCondition ?: return false
    if (c.contains('!')) return false
    return r.variations.any { v ->
        if (v.kind == "add") c == "add:${v.id}"
        else sel.choice[v.id] != v.def && c == "${v.id}=${sel.choice[v.id]}"
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

/** One segment of the 3-phase recipe-flow progress indicator. */
data class ProgressSegment(val phase: Int, val state: SegmentState) {
    /** Only completed segments can be tapped (to go back). */
    val tappable: Boolean get() = state == SegmentState.Done
}

const val FLOW_PHASE_COUNT = 3

fun progressSegments(current: Int): List<ProgressSegment> =
    (1..FLOW_PHASE_COUNT).map { p ->
        ProgressSegment(
            phase = p,
            state = when {
                p < current -> SegmentState.Done
                p == current -> SegmentState.Current
                else -> SegmentState.Todo
            },
        )
    }
