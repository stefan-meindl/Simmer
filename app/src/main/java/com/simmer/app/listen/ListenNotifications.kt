package com.simmer.app.listen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.simmer.app.MainActivity
import com.simmer.app.R

/** Notification channels and notifications of Listen. */
object ListenNotifications {
    const val CHANNEL_CAPTURE = "listen_capture"
    const val CHANNEL_DONE = "listen_done"

    /** Distinct from the timer (1), model download (2, 3) and import ids. */
    const val CAPTURE_ID = 0x1E01
    const val DONE_ID = 0x1E02

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_CAPTURE, context.getString(R.string.listen_channel_capture), NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CHANNEL_DONE, context.getString(R.string.listen_channel_done), NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }

    /** Ongoing notification with a running timer (since [startedAt]) and a Stop action. */
    fun capturing(context: Context, startedAt: Long): Notification =
        NotificationCompat.Builder(context, CHANNEL_CAPTURE)
            .setSmallIcon(R.drawable.ic_play)
            .setContentTitle(context.getString(R.string.listen_notification_title))
            .setContentText(context.getString(R.string.listen_notification_text))
            .setWhen(startedAt)
            .setUsesChronometer(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAddRecipe(context))
            .addAction(0, context.getString(R.string.listen_stop), stopIntent(context))
            .build()

    fun transcribing(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_CAPTURE)
            .setSmallIcon(R.drawable.ic_play)
            .setContentTitle(context.getString(R.string.listen_transcribing))
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openAddRecipe(context))
            .build()

    fun ready(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_play)
            .setContentTitle(context.getString(R.string.listen_ready_title))
            .setAutoCancel(true)
            .setContentIntent(openAddRecipe(context))
            .build()

    fun silent(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_play)
            .setContentTitle(context.getString(R.string.listen_silent_title))
            .setAutoCancel(true)
            .setContentIntent(openAddRecipe(context))
            .build()

    private fun stopIntent(context: Context): PendingIntent = PendingIntent.getService(
        context,
        2,
        Intent(context, ListenService::class.java).setAction(ListenService.ACTION_STOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Opens Add recipe in the running Simmer. */
    fun openAddRecipe(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        1,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_ADD_RECIPE, true),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
