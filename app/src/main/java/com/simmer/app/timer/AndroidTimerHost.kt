package com.simmer.app.timer

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.simmer.app.domain.TimerHost

class AndroidTimerHost(private val context: Context) : TimerHost {

    override fun ensureRunning() {
        // Starting a foreground service can be refused when the app is in the background. The
        // timers are persisted either way, and visible timers keep ticking the store themselves.
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
            notifications.notify(TimerNotifications.doneId(key), TimerNotifications.done(context, title))
        } else {
            // Notifications are blocked, so the channel is silent: play the same sound and buzz here.
            playDefaultSound()
            vibrate()
        }
    }

    override fun dismissFinished(key: String) {
        NotificationManagerCompat.from(context).cancel(TimerNotifications.doneId(key))
    }

    private fun playDefaultSound() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        runCatching { RingtoneManager.getRingtone(context, uri)?.play() }
    }

    private fun vibrate() {
        val vibrator = context.getSystemService(Vibrator::class.java)
        vibrator.vibrate(VibrationEffect.createWaveform(TimerNotifications.DONE_VIBRATION, -1))
    }
}
