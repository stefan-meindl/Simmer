package com.simmer.app.timer

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.simmer.app.domain.StepTimerStore
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
 * Keeps the process alive while any StepTimer runs, ticks the store, and shows one ongoing
 * notification per running timer. One of them is the foreground notification; the rest are
 * posted beside it. Stops itself when nothing runs. START_STICKY: after process death the system
 * restarts it and the store restores the timers.
 */
@AndroidEntryPoint
class TimerService : Service() {

    @Inject lateinit var store: StepTimerStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val manager by lazy { getSystemService(NotificationManager::class.java) }

    /** Running timers that have a notification, by key, with the end time it was posted for. */
    private val posted = mutableMapOf<String, Long>()
    private var foregroundKey: String? = null
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        TimerNotifications.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!started) {
            started = true
            // startForeground must follow startForegroundService soon, even if nothing runs any more.
            reconcile(runningTimers(), forcePromote = true)
            scope.launch { store.timers.collect { reconcile(runningTimers()) } }
            scope.launch {
                while (true) {
                    store.tick()
                    delay(TICK_MS)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun runningTimers(): Map<String, TimerState> =
        store.timers.value.filterValues { it.status == TimerStatus.Running }

    private fun reconcile(running: Map<String, TimerState>, forcePromote: Boolean = false) {
        if (running.isEmpty()) {
            if (forcePromote) promote(null, TimerNotifications.placeholder(this))
            stopForeground(STOP_FOREGROUND_REMOVE)
            posted.keys.forEach { manager.cancel(TimerNotifications.idFor(it)) }
            posted.clear()
            foregroundKey = null
            stopSelf()
            return
        }
        // Timers that stopped running lose their notification.
        (posted.keys - running.keys).forEach { key ->
            posted.remove(key)
            if (key != foregroundKey) manager.cancel(TimerNotifications.idFor(key))
        }
        // The foreground notification belongs to a timer that still runs.
        if (foregroundKey !in running) {
            val oldId = foregroundKey?.let(TimerNotifications::idFor)
            val key = running.keys.first()
            promote(key, TimerNotifications.running(this, running.getValue(key)))
            posted[key] = running.getValue(key).endAtMs
            oldId?.let(manager::cancel)
        }
        running.forEach { (key, timer) ->
            if (posted[key] != timer.endAtMs) {
                manager.notify(TimerNotifications.idFor(key), TimerNotifications.running(this, timer))
                posted[key] = timer.endAtMs
            }
        }
    }

    private fun promote(key: String?, notification: Notification) {
        ServiceCompat.startForeground(
            this,
            key?.let(TimerNotifications::idFor) ?: PLACEHOLDER_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        foregroundKey = key
    }

    private companion object {
        const val TICK_MS = 250L
        const val PLACEHOLDER_ID = 1
    }
}
