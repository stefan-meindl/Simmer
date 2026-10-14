package com.simmer.app

import com.simmer.app.domain.CookingProgressStore
import com.simmer.app.domain.CookingSessions
import com.simmer.app.domain.PlannedChoice
import com.simmer.app.domain.Phase1State
import com.simmer.app.domain.StepTimerStore
import com.simmer.app.domain.TimerHost
import com.simmer.app.domain.TimerPersistence
import com.simmer.app.domain.TimerState
import com.simmer.app.domain.resolve
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A recipe is in progress while a StepTimer runs or is paused, or a Step is marked done, and not once every Step is done. */
class CookingSessionsTest {

    private object NoHost : TimerHost {
        override fun ensureRunning() = Unit
        override fun finished(key: String, title: String) = Unit
        override fun dismissFinished(key: String) = Unit
    }

    private class MemoryTimers : TimerPersistence {
        var saved: Map<String, TimerState> = emptyMap()
        override fun load() = saved
        override fun save(timers: Map<String, TimerState>) { saved = timers }
    }

    private var now = 1_000_000L
    private val progress = CookingProgressStore()
    private val timers = StepTimerStore({ now }, MemoryTimers(), NoHost)
    private val sessions = CookingSessions(progress, timers)

    private val chilli = BuiltInRecipes.chilli()
    private val curry = BuiltInRecipes.curry()
    private val choice = Phase1State.initial(chilli, planned = null).let { PlannedChoice(it.servings, it.selection) }
    private val chilliSteps = resolve(chilli, choice.servings, choice.selection).steps.map { it.id }

    @Test
    fun freshRecipeIsNotInProgress() {
        assertFalse(sessions.isInProgress(chilli, choice))
    }

    @Test
    fun aMarkedStepMakesItInProgress() {
        progress.markDone("chilli", chilliSteps.first())
        assertTrue(sessions.isInProgress(chilli, choice))
    }

    @Test
    fun undoingTheOnlyMarkedStepEndsIt() {
        progress.markDone("chilli", chilliSteps.first())
        progress.undo("chilli", chilliSteps.first())
        assertFalse(sessions.isInProgress(chilli, choice))
    }

    @Test
    fun aRunningTimerMakesItInProgress() {
        timers.toggle("chilli/${chilliSteps.first()}", "Chop", 60)
        assertTrue(sessions.isInProgress(chilli, choice))
    }

    @Test
    fun aPausedTimerMakesItInProgress() {
        timers.toggle("chilli/s", "Chop", 60)
        timers.toggle("chilli/s", "Chop", 60)
        assertTrue(sessions.isInProgress(chilli, choice))
    }

    @Test
    fun aFinishedTimerAloneDoesNot() {
        timers.toggle("chilli/s", "Chop", 60)
        now += 60_000
        timers.tick()
        assertFalse(sessions.isInProgress(chilli, choice))
    }

    @Test
    fun anotherRecipesTimerDoesNot() {
        timers.toggle("curry/s", "Fry", 60)
        assertFalse(sessions.isInProgress(chilli, choice))
    }

    @Test
    fun finishingEveryStepEndsIt() {
        chilliSteps.forEach { progress.markDone("chilli", it) }
        assertFalse(sessions.isInProgress(chilli, choice))
    }

    @Test
    fun aFinishedRecipeStaysHiddenWhileATimerStillRuns() {
        timers.toggle("chilli/s", "Simmer", 600)
        chilliSteps.forEach { progress.markDone("chilli", it) }
        assertFalse(sessions.isInProgress(chilli, choice))
        assertTrue(timers.runningKeys().isNotEmpty())
    }

    @Test
    fun stoppingClearsTimersAndProgressButKeepsTheChoice() {
        progress.setChoice("chilli", choice)
        progress.markDone("chilli", chilliSteps.first())
        timers.toggle("chilli/s", "Chop", 60)
        progress.markDone("curry", "x")

        sessions.stop("chilli")

        assertFalse(sessions.isInProgress(chilli, choice))
        assertTrue(timers.timers.value.isEmpty())
        assertEquals(emptySet<String>(), progress.doneSteps("chilli"))
        assertEquals(choice, progress.choice("chilli"))
        assertEquals(setOf("x"), progress.doneSteps("curry"))
    }

    @Test
    fun stoppingReturnsTheDoneStepsAndUndoPutsThemBackWithoutTheTimers() {
        progress.setChoice("chilli", choice)
        chilliSteps.take(2).forEach { progress.markDone("chilli", it) }
        timers.toggle("chilli/s", "Chop", 60)

        val stopped = sessions.stop("chilli")
        assertFalse(sessions.isInProgress(chilli, choice))

        sessions.restore(stopped)

        assertEquals(chilliSteps.take(2).toSet(), progress.doneSteps("chilli"))
        assertTrue(sessions.isInProgress(chilli, choice))
        assertTrue(timers.timers.value.isEmpty())
        assertEquals(choice, progress.choice("chilli"))
    }

    @Test
    fun cardsListEveryInProgressRecipeWithItsChoice() = runBlocking {
        val curryChoice = PlannedChoice(2, curry.let { Phase1State.initial(it, null).selection })
        progress.setChoice("curry", curryChoice)
        progress.setChoice("chilli", choice)
        progress.markDone("chilli", chilliSteps.first())
        timers.toggle("curry/s", "Fry", 60)

        val entries = sessions.observe(listOf(chilli, curry)).first()

        assertEquals(listOf("chilli", "curry"), entries.map { it.recipe.id })
        assertEquals(listOf(choice, curryChoice), entries.map { it.choice })
    }

    @Test
    fun noCardWhenNothingIsInProgress() = runBlocking {
        progress.setChoice("chilli", choice)
        assertTrue(sessions.observe(listOf(chilli, curry)).first().isEmpty())
    }

    @Test
    fun resumeChoiceIsOnlyOfferedWhileInProgress() {
        progress.setChoice("chilli", choice)
        assertNull(sessions.resumeChoice(chilli))
        progress.markDone("chilli", chilliSteps.first())
        assertEquals(choice, sessions.resumeChoice(chilli))
    }
}
