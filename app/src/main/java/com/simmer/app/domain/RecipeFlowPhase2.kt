package com.simmer.app.domain

import com.simmer.app.domain.model.Method
import com.simmer.app.domain.model.MethodOption
import com.simmer.app.domain.model.Recipe
import com.simmer.app.domain.model.Selection

// ---------------------------------------------------------------------------
// Appliance filtering
// ---------------------------------------------------------------------------

/** The appliance a Method option needs, by option value. Options not listed need none. */
private val OPTION_APPLIANCE = mapOf(
    "pot" to "hob",
    "hob" to "hob",
    "oven" to "oven",
    "microwave" to "microwave",
    "slow" to "slowcooker",
    "cooker" to "ricecooker",
    "air" to "airfryer",
)

/**
 * The Methods of [recipe] with options limited to the user's kitchen.
 *
 * Kitchen mode "free" offers everything. A Method none of whose options the
 * kitchen can serve keeps all of them, so the cook is never left without a choice.
 */
fun availableMethods(recipe: Recipe, appliances: Set<String>, kitchenMode: String): List<Method> {
    if (kitchenMode == "free") return recipe.methods
    return recipe.methods.map { method ->
        val usable = method.options.filter { OPTION_APPLIANCE[it.value].let { a -> a == null || a in appliances } }
        if (usable.isEmpty()) method else method.copy(options = usable)
    }
}

/** Option description with °C temperatures shown in the user's [units]. */
fun methodDescription(option: MethodOption, units: String): String = temp(option.d, units)

/**
 * Replaces any Method pick that is no longer offered (kitchen changed since it
 * was made) with the first offered option. Other choices are left alone.
 */
fun coerceMethodChoices(selection: Selection, available: List<Method>): Selection {
    val fixed = available.mapNotNull { m ->
        val picked = selection.choice[m.id]
        if (m.options.isEmpty() || m.options.any { it.value == picked }) null else m.id to m.options.first().value
    }.toMap()
    return if (fixed.isEmpty()) selection else selection.copy(choice = selection.choice + fixed)
}

// ---------------------------------------------------------------------------
// Step plan
// ---------------------------------------------------------------------------

/** How many preparation and cooking Steps the current choices lead to. */
data class StepPlan(val prep: Int, val cook: Int)

fun stepPlan(recipe: Recipe, servings: Int, selection: Selection): StepPlan {
    val steps = resolve(recipe, servings, selection).steps
    return StepPlan(prep = steps.count { it.phase == "prep" }, cook = steps.count { it.phase == "cook" })
}
