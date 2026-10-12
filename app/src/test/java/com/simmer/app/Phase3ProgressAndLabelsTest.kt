package com.simmer.app

import com.simmer.app.domain.CookingProgressStore
import com.simmer.app.domain.Phase1State
import com.simmer.app.domain.formatDuration
import com.simmer.app.domain.selectionSummary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class Phase3ProgressAndLabelsTest {
    private val chilli = BuiltInRecipes.chilli()
    private val start = Phase1State.initial(chilli, planned = null)

    // --- Cooking Progress store ----------------------------------------------

    @Test fun `undo takes a step out of the done set`() {
        val store = CookingProgressStore()
        store.markDone("chilli", "p-veg")
        store.markDone("chilli", "p-beans")
        store.undo("chilli", "p-veg")
        assertEquals(setOf("p-beans"), store.doneSteps("chilli"))
    }

    @Test fun `restore fills an empty recipe from saved state`() {
        val store = CookingProgressStore()
        store.restore("chilli", listOf("p-veg", "p-rice"))
        assertEquals(setOf("p-veg", "p-rice"), store.doneSteps("chilli"))
    }

    @Test fun `restore never overwrites progress already in memory`() {
        val store = CookingProgressStore()
        store.markDone("chilli", "p-beans")
        store.restore("chilli", listOf("p-veg"))
        assertEquals(setOf("p-beans"), store.doneSteps("chilli"))
    }

    @Test fun `observe emits the current done set of that recipe`() = runBlocking {
        val store = CookingProgressStore()
        store.markDone("chilli", "p-veg")
        store.markDone("curry", "p-rice")
        assertEquals(setOf("p-veg"), store.observe("chilli").first())
    }

    // --- labels --------------------------------------------------------------

    @Test fun `steps are labelled Vorb and Schritt in German`() {
        val steps = start.resolved(lang = "de").steps
        assertEquals("Vorb. 1", steps.first().label)
        assertEquals("Schritt 1", steps.first { it.phase == "cook" }.label)
    }

    @Test fun `steps are labelled Prep and Step in English`() {
        val steps = start.resolved().steps
        assertEquals("Prep 1", steps.first().label)
        assertEquals("Step 1", steps.first { it.phase == "cook" }.label)
    }

    // --- selection summary ---------------------------------------------------

    @Test fun `summary is just servings for the default selection`() {
        assertEquals("4 servings", selectionSummary(chilli, start.selection, "4 servings"))
    }

    @Test fun `summary names non-default swaps and active add-ins`() {
        val picked = start.withSwap("mince", "plant").withAddIn("choc", true).withAddIn("beer", false)
        assertEquals(
            "2 servings · Plant-based · Dark chocolate",
            selectionSummary(chilli, picked.selection, "2 servings"),
        )
    }

    // --- timer label ---------------------------------------------------------

    @Test fun `durations read as seconds, minutes or hours`() {
        assertEquals("45 s", formatDuration(45))
        assertEquals("5 min", formatDuration(300))
        assertEquals("18 min", formatDuration(1080))
        assertEquals("1 h", formatDuration(3600))
        assertEquals("1 h 30 min", formatDuration(5400))
        assertEquals("6 h", formatDuration(21600))
    }
}
