package com.simmer.app

import com.simmer.app.data.RecipeRemovals
import com.simmer.app.data.Removal
import com.simmer.app.data.ShoppingRepository
import com.simmer.app.domain.CookingProgressStore
import com.simmer.app.domain.CookingSessions
import com.simmer.app.domain.PlannedChoice
import com.simmer.app.domain.ShoppingEntry
import com.simmer.app.domain.StepTimerStore
import com.simmer.app.domain.TimerHost
import com.simmer.app.domain.TimerPersistence
import com.simmer.app.domain.TimerState
import com.simmer.app.domain.model.Selection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Swiping a card away: the delete, the 5 second Undo window and what Undo brings back. */
@OptIn(ExperimentalCoroutinesApi::class)
class RecipeRemovalsTest {
    private object NoHost : TimerHost {
        override fun ensureRunning() = Unit
        override fun finished(key: String, title: String) = Unit
        override fun dismissFinished(key: String) = Unit
    }

    private class MemoryTimers : TimerPersistence {
        override fun load(): Map<String, TimerState> = emptyMap()
        override fun save(timers: Map<String, TimerState>) = Unit
    }

    private val dao = FakeShoppingDao()
    private val shopping = ShoppingRepository(dao)
    private val progress = CookingProgressStore()
    private val timers = StepTimerStore({ 1_000_000L }, MemoryTimers(), NoHost)
    private val sessions = CookingSessions(progress, timers)
    private val choice = PlannedChoice(4, Selection(mapOf("mince" to "plant"), emptyMap()))

    private fun TestScope.removals() = RecipeRemovals(shopping, sessions, this)

    private suspend fun plan() {
        shopping.addRecipeToList("chilli", listOf(ShoppingEntry("onions", "Onions", "1", "Produce", "n")), choice)
    }

    @Test fun `unplan removes items and Planned state and announces it`() = runTest {
        plan()
        val r = removals()

        r.unplan("chilli", "Chilli")
        runCurrent()

        assertTrue(dao.items.isEmpty())
        assertTrue(dao.planned.isEmpty())
        assertEquals("Chilli", r.lastRemoval.value?.title)
        assertTrue(r.lastRemoval.value is Removal.Unplanned)
    }

    @Test fun `undo after unplan restores items and the Planned choice`() = runTest {
        plan()
        val r = removals()
        r.unplan("chilli", "Chilli")
        runCurrent()

        r.undo()
        runCurrent()

        assertEquals(listOf("Onions"), dao.items.map { it.name })
        assertEquals(choice, shopping.getPlanned("chilli"))
        assertNull(r.lastRemoval.value)
    }

    @Test fun `stop cooking cancels timers, clears progress, keeps Planned and the choice`() = runTest {
        plan()
        progress.setChoice("chilli", choice)
        progress.markDone("chilli", "s1")
        timers.toggle("chilli/s2", "Chop", 60)
        val r = removals()

        r.stopCooking("chilli", "Chilli")
        runCurrent()

        assertTrue(timers.timers.value.isEmpty())
        assertTrue(progress.doneSteps("chilli").isEmpty())
        assertEquals(choice, progress.choice("chilli"))
        assertEquals(choice, shopping.getPlanned("chilli"))
        assertTrue(r.lastRemoval.value is Removal.Stopped)
    }

    @Test fun `undo after stop cooking restores done Steps but not timers`() = runTest {
        progress.markDone("chilli", "s1")
        timers.toggle("chilli/s2", "Chop", 60)
        val r = removals()
        r.stopCooking("chilli", "Chilli")
        runCurrent()

        r.undo()
        runCurrent()

        assertEquals(setOf("s1"), progress.doneSteps("chilli"))
        assertTrue(timers.timers.value.isEmpty())
    }

    @Test fun `the Undo offer expires after 5 seconds`() = runTest {
        plan()
        val r = removals()
        r.unplan("chilli", "Chilli")
        runCurrent()

        advanceTimeBy(4_999)
        assertTrue(r.lastRemoval.value != null)
        advanceTimeBy(2)
        assertNull(r.lastRemoval.value)

        r.undo()
        runCurrent()
        assertTrue(dao.items.isEmpty())
    }

    @Test fun `a newer removal replaces the offer and restarts the window`() = runTest {
        plan()
        val r = removals()
        r.unplan("chilli", "Chilli")
        runCurrent()
        advanceTimeBy(3_000)

        r.stopCooking("curry", "Curry")
        runCurrent()
        advanceTimeBy(3_000)

        assertEquals("Curry", r.lastRemoval.value?.title)
    }
}
