package com.simmer.app.timer

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.simmer.app.domain.StepTimerStore
import com.simmer.app.domain.TIMER_TICK_MS
import com.simmer.app.domain.TimerNotificationContent
import com.simmer.app.domain.TimerState
import com.simmer.app.domain.timerNotificationContent
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps the process alive while any StepTimer runs or is paused with the notification's Pause
 * action, ticks the store, and shows one ongoing notification per such timer. One of them is the foreground notification; the rest are
 * posted beside it. Stops itself when nothing runs. START_STICKY: after process death the system
 * restarts it and the store restores the timers.
 */
@AndroidEntryPoint
class TimerService : Service() {

    @Inject lateinit var store: StepTimerStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val manager by lazy { getSystemService(NotificationManager::class.java) }

    /** What each active timer's notification shows now, by key; posted again when it changes. */
    private val posted = mutableMapOf<String, TimerNotificationContent>()
    private var foregroundKey: String? = null
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        TimerNotifications.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra(TimerNotifications.EXTRA_KEY)?.let { key ->
            when (intent.action) {
                TimerNotifications.ACTION_PAUSE -> store.pauseFromNotification(key)
                TimerNotifications.ACTION_RESUME -> store.resume(key)
                TimerNotifications.ACTION_RESET -> store.resetFromNotification(key)
            }
        }
        if (!started) {
            started = true
            // startForeground must follow startForegroundService soon, even if nothing runs any more.
            reconcile(activeTimers(), forcePromote = true)
            scope.launch { store.timers.collect { reconcile(activeTimers()) } }
            scope.launch {
                while (true) {
                    store.tick()
                    reconcile(activeTimers()) // refreshes the progress bars
                    delay(TIMER_TICK_MS)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun activeTimers(): Map<String, TimerState> {
        val active = store.activeKeys()
        return store.timers.value.filterKeys { it in active }
    }

    /** Brings the notifications in line with the [active] timers (running, or paused with the notification's Pause). */
    private fun reconcile(active: Map<String, TimerState>, forcePromote: Boolean = false) {
        if (active.isEmpty()) {
            if (forcePromote) promote(null, TimerNotifications.placeholder(this))
            stopForeground(STOP_FOREGROUND_REMOVE)
            posted.keys.forEach { manager.cancel(TimerNotifications.runningId(it)) }
            posted.clear()
            foregroundKey = null
            stopSelf()
            return
        }
        // Timers that are no longer active lose their notification.
        (posted.keys - active.keys).forEach { key ->
            posted.remove(key)
            if (key != foregroundKey) manager.cancel(TimerNotifications.runningId(key))
        }
        val now = System.currentTimeMillis()
        val contents = active.mapValues { (_, timer) -> timerNotificationContent(timer, now) }
        // The foreground notification belongs to a timer that is still active.
        if (foregroundKey !in active) {
            val oldId = foregroundKey?.let(TimerNotifications::runningId)
            val key = active.keys.first()
            val content = contents.getValue(key)
            promote(key, TimerNotifications.ongoing(this, key, content))
            posted[key] = content
            oldId?.let(manager::cancel)
        }
        contents.forEach { (key, content) ->
            if (posted[key] != content) {
                manager.notify(TimerNotifications.runningId(key), TimerNotifications.ongoing(this, key, content))
                posted[key] = content
            }
        }
    }

    private fun promote(key: String?, notification: Notification) {
        ServiceCompat.startForeground(
            this,
            key?.let(TimerNotifications::runningId) ?: TimerNotifications.PLACEHOLDER_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        foregroundKey = key
    }

}
