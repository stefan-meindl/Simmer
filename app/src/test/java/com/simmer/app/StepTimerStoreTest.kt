package com.simmer.app

import com.simmer.app.domain.StepTimerStore
import com.simmer.app.domain.TimerHost
import com.simmer.app.domain.TimerPersistence
import com.simmer.app.domain.TimerState
import com.simmer.app.domain.TimerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The timers of the app: several at once, a foreground host while any runs, and survival of process death. */
class StepTimerStoreTest {

    private class FakeHost : TimerHost {
        var serviceRequests = 0
        val finished = mutableListOf<String>()
        override fun ensureRunning() { serviceRequests++ }
        override fun finished(key: String, title: String) { finished += key }
    }

    private class FakePersistence : TimerPersistence {
        var saved: Map<String, TimerState> = emptyMap()
        override fun load() = saved
        override fun save(timers: Map<String, TimerState>) { saved = timers }
    }

    private var now = 1_000_000L
    private val host = FakeHost()
    private val disk = FakePersistence()

    private fun store() = StepTimerStore(clock = { now }, persistence = disk, host = host)

    @Test
    fun firstStartAsksTheHostToRunTheForegroundService() {
        val store = store()
        store.toggle("chilli/s1", "Simmer", 600)
        assertEquals(TimerStatus.Running, store.timers.value.getValue("chilli/s1").status)
        assertEquals(1, host.serviceRequests)
    }

    @Test
    fun pausingDoesNotAskTheHostAgain() {
        val store = store()
        store.toggle("chilli/s1", "Simmer", 600)
        store.toggle("chilli/s1", "Simmer", 600)
        assertEquals(TimerStatus.Paused, store.timers.value.getValue("chilli/s1").status)
        assertEquals(1, host.serviceRequests)
    }

    @Test
    fun severalTimersRunAtOnce() {
        val store = store()
        store.toggle("chilli/s1", "Simmer", 600)
        store.toggle("chilli/s2", "Rice", 300)
        store.toggle("curry/s1", "Fry", 120)
        assertEquals(3, store.runningKeys().size)

        now += 130_000
        store.tick()
        assertEquals(setOf("chilli/s1", "chilli/s2"), store.runningKeys())
        assertEquals(listOf("curry/s1"), host.finished)
    }

    @Test
    fun finishingAlertsOnceAndMarksDone() {
        val store = store()
        store.toggle("chilli/s1", "Simmer", 60)
        now += 59_000
        store.tick()
        assertTrue(host.finished.isEmpty())
        now += 1_000
        store.tick()
        store.tick()
        assertEquals(listOf("chilli/s1"), host.finished)
        assertEquals(TimerStatus.Done, store.timers.value.getValue("chilli/s1").status)
    }

    @Test
    fun resetReturnsToIdle() {
        val store = store()
        store.toggle("chilli/s1", "Simmer", 60)
        store.toggle("chilli/s1", "Simmer", 60)
        store.reset("chilli/s1")
        assertTrue(store.timers.value["chilli/s1"] == null)
    }

    @Test
    fun runningTimerKeepsCountingAfterProcessDeath() {
        store().toggle("chilli/s1", "Simmer", 600)

        now += 100_000 // process killed, reopened 100 s later
        val reopened = store()
        val timer = reopened.timers.value.getValue("chilli/s1")
        assertEquals(TimerStatus.Running, timer.status)
        assertEquals(500_000, timer.remainingMsAt(now))
        assertEquals(2, host.serviceRequests) // the service is started again for it
    }

    @Test
    fun pausedTimerRestoresWithItsRemainingTime() {
        val first = store()
        first.toggle("chilli/s1", "Simmer", 600)
        now += 200_000
        first.toggle("chilli/s1", "Simmer", 600)

        now += 5_000_000
        val timer = store().timers.value.getValue("chilli/s1")
        assertEquals(TimerStatus.Paused, timer.status)
        assertEquals(400_000, timer.remainingMsAt(now))
    }

    @Test
    fun timerThatEndedWhileTheProcessWasDeadRestoresDoneAndAlerts() {
        store().toggle("chilli/s1", "Simmer", 60)

        now += 90_000
        val reopened = store()
        assertEquals(TimerStatus.Done, reopened.timers.value.getValue("chilli/s1").status)
        assertEquals(listOf("chilli/s1"), host.finished)
    }

    @Test
    fun idleTimersAreNotKept() {
        val store = store()
        store.toggle("chilli/s1", "Simmer", 60)
        store.reset("chilli/s1")
        assertTrue(store.timers.value.isEmpty())
        assertTrue(disk.saved.isEmpty())
    }
}
