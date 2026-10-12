package com.simmer.app

import com.simmer.app.domain.Phase1State
import com.simmer.app.domain.PlannedChoice
import com.simmer.app.domain.defaults
import com.simmer.app.domain.model.Selection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase1AddToListTest {
    private val chilli = BuiltInRecipes.chilli()
    private fun fresh() = Phase1State.initial(chilli, planned = null)
    private fun Phase1State.asAdded() = copy(added = PlannedChoice(servings, selection))

    // --- initial state -----------------------------------------------------

    @Test fun `a new flow starts at the recipe base with default selection and nothing on the list`() {
        val s = fresh()
        assertEquals(4, s.servings)
        assertEquals(defaults(chilli), s.selection)
        assertFalse(s.isOnList)
    }

    @Test fun `reopening a planned recipe restores its servings and selection and shows on-list`() {
        val sel = Selection(choice = defaults(chilli).choice + ("mince" to "plant"), add = mapOf("beer" to true))
        val s = Phase1State.initial(chilli, PlannedChoice(servings = 6, selection = sel))
        assertEquals(6, s.servings)
        assertEquals(sel, s.selection)
        assertTrue(s.isOnList)
    }

    // --- shopping entries --------------------------------------------------

    @Test fun `only ingredients with an aisle are listed, each carrying the recipe title as note`() {
        val entries = fresh().shoppingEntries()
        // 17 visible ingredients by default, minus water which has no aisle
        assertEquals(16, entries.size)
        assertTrue(entries.none { it.ingredientId == "wa" })
        assertTrue(entries.all { it.note == "Chilli con carne" })
    }

    @Test fun `entries use scaled amounts and follow the current selection`() {
        val s = fresh().withServings(8).withSwap("mince", "plant").withAddIn("beer", true)
        val byId = s.shoppingEntries().associateBy { it.ingredientId }
        assertEquals("800 g", byId.getValue("mp").amount)
        assertEquals("400 ml", byId.getValue("be").amount)
        assertNull(byId["mb"])
        assertEquals(17, byId.size)
    }

    @Test fun `entries are grouped by aisle`() {
        val aisles = fresh().shoppingEntries().map { it.aisle }
        // each aisle forms one contiguous run
        val runs = aisles.fold(emptyList<String>()) { acc, a -> if (acc.lastOrNull() == a) acc else acc + a }
        assertEquals(runs.size, runs.toSet().size)
    }

    // --- on-list tracking --------------------------------------------------

    @Test fun `adding to the list marks the state as on-list`() {
        assertTrue(fresh().asAdded().isOnList)
    }

    @Test fun `changing servings after adding reverts to add-to-list`() {
        assertFalse(fresh().asAdded().withServings(5).isOnList)
    }

    @Test fun `changing a swap after adding reverts to add-to-list`() {
        assertFalse(fresh().asAdded().withSwap("mince", "plant").isOnList)
    }

    @Test fun `toggling an add-in after adding reverts to add-to-list`() {
        assertFalse(fresh().asAdded().withAddIn("choc", true).isOnList)
    }

    @Test fun `restoring the added choices flips back to on-list`() {
        val s = fresh().asAdded().withServings(5).withServings(4)
        assertTrue(s.isOnList)
    }

    @Test fun `an add-in toggled off counts the same as one never toggled`() {
        val added = fresh().asAdded().added
        val toggledOff = fresh().withAddIn("beer", true).withAddIn("beer", false)
        assertTrue(toggledOff.copy(added = added).isOnList)
    }

    @Test fun `a restored draft overrides the planned choice`() {
        val planned = PlannedChoice(6, defaults(chilli))
        val draftSel = Selection(defaults(chilli).choice, mapOf("beer" to true))
        val s = Phase1State.initial(chilli, planned, draft = PlannedChoice(9, draftSel))
        assertEquals(9, s.servings)
        assertEquals(draftSel, s.selection)
        assertFalse(s.isOnList)
        assertEquals(planned, s.added)
    }

    // --- resolved view -----------------------------------------------------

    @Test fun `ingredient caption counts visible items and servings`() {
        val s = fresh().withServings(6)
        assertEquals(17, s.resolved().ingredients.size)
        assertEquals(6, s.servings)
    }
}
