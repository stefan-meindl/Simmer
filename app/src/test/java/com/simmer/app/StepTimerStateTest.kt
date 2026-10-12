package com.simmer.app

import com.simmer.app.domain.TimerState
import com.simmer.app.domain.TimerStatus
import com.simmer.app.domain.formatClock
import org.junit.Assert.assertEquals
import org.junit.Test

/** The StepTimer cycle idle, running, paused, done, and the countdown it shows. */
class StepTimerStateTest {

    private val fiveMin = TimerState.idle(300)

    @Test
    fun cyclesIdleRunningPausedRunningDoneThenBackToIdle() {
        val running = fiveMin.toggle(now = 1_000)
        assertEquals(TimerStatus.Running, running.status)

        val paused = running.toggle(now = 61_000)
        assertEquals(TimerStatus.Paused, paused.status)

        val resumed = paused.toggle(now = 100_000)
        assertEquals(TimerStatus.Running, resumed.status)

        val done = resumed.tick(now = 100_000 + 240_000)
        assertEquals(TimerStatus.Done, done.status)

        assertEquals(TimerStatus.Idle, done.toggle(now = 400_000).status)
    }

    @Test
    fun countsDownWhileRunningAndFreezesWhilePaused() {
        val running = fiveMin.toggle(now = 10_000)
        assertEquals(240_000, running.remainingMsAt(now = 70_000))

        val paused = running.toggle(now = 70_000)
        assertEquals(240_000, paused.remainingMsAt(now = 999_000))
    }

    @Test
    fun resumeContinuesFromWhereItPaused() {
        val paused = fiveMin.toggle(now = 0).toggle(now = 100_000)
        val resumed = paused.toggle(now = 500_000)
        assertEquals(200_000, resumed.remainingMsAt(now = 500_000))
        assertEquals(150_000, resumed.remainingMsAt(now = 550_000))
    }

    @Test
    fun tickBeforeTheEndKeepsRunning() {
        val running = fiveMin.toggle(now = 0)
        assertEquals(TimerStatus.Running, running.tick(now = 299_999).status)
        assertEquals(TimerStatus.Done, running.tick(now = 300_000).status)
    }

    @Test
    fun doneHasNothingRemainingAndResetRestoresTheFullDuration() {
        val done = fiveMin.toggle(now = 0).tick(now = 400_000)
        assertEquals(0, done.remainingMsAt(now = 400_000))

        val reset = done.reset()
        assertEquals(TimerStatus.Idle, reset.status)
        assertEquals(300_000, reset.remainingMsAt(now = 500_000))
    }

    @Test
    fun resetFromPausedRestoresTheFullDuration() {
        val paused = fiveMin.toggle(now = 0).toggle(now = 100_000)
        assertEquals(TimerStatus.Idle, paused.reset().status)
        assertEquals(300_000, paused.reset().remainingMsAt(now = 0))
    }

    @Test
    fun progressGoesFromZeroToOne() {
        val running = fiveMin.toggle(now = 0)
        assertEquals(0f, running.progressAt(now = 0), 0.001f)
        assertEquals(0.5f, running.progressAt(now = 150_000), 0.001f)
        assertEquals(1f, running.tick(now = 300_000).progressAt(now = 300_000), 0.001f)
    }

    @Test
    fun clockShowsMinutesAndSecondsOrHours() {
        assertEquals("0:00", formatClock(0))
        assertEquals("0:09", formatClock(9_000))
        assertEquals("5:00", formatClock(300_000))
        assertEquals("59:59", formatClock(3_599_000))
        assertEquals("1:00:00", formatClock(3_600_000))
        assertEquals("1:05:07", formatClock(3_907_000))
    }

    @Test
    fun clockRoundsPartialSecondsUp() {
        assertEquals("0:01", formatClock(1))
        assertEquals("4:59", formatClock(298_001))
    }
}
