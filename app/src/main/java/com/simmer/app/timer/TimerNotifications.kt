package com.simmer.app.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.simmer.app.MainActivity
import com.simmer.app.R
import com.simmer.app.domain.TimerState
import com.simmer.app.domain.TimerStatus
import com.simmer.app.domain.formatClock

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

    const val ACTION_PAUSE = "com.simmer.app.timer.PAUSE"
    const val ACTION_RESUME = "com.simmer.app.timer.RESUME"
    const val ACTION_RESET = "com.simmer.app.timer.RESET"
    const val EXTRA_KEY = "timer_key"
    private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"

    /**
     * The ongoing notification of an active timer: a progress bar (elapsed of duration) and a
     * countdown, public on the lockscreen. Running shows Pause; paused with the notification's
     * Pause shows Resume and Reset. API 36+ uses a Live Update (`Notification.ProgressStyle`,
     * promoted to the status-bar chip, lockscreen and always-on display); older versions get a
     * plain ongoing notification with a progress bar and chronometer.
     */
    fun running(context: Context, key: String, timer: TimerState, now: Long = System.currentTimeMillis()): Notification {
        val running = timer.status == TimerStatus.Running
        val remainingMs = timer.remainingMsAt(now)
        val elapsedSecs = ((timer.durationSecs * 1000L - remainingMs) / 1000).toInt().coerceIn(0, timer.durationSecs)
        val title = titleOf(context, timer.title)
        val status = context.getString(if (running) R.string.timer_notification_running else R.string.timer_notification_paused)
        val text = if (timer.recipeTitle.isBlank()) status
        else context.getString(R.string.timer_notification_with_recipe, timer.recipeTitle, status)
        val actions = if (running) {
            listOf(action(context, key, ACTION_PAUSE, R.string.timer_action_pause))
        } else {
            listOf(
                action(context, key, ACTION_RESUME, R.string.timer_action_resume),
                action(context, key, ACTION_RESET, R.string.timer_action_reset),
            )
        }

        if (Build.VERSION.SDK_INT >= 36) {
            return liveUpdate(context, timer, title, text, running, remainingMs, elapsedSecs, actions)
        }
        return NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(title)
            .setContentText(text)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setProgress(timer.durationSecs, elapsedSecs, false)
            .apply {
                if (running) {
                    // The system counts down to the end by itself; the bar is refreshed by the service.
                    setShowWhen(true)
                    setUsesChronometer(true)
                    setChronometerCountDown(true)
                    setWhen(timer.endAtMs)
                } else {
                    setShowWhen(false)
                    setSubText(formatClock(remainingMs))
                }
                actions.forEach { addAction(it.icon, it.title, it.intent) }
            }
            .setContentIntent(openApp(context))
            .build()
    }

    @RequiresApi(36)
    private fun liveUpdate(
        context: Context,
        timer: TimerState,
        title: String,
        text: String,
        running: Boolean,
        remainingMs: Long,
        elapsedSecs: Int,
        actions: List<TimerAction>,
    ): Notification {
        val style = Notification.ProgressStyle()
            .setProgressSegments(listOf(Notification.ProgressStyle.Segment(timer.durationSecs)))
            .setProgress(elapsedSecs)
        return Notification.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(title)
            .setContentText(text)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .setStyle(style)
            // What Builder.setRequestPromotedOngoing sets; that method needs API 36.1, the extra works on 36.
            .addExtras(Bundle().apply { putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true) })
            .setShortCriticalText(formatClock(remainingMs))
            .apply {
                if (running) {
                    setShowWhen(true)
                    setUsesChronometer(true)
                    setChronometerCountDown(true)
                    setWhen(timer.endAtMs)
                }
                actions.forEach {
                    addAction(
                        Notification.Action.Builder(Icon.createWithResource(context, it.icon), it.title, it.intent).build(),
                    )
                }
            }
            .setContentIntent(openApp(context))
            .build()
    }

    private class TimerAction(val icon: Int, val title: String, val intent: PendingIntent)

    private fun action(context: Context, key: String, action: String, label: Int): TimerAction {
        val intent = Intent(context, TimerService::class.java).setAction(action).putExtra(EXTRA_KEY, key)
        val pending = PendingIntent.getService(
            context,
            "$action/$key".hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return TimerAction(R.drawable.ic_timer, context.getString(label), pending)
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
