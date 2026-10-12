package com.simmer.app.timer

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.simmer.app.domain.TimerHost
import com.simmer.app.domain.TimerState
import com.simmer.app.domain.TimerStatus

class AndroidTimerHost(private val context: Context) : TimerHost {

    override fun ensureRunning() {
        // Starting a foreground service can be refused when the app is in the background; the
        // timers are persisted either way and the service is retried on the next start.
        runCatching {
            ContextCompat.startForegroundService(context, Intent(context, TimerService::class.java))
        }
    }

    @SuppressLint("MissingPermission") // Guarded by areNotificationsEnabled().
    override fun finished(key: String, title: String) {
        TimerNotifications.createChannels(context)
        val notifications = NotificationManagerCompat.from(context)
        if (notifications.areNotificationsEnabled()) {
            // Sound and the 200-100-200 vibration come from the channel.
            val timer = TimerState(durationSecs = 0, status = TimerStatus.Done, title = title)
            notifications.notify(TimerNotifications.idFor(key) xor DONE_ID_MASK, TimerNotifications.done(context, timer))
        } else {
            vibrate() // Notifications are blocked: at least buzz.
        }
    }

    private fun vibrate() {
        val vibrator = context.getSystemService(Vibrator::class.java)
        vibrator.vibrate(VibrationEffect.createWaveform(TimerNotifications.DONE_VIBRATION, -1))
    }

    private companion object {
        const val DONE_ID_MASK = 0x5F3759DF
    }
}
