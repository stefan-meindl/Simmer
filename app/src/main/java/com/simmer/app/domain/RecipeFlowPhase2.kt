package com.simmer.app.domain

import com.simmer.app.domain.model.Method
import com.simmer.app.domain.model.MethodOption
import com.simmer.app.domain.model.Recipe
import com.simmer.app.domain.model.Selection

// ---------------------------------------------------------------------------
// Method descriptions
// ---------------------------------------------------------------------------

/** Option description with °C temperatures shown in the user's [units]. */
fun methodDescription(option: MethodOption, units: String): String = temp(option.d, units)

// ---------------------------------------------------------------------------
// Step plan
// ---------------------------------------------------------------------------

/** How many preparation and cooking Steps the current choices lead to. */
data class StepPlan(val prep: Int, val cook: Int)

fun stepPlan(recipe: Recipe, servings: Int, selection: Selection): StepPlan {
    val steps = resolve(recipe, servings, selection).steps
    return StepPlan(prep = steps.count { it.phase == "prep" }, cook = steps.count { it.phase == "cook" })
}

// ---------------------------------------------------------------------------
// Skipping Flow Phase 2
// ---------------------------------------------------------------------------

/**
 * Whether Flow Phase 2 has anything to ask: at least one Method offers more than one option.
 * When false the cooking flow goes from Flow Phase 1 straight to Flow Phase 3.
 */
fun hasSelectableMethod(recipe: Recipe): Boolean = recipe.methods.any { it.options.size > 1 }
