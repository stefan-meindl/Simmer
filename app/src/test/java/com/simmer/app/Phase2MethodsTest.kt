package com.simmer.app

import com.simmer.app.domain.DEFAULT_APPLIANCES
import com.simmer.app.domain.availableMethods
import com.simmer.app.domain.coerceMethodChoices
import com.simmer.app.domain.defaults
import com.simmer.app.domain.methodDescription
import com.simmer.app.domain.model.Method
import com.simmer.app.domain.model.Recipe
import com.simmer.app.domain.model.Selection
import com.simmer.app.domain.model.Step
import com.simmer.app.domain.stepPlan
import org.junit.Assert.assertEquals
import org.junit.Test

class Phase2MethodsTest {
    private val chilli = BuiltInRecipes.chilli()

    private fun optionValues(methods: List<Method>, id: String) =
        methods.first { it.id == id }.options.map { it.value }

    // --- appliance filtering -------------------------------------------------

    @Test fun `default appliances are hob, oven and microwave`() {
        assertEquals(setOf("hob", "oven", "microwave"), DEFAULT_APPLIANCES)
    }

    @Test fun `default kitchen hides the slow cooker and rice cooker options`() {
        val methods = availableMethods(chilli, DEFAULT_APPLIANCES, "medium")
        assertEquals(listOf("pot", "oven"), optionValues(methods, "chilli"))
        assertEquals(listOf("pot", "microwave"), optionValues(methods, "rice"))
    }

    @Test fun `owning a slow cooker brings its option back`() {
        val methods = availableMethods(chilli, DEFAULT_APPLIANCES + "slowcooker", "medium")
        assertEquals(listOf("pot", "oven", "slow"), optionValues(methods, "chilli"))
    }

    @Test fun `strict mode filters like medium`() {
        val methods = availableMethods(chilli, DEFAULT_APPLIANCES, "strict")
        assertEquals(listOf("pot", "oven"), optionValues(methods, "chilli"))
    }

    @Test fun `free mode offers every option`() {
        val methods = availableMethods(chilli, emptySet(), "free")
        assertEquals(listOf("pot", "oven", "slow"), optionValues(methods, "chilli"))
    }

    @Test fun `options that need no appliance are always offered`() {
        val methods = availableMethods(BuiltInRecipes.tagliatelle(), emptySet(), "medium")
        assertEquals(listOf("dried", "fresh"), optionValues(methods, "pasta"))
    }

    @Test fun `a method with no usable option still offers all its options`() {
        // chilli offers pot/oven/slow; a microwave-only kitchen matches none of them.
        val methods = availableMethods(chilli, setOf("microwave"), "medium")
        assertEquals(listOf("pot", "oven", "slow"), optionValues(methods, "chilli"))
    }

    // --- description ---------------------------------------------------------

    @Test fun `description temperatures follow the unit preference`() {
        val oven = chilli.methods.first { it.id == "chilli" }.options.first { it.value == "oven" }
        assertEquals("Covered at 150°C. Richer, no stirring.", methodDescription(oven, "metric"))
        assertEquals("Covered at 300°F. Richer, no stirring.", methodDescription(oven, "imperial"))
    }

    // --- selection stays valid -----------------------------------------------

    @Test fun `a hidden default pick falls back to the first visible option`() {
        val available = availableMethods(chilli, setOf("oven", "microwave"), "medium")
        val picked = coerceMethodChoices(defaults(chilli), available)
        assertEquals("oven", picked.choice["chilli"])
        assertEquals("microwave", picked.choice["rice"])
    }

    @Test fun `visible picks and non-method choices are kept`() {
        val available = availableMethods(chilli, DEFAULT_APPLIANCES, "medium")
        val sel = defaults(chilli).let { it.copy(choice = it.choice + ("chilli" to "oven")) }
        assertEquals(sel, coerceMethodChoices(sel, available))
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
