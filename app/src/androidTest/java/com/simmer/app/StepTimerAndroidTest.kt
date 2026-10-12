package com.simmer.app

import android.app.NotificationManager
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simmer.app.domain.StepTimerStore
import com.simmer.app.domain.TimerHost
import com.simmer.app.domain.TimerState
import com.simmer.app.domain.TimerStatus
import com.simmer.app.timer.SharedPrefsTimerPersistence
import com.simmer.app.timer.TimerNotifications
import com.simmer.app.ui.components.StepTimer
import com.simmer.app.ui.theme.SimmerTheme
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StepTimerAndroidTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun show(status: TimerStatus) = compose.setContent {
        SimmerTheme {
            StepTimer(status, idleText = "5 min", clockText = "3:42", progress = 0.3f, onToggle = {}, onReset = {})
        }
    }

    @Test
    fun idleShowsDurationAndNoReset() {
        show(TimerStatus.Idle)
        compose.onNodeWithText("5 min").assertIsDisplayed()
        compose.onNodeWithContentDescription("Reset timer").assertDoesNotExist()
    }

    @Test
    fun runningShowsCountdownAndNoReset() {
        show(TimerStatus.Running)
        compose.onNodeWithText("3:42").assertIsDisplayed()
        compose.onNodeWithContentDescription("Pause timer, 3:42 left").assertIsDisplayed()
        compose.onNodeWithContentDescription("Reset timer").assertDoesNotExist()
    }

    @Test
    fun pausedShowsResumeAndReset() {
        show(TimerStatus.Paused)
        compose.onNodeWithContentDescription("Resume timer, 3:42 left").assertIsDisplayed()
        compose.onNodeWithContentDescription("Reset timer").assertIsDisplayed()
    }

    @Test
    fun doneShowsDoneLabelAndReset() {
        show(TimerStatus.Done)
        compose.onNodeWithText("Done").assertIsDisplayed()
        compose.onNodeWithContentDescription("Reset timer").assertIsDisplayed()
    }

    @Test
    fun resetButtonIs44dpAndTimerButtonDoesNotShift() {
        var status by mutableStateOf(TimerStatus.Running)
        compose.setContent {
            SimmerTheme {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    StepTimer(status, "5 min", "3:42", 0.3f, onToggle = {}, onReset = {})
                }
            }
        }
        val running = compose.onNodeWithContentDescription("Pause timer, 3:42 left").getUnclippedBoundsInRoot()
        status = TimerStatus.Paused
        compose.waitForIdle()
        val paused = compose.onNodeWithContentDescription("Resume timer, 3:42 left").getUnclippedBoundsInRoot()
        assertEquals(running.right.value, paused.right.value, 0.5f)
        assertEquals(running.width.value, paused.width.value, 0.5f)

        val reset = compose.onNodeWithContentDescription("Reset timer").getUnclippedBoundsInRoot()
        assertEquals(44f, reset.width.value, 0.5f)
        assertEquals(44f, reset.height.value, 0.5f)
        assert(reset.right <= paused.left) { "Reset must sit left of the timer" }
    }

    @Test
    fun timerChannelsExistWithTheDoneVibration() {
        TimerNotifications.createChannels(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        assertNotNull(manager.getNotificationChannel(TimerNotifications.CHANNEL_RUNNING))
        val done = manager.getNotificationChannel(TimerNotifications.CHANNEL_DONE)
        assertNotNull(done.sound)
        assertEquals(true, done.shouldVibrate())
        assertArrayEquals(longArrayOf(0, 200, 100, 200), done.vibrationPattern)
    }

    @Test
    fun timersSurviveAProcessRestartThroughSharedPreferences() {
        val noHost = object : TimerHost {
            override fun ensureRunning() = Unit
            override fun finished(key: String, title: String) = Unit
            override fun dismissFinished(key: String) = Unit
        }
        context.getSharedPreferences("step_timers", Context.MODE_PRIVATE).edit().clear().commit()
        var now = 5_000_000L
        val first = StepTimerStore({ now }, SharedPrefsTimerPersistence(context), noHost)
        first.toggle("chilli/s1", "Simmer", 600)
        first.toggle("chilli/s2", "Rice", 300)
        now += 60_000
        first.toggle("chilli/s2", "Rice", 300) // pause

        now += 30_000
        val reopened = StepTimerStore({ now }, SharedPrefsTimerPersistence(context), noHost)
        val simmer: TimerState = reopened.timers.value.getValue("chilli/s1")
        assertEquals(TimerStatus.Running, simmer.status)
        assertEquals(510_000, simmer.remainingMsAt(now))
        val rice = reopened.timers.value.getValue("chilli/s2")
        assertEquals(TimerStatus.Paused, rice.status)
        assertEquals(240_000, rice.remainingMsAt(now))
        assertEquals("Simmer", simmer.title)
        context.getSharedPreferences("step_timers", Context.MODE_PRIVATE).edit().clear().commit()
    }
}
