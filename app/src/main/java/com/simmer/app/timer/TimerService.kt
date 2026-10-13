package com.simmer.app.timer

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.simmer.app.domain.StepTimerStore
import com.simmer.app.domain.TIMER_TICK_MS
import com.simmer.app.domain.TimerState
import com.simmer.app.domain.TimerStatus
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

    /** What a posted notification shows; it is posted again when this changes (about once a second while running). */
    private data class Posted(val status: TimerStatus, val endAtMs: Long, val elapsedSecs: Int)

    /** Active timers that have a notification, by key. */
    private val posted = mutableMapOf<String, Posted>()
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
                TimerNotifications.ACTION_RESUME -> store.timers.value[key]?.let {
                    store.toggle(key, it.title, it.durationSecs, it.recipeTitle)
                }
                TimerNotifications.ACTION_RESET -> store.reset(key)
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

    private fun postedOf(timer: TimerState, now: Long) = Posted(
        timer.status,
        timer.endAtMs,
        (timer.progressAt(now) * timer.durationSecs).toInt(),
    )

    private fun reconcile(running: Map<String, TimerState>, forcePromote: Boolean = false) {
        if (running.isEmpty()) {
            if (forcePromote) promote(null, TimerNotifications.placeholder(this))
            stopForeground(STOP_FOREGROUND_REMOVE)
            posted.keys.forEach { manager.cancel(TimerNotifications.runningId(it)) }
            posted.clear()
            foregroundKey = null
            stopSelf()
            return
        }
        // Timers that stopped running lose their notification.
        (posted.keys - running.keys).forEach { key ->
            posted.remove(key)
            if (key != foregroundKey) manager.cancel(TimerNotifications.runningId(key))
        }
        // The foreground notification belongs to a timer that still runs.
        if (foregroundKey !in running) {
            val oldId = foregroundKey?.let(TimerNotifications::runningId)
            val key = running.keys.first()
            promote(key, TimerNotifications.running(this, key, running.getValue(key)))
            posted[key] = postedOf(running.getValue(key), System.currentTimeMillis())
            oldId?.let(manager::cancel)
        }
        val now = System.currentTimeMillis()
        running.forEach { (key, timer) ->
            val shown = postedOf(timer, now)
            if (posted[key] != shown) {
                manager.notify(TimerNotifications.runningId(key), TimerNotifications.running(this, key, timer, now))
                posted[key] = shown
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
