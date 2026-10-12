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
        assertTrue(fresh().markAdded().isOnList)
    }

    @Test fun `changing servings after adding reverts to add-to-list`() {
        assertFalse(fresh().markAdded().withServings(5).isOnList)
    }

    @Test fun `changing a swap after adding reverts to add-to-list`() {
        assertFalse(fresh().markAdded().withSwap("mince", "plant").isOnList)
    }

    @Test fun `toggling an add-in after adding reverts to add-to-list`() {
        assertFalse(fresh().markAdded().withAddIn("choc", true).isOnList)
    }

    @Test fun `restoring the added choices flips back to on-list`() {
        val s = fresh().markAdded().withServings(5).withServings(4)
        assertTrue(s.isOnList)
    }

    @Test fun `servings are clamped between 1 and 12`() {
        assertEquals(12, fresh().withServings(40).servings)
        assertEquals(1, fresh().withServings(0).servings)
    }

    // --- resolved view -----------------------------------------------------

    @Test fun `ingredient caption counts visible items and servings`() {
        val s = fresh().withServings(6)
        assertEquals(17, s.resolved().ingredients.size)
        assertEquals(6, s.servings)
    }
}
