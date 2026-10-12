package com.simmer.app

import com.simmer.app.domain.methodDescription
import com.simmer.app.domain.model.Recipe
import com.simmer.app.domain.model.Selection
import com.simmer.app.domain.model.Step
import com.simmer.app.domain.stepPlan
import org.junit.Assert.assertEquals
import org.junit.Test

class Phase2MethodsTest {
    private val chilli = BuiltInRecipes.chilli()

    // --- description ---------------------------------------------------------

    @Test fun `description temperatures follow the unit preference`() {
        val oven = chilli.methods.first { it.id == "chilli" }.options.first { it.value == "oven" }
        assertEquals("Covered at 150°C. Richer, no stirring.", methodDescription(oven, "metric"))
        assertEquals("Covered at 300°F. Richer, no stirring.", methodDescription(oven, "imperial"))
    }

    // --- step plan -----------------------------------------------------------

    @Test fun `plan counts preparation and cooking steps for the selection`() {
        val recipe = Recipe(
            id = "r", title = "R", category = "Mains", time = 10, difficulty = "Easy", base = 2,
            steps = listOf(
                Step("p1", "prep", "a", 60, "x"),
                Step("p2", "prep", "b", 60, "x", ifCondition = "m=b"),
                Step("c1", "cook", "c", 60, "x"),
                Step("c2", "cook", "d", 60, "x", ifCondition = "m=a"),
                Step("c3", "cook", "e", 60, "x", ifCondition = "m=b"),
            ),
        )
        val a = stepPlan(recipe, 2, Selection(choice = mapOf("m" to "a")))
        assertEquals(1 to 2, a.prep to a.cook)
        val b = stepPlan(recipe, 2, Selection(choice = mapOf("m" to "b")))
        assertEquals(2 to 2, b.prep to b.cook)
    }
}
