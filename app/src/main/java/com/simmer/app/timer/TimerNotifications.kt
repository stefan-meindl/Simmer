package com.simmer.app.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import com.simmer.app.MainActivity
import com.simmer.app.R
import com.simmer.app.domain.TimerState

/** Notification channels and notifications of the step timers. */
object TimerNotifications {
    const val CHANNEL_RUNNING = "timers_running"
    const val CHANNEL_DONE = "timers_done"

    /** Vibration when a timer ends: wait 0, buzz 200 ms, pause 100 ms, buzz 200 ms. */
    val DONE_VIBRATION = longArrayOf(0, 200, 100, 200)

    /** Creates both channels (minSdk is 26, so channels always exist); safe to call repeatedly. */
    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val running = NotificationChannel(
            CHANNEL_RUNNING,
            context.getString(R.string.timer_channel_running),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = context.getString(R.string.timer_channel_running_description) }
        val done = NotificationChannel(
            CHANNEL_DONE,
            context.getString(R.string.timer_channel_done),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.timer_channel_done_description)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
            vibrationPattern = DONE_VIBRATION
        }
        manager.createNotificationChannels(listOf(running, done))
    }

    /** Id of the service's placeholder notification; no timer notification may use it. */
    const val PLACEHOLDER_ID = 1

    /** Ids of a timer's ongoing and finished notifications. They differ, and never equal [PLACEHOLDER_ID]. */
    fun runningId(key: String): Int = safe("running/$key".hashCode())

    fun doneId(key: String): Int = safe("done/$key".hashCode())

    private fun safe(id: Int) = if (id == PLACEHOLDER_ID) id + 1 else id

    fun running(context: Context, timer: TimerState): Notification =
        NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(titleOf(context, timer.title))
            .setContentText(context.getString(R.string.timer_notification_running))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            // The system counts down to the end by itself; no per-second updates.
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(timer.endAtMs)
            .setContentIntent(openApp(context))
            .build()

    fun done(context: Context, title: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(titleOf(context, title))
            .setContentText(context.getString(R.string.timer_notification_done))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openApp(context))
            .build()

    /** Shown by the service when it must call startForeground with no running timer left. */
    fun placeholder(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(context.getString(R.string.timer_notification_fallback_title))
            .setOngoing(true)
            .build()

    private fun titleOf(context: Context, title: String) =
        title.ifBlank { context.getString(R.string.timer_notification_fallback_title) }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
