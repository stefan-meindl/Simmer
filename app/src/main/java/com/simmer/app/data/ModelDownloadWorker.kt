package com.simmer.app.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.simmer.app.MainActivity
import com.simmer.app.R
import com.simmer.app.domain.DownloadFailure
import dagger.hilt.android.EntryPointAccessors

/**
 * Downloads the Recipe reader model in the background, with an ongoing progress notification that
 * opens Settings. A dropped connection returns `retry()`; the partial file is kept, so the next
 * run resumes at its last byte.
 */
class ModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val controller = EntryPointAccessors.fromApplication(applicationContext, ReaderEntryPoint::class.java).readerController()
        showForeground(0)
        val outcome = controller.downloader.download { percent ->
            setProgress(workDataOf(ReaderController.KEY_PERCENT to percent))
            showForeground(percent)
        }
        controller.filesChanged()
        return when (outcome) {
            DownloadOutcome.Completed -> Result.success()
            is DownloadOutcome.Interrupted -> Result.retry()
            DownloadOutcome.ChecksumMismatch -> Result.failure(failureData(DownloadFailure.ChecksumMismatch))
            DownloadOutcome.NotEnoughStorage -> Result.failure(failureData(DownloadFailure.NotEnoughStorage))
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(0)

    /** Android may refuse a foreground start from the background; the download then runs without the notification. */
    private suspend fun showForeground(percent: Int) {
        try {
            setForeground(foregroundInfo(percent))
        } catch (_: IllegalStateException) {
        }
    }

    private fun foregroundInfo(percent: Int): ForegroundInfo {
        val context = applicationContext
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.reader_channel), NotificationManager.IMPORTANCE_LOW)
                .apply { description = context.getString(R.string.reader_channel_description) },
        )
        val notification: Notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(context.getString(R.string.reader_notification_title))
            .setContentText(context.getString(R.string.reader_notification_progress, percent))
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openSettings(context))
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private fun openSettings(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        1,
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_SETTINGS, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val CHANNEL = "reader_download"
        const val NOTIFICATION_ID = 2
    }
}
