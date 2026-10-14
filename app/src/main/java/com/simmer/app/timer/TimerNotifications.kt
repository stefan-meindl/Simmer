package com.simmer.app.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.simmer.app.MainActivity
import com.simmer.app.R
import com.simmer.app.domain.TimerAction
import com.simmer.app.domain.TimerNotificationContent

/** Notification channels and notifications of the step timers. */
object TimerNotifications {
    /**
     * Not IMPORTANCE_LOW: with "Show silent conversations and notifications" off, the lockscreen and
     * always-on display hide LOW notifications, promoted or not. DEFAULT with no sound or vibration
     * stays quiet. Channel importance can't be raised after creation, hence the new id.
     */
    const val CHANNEL_RUNNING = "timers_running_v2"
    private const val LEGACY_CHANNEL_RUNNING = "timers_running"
    const val CHANNEL_DONE = "timers_done"

    /** Vibration when a timer ends: wait 0, buzz 200 ms, pause 100 ms, buzz 200 ms. */
    val DONE_VIBRATION = longArrayOf(0, 200, 100, 200)

    /** Creates both channels (minSdk is 26, so channels always exist); safe to call repeatedly. */
    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val running = NotificationChannel(
            CHANNEL_RUNNING,
            context.getString(R.string.timer_channel_running),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.timer_channel_running_description)
            setSound(null, null)
            enableVibration(false)
        }
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
        manager.deleteNotificationChannel(LEGACY_CHANNEL_RUNNING)
        manager.createNotificationChannels(listOf(running, done))
    }

    /** Id of the service's placeholder notification; no timer notification may use it. */
    const val PLACEHOLDER_ID = 1

    /** Ids of a timer's ongoing and finished notifications. They differ, and never equal [PLACEHOLDER_ID]. */
    fun runningId(key: String): Int = safe("running/$key".hashCode())

    fun doneId(key: String): Int = safe("done/$key".hashCode())

    private fun safe(id: Int) = if (id == PLACEHOLDER_ID) id + 1 else id

    const val ACTION_PAUSE = "com.simmer.app.timer.PAUSE"
    const val ACTION_RESUME = "com.simmer.app.timer.RESUME"
    const val ACTION_RESET = "com.simmer.app.timer.RESET"
    const val EXTRA_KEY = "timer_key"
    private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"

    /**
     * The ongoing notification of an active timer: a progress bar (elapsed of duration) and a
     * countdown, public on the lockscreen. Running shows Pause; paused with the notification's
     * Pause shows Resume and Reset. API 36+ turns it into a Live Update (`Notification.ProgressStyle`,
     * promoted to the status-bar chip, lockscreen and always-on display); older versions keep the
     * plain ongoing notification with a progress bar and chronometer.
     */
    fun ongoing(context: Context, key: String, content: TimerNotificationContent): Notification {
        val status = context.getString(if (content.paused) R.string.timer_notification_paused else R.string.timer_notification_running)
        val text = if (content.recipeTitle.isBlank()) status
        else context.getString(R.string.timer_notification_with_recipe, content.recipeTitle, status)
        val plain = NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(titleOf(context, content.title))
            .setContentText(text)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setProgress(content.durationSecs, content.elapsedSecs, false)
            .apply {
                if (content.paused) {
                    setShowWhen(false)
                    setSubText(content.clock)
                } else {
                    // The system counts down to the end by itself; the bar is refreshed by the service.
                    setShowWhen(true)
                    setUsesChronometer(true)
                    setChronometerCountDown(true)
                    setWhen(content.endAtMs)
                }
                content.actions.forEach { addAction(iconOf(it), context.getString(labelOf(it)), actionIntent(context, key, it)) }
            }
            .setContentIntent(openApp(context))
            .build()
        return if (Build.VERSION.SDK_INT >= 36) liveUpdate(context, plain, content) else plain
    }

    @RequiresApi(36)
    private fun liveUpdate(context: Context, plain: Notification, content: TimerNotificationContent): Notification =
        Notification.Builder.recoverBuilder(context, plain)
            .setStyle(
                Notification.ProgressStyle()
                    .setProgressSegments(listOf(Notification.ProgressStyle.Segment(content.durationSecs)))
                    .setProgress(content.elapsedSecs),
            )
            // What Builder.setRequestPromotedOngoing sets; that method needs API 36.1, the extra works on 36.
            .addExtras(Bundle().apply { putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true) })
            .setShortCriticalText(content.clock)
            .build()

    private fun iconOf(action: TimerAction) = when (action) {
        TimerAction.Pause -> R.drawable.ic_pause
        TimerAction.Resume -> R.drawable.ic_play
        TimerAction.Reset -> R.drawable.ic_rotate_ccw
    }

    private fun labelOf(action: TimerAction) = when (action) {
        TimerAction.Pause -> R.string.timer_action_pause
        TimerAction.Resume -> R.string.timer_action_resume
        TimerAction.Reset -> R.string.timer_action_reset
    }

    private fun actionIntent(context: Context, key: String, action: TimerAction): PendingIntent {
        val name = when (action) {
            TimerAction.Pause -> ACTION_PAUSE
            TimerAction.Resume -> ACTION_RESUME
            TimerAction.Reset -> ACTION_RESET
        }
        return PendingIntent.getService(
            context,
            "$name/$key".hashCode(),
            Intent(context, TimerService::class.java).setAction(name).putExtra(EXTRA_KEY, key),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

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
