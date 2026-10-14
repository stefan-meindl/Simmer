package com.simmer.app

import com.simmer.app.domain.TimerAction
import com.simmer.app.domain.TimerState
import com.simmer.app.domain.timerNotificationContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What an ongoing timer notification shows, decided without Android. */
class TimerNotificationContentTest {

    private val start = 1_000_000L
    private val running = TimerState.idle(600, "Simmer", "Chilli").toggle(start)

    @Test
    fun runningShowsPauseOnlyAndCountsDownToTheEnd() {
        val c = timerNotificationContent(running, start + 100_000)
        assertFalse(c.paused)
        assertEquals(listOf(TimerAction.Pause), c.actions)
        assertEquals(start + 600_000, c.endAtMs)
        assertEquals(100, c.elapsedSecs)
        assertEquals(600, c.durationSecs)
        assertEquals("8:20", c.clock)
    }

    @Test
    fun pausedFromTheNotificationShowsResumeAndResetWithoutAnEndTime() {
        val paused = running.pauseFromNotification(start + 100_000)
        val c = timerNotificationContent(paused, start + 900_000)
        assertTrue(c.paused)
        assertEquals(listOf(TimerAction.Resume, TimerAction.Reset), c.actions)
        assertEquals(0, c.endAtMs)
        assertEquals(100, c.elapsedSecs)
        assertEquals("8:20", c.clock)
    }

    @Test
    fun elapsedIsClampedToTheDuration() {
        assertEquals(600, timerNotificationContent(running, start + 9_000_000).elapsedSecs)
        assertEquals(0, timerNotificationContent(running, start - 5_000).elapsedSecs)
    }

    @Test
    fun contentIsEqualWithinOneSecondAndDiffersOnTheNextOne() {
        val a = timerNotificationContent(running, start + 100_000)
        assertEquals(a, timerNotificationContent(running, start + 100_250))
        assertNotEquals(a, timerNotificationContent(running, start + 101_000))
    }

    @Test
    fun carriesTitlesForTheText() {
        val c = timerNotificationContent(running, start)
        assertEquals("Simmer", c.title)
        assertEquals("Chilli", c.recipeTitle)
    }
}
