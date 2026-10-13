package com.simmer.app.pipeline

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.simmer.app.MainActivity
import com.simmer.app.R
import com.simmer.app.domain.PipelineStage

/** Notification channels and notifications of the Pipeline job. */
object ImportNotifications {
    const val CHANNEL_PROGRESS = "import_progress"
    const val CHANNEL_DONE = "import_done"

    /** Distinct from the timer notification ids (1 and hashes). */
    const val PROGRESS_ID = 0x1D01
    const val DONE_ID = 0x1D02

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_PROGRESS, context.getString(R.string.import_channel_progress), NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CHANNEL_DONE, context.getString(R.string.import_channel_done), NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }

    fun stageLabel(stage: PipelineStage): Int = when (stage) {
        PipelineStage.Ingredients -> R.string.import_stage_ingredients
        PipelineStage.Splitting -> R.string.import_stage_splitting
        PipelineStage.Linking -> R.string.import_stage_linking
        PipelineStage.Timers -> R.string.import_stage_timers
    }

    /** Ongoing notification naming the pass that runs. */
    fun progress(context: Context, stage: PipelineStage): Notification =
        NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_sparkles)
            .setContentTitle(context.getString(R.string.import_working_title))
            .setContentText(context.getString(stageLabel(stage)))
            .setProgress(PipelineStage.entries.size, stage.ordinal, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAddRecipe(context))
            .build()

    fun ready(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_sparkles)
            .setContentTitle(context.getString(R.string.import_ready_title))
            .setAutoCancel(true)
            .setContentIntent(openAddRecipe(context))
            .build()

    fun failed(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_sparkles)
            .setContentTitle(context.getString(R.string.import_error_title))
            .setContentText(context.getString(R.string.import_error_body))
            .setAutoCancel(true)
            .setContentIntent(openAddRecipe(context))
            .build()

    private fun openAddRecipe(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        1,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_ADD_RECIPE, true),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
