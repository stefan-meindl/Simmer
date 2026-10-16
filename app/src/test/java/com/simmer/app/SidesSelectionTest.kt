package com.simmer.app

import com.simmer.app.data.decodeSelection
import com.simmer.app.data.encodeSelection
import com.simmer.app.domain.Phase1State
import com.simmer.app.domain.PlannedChoice
import com.simmer.app.domain.defaults
import com.simmer.app.domain.model.BodySegment
import com.simmer.app.domain.model.Selection
import com.simmer.app.domain.resolve
import com.simmer.app.domain.selectionLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SidesSelectionTest {
    private val recipe = BuiltInRecipes.chilliWithRice()
    private val main = BuiltInRecipes.chilli()
    private fun fresh() = Phase1State.initial(recipe, planned = null)
    private fun ticked() = fresh().withSide("rice", true)

    // --- Selection / state ---------------------------------------------------

    @Test fun `sides default to none and old positional selections still work`() {
        assertEquals(emptyMap<String, Boolean>(), Selection().sides)
        assertEquals(Selection(mapOf("a" to "b"), mapOf("c" to true), emptyMap()), Selection(mapOf("a" to "b"), mapOf("c" to true)))
    }

    @Test fun `a side toggled off counts the same as one never ticked`() {
        val planned = PlannedChoice(4, defaults(recipe))
        val s = Phase1State.initial(recipe, planned).withSide("rice", true).withSide("rice", false)
        assertTrue(s.isOnList)
        assertFalse(Phase1State.initial(recipe, planned).withSide("rice", true).isOnList)
    }

    @Test fun `ticking a side or choosing its method changes the cooking plan, toggling off does not`() {
        val base = fresh()
        assertTrue(base.changesCookingPlan(base.withSide("rice", true)))
        assertFalse(base.changesCookingPlan(base.withSide("rice", false)))
        assertTrue(ticked().changesCookingPlan(ticked().withSideMethod("rice", "rice", "cooker")))
    }

    @Test fun `clearSides unticks every side`() {
        val s = ticked().clearSides()
        assertTrue(s.selection.sides.values.none { it })
        assertFalse(fresh().changesCookingPlan(s))
    }

    @Test fun `withSideMethod stores the choice under the namespaced key`() {
        assertEquals("cooker", ticked().withSideMethod("rice", "rice", "cooker").selection.choice["s_rice_rice"])
    }

    // --- persistence -----------------------------------------------------------

    @Test fun `old selection json without sides decodes equal to the old selection`() {
        val sel = decodeSelection("""{"choice":{"mince":"plant"},"add":{"beer":true}}""")
        assertEquals(Selection(mapOf("mince" to "plant"), mapOf("beer" to true)), sel)
    }

    @Test fun `sides round-trip through encode and decode`() {
        val sel = Selection(mapOf("s_rice_rice" to "cooker"), emptyMap(), mapOf("rice" to true))
        assertEquals(sel, decodeSelection(encodeSelection(sel)))
    }

    // --- resolve -----------------------------------------------------------------

    @Test fun `unticked or unknown sides leave the resolved recipe untouched`() {
        val sel = defaults(recipe)
        assertEquals(resolve(main, 4, sel), resolve(recipe, 4, sel))
        assertEquals(resolve(main, 4, sel), resolve(recipe, 4, sel.copy(sides = mapOf("ghost" to true, "rice" to false))))
    }

    @Test fun `a ticked side appends namespaced ingredients after the main's, scaled by servings`() {
        val sel = ticked().selection
        val r = resolve(recipe, 8, sel)
        val mainCount = resolve(main, 8, sel).ingredients.size
        assertEquals(listOf("s_rice_ri", "s_rice_wa"), r.ingredients.drop(mainCount).map { it.id })
        assertEquals("400 g", r.ingredients.first { it.id == "s_rice_ri" }.amount)
    }

    @Test fun `side steps follow the main's within each phase with prefixed titles and the default method`() {
        val sel = ticked().selection
        val r = resolve(recipe, 4, sel)
        val mainSteps = resolve(main, 4, sel).steps
        val prepEnd = mainSteps.count { it.phase == "prep" }
        assertEquals("Rice: Rinse the rice", r.steps[prepEnd].title)
        assertEquals("s_rice_rinse", r.steps[prepEnd].id)
        assertEquals("Rice: Cook the rice", r.steps.last().title)
        assertEquals(mainSteps.size + 2, r.steps.size)
    }

    @Test fun `side method choice picks the matching step, needs and body links are namespaced`() {
        val sel = ticked().withSideMethod("rice", "rice", "cooker").selection
        val last = resolve(recipe, 4, sel).steps.last()
        assertEquals("Rice: Start the rice cooker", last.title)
        assertEquals(listOf("s_rice_rinse"), last.needs.map { it.id })
        assertTrue(last.segs.any { it is BodySegment.IngredientLinkSegment && it.id == "s_rice_ri" })
    }

    @Test fun `step ids and ingredient ids stay unique with sides`() {
        val r = resolve(recipe, 4, ticked().selection)
        assertEquals(r.steps.size, r.steps.map { it.id }.toSet().size)
        assertEquals(r.ingredients.size, r.ingredients.map { it.id }.toSet().size)
    }

    // --- shopping / summary -------------------------------------------------------

    @Test fun `a ticked side's ingredients join the shopping entries and drop when deselected`() {
        val withSide = ticked().shoppingEntries().associateBy { it.ingredientId }
        assertEquals("200 g", withSide.getValue("s_rice_ri").amount)
        assertEquals("Pantry", withSide.getValue("s_rice_ri").aisle)
        assertNull(withSide["s_rice_wa"])
        assertNull(ticked().withSide("rice", false).shoppingEntries().firstOrNull { it.ingredientId.startsWith("s_rice") })
    }

    @Test fun `selection labels append ticked side labels`() {
        assertEquals(emptyList<String>(), selectionLabels(recipe, defaults(recipe)))
        assertEquals(listOf("Rice"), selectionLabels(recipe, ticked().selection))
    }
}
