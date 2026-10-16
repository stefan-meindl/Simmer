package com.simmer.app.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.simmer.app.MainActivity
import com.simmer.app.R
import com.simmer.app.domain.DownloadFailure
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Gives [ModelDownloadWorker] the Whisper model's controller for Listen. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WhisperEntryPoint {
    fun whisperModelController(): WhisperModelController
}

/**
 * Downloads the Recipe reader model (or, with [KEY_MODEL] = [MODEL_WHISPER], the Whisper model of Listen) in the background, with an ongoing progress notification that
 * opens Settings. A dropped connection (or a 5xx / 408 / 429) returns `retry()`; permanent errors and a full disk end as a failure; the partial file is kept, so the next
 * run resumes at its last byte.
 */
class ModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val downloader = if (whisper) {
            EntryPointAccessors.fromApplication(applicationContext, WhisperEntryPoint::class.java).whisperModelController().downloader
        } else {
            EntryPointAccessors.fromApplication(applicationContext, ReaderEntryPoint::class.java).readerController().downloader
        }
        showForeground(0)
        val outcome = downloader.download { percent ->
            setProgress(workDataOf(ReaderController.KEY_PERCENT to percent))
            showForeground(percent)
        }
        if (!whisper) {
            EntryPointAccessors.fromApplication(applicationContext, ReaderEntryPoint::class.java).readerController().filesChanged()
        }
        return when (outcome) {
            DownloadOutcome.Completed -> Result.success()
            is DownloadOutcome.Interrupted -> Result.retry()
            DownloadOutcome.ChecksumMismatch -> Result.failure(failureData(DownloadFailure.ChecksumMismatch))
            is DownloadOutcome.ServerError -> Result.failure(failureData(DownloadFailure.ServerError))
            DownloadOutcome.NotEnoughStorage -> Result.failure(failureData(DownloadFailure.NotEnoughStorage))
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(0)

    private val whisper get() = inputData.getString(KEY_MODEL) == MODEL_WHISPER

    /** Android may refuse a foreground start from the background; the download then runs without the notification. */
    private suspend fun showForeground(percent: Int) {
        try {
            setForeground(foregroundInfo(percent))
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Foreground start refused; downloading without a notification", e)
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
            .setContentTitle(context.getString(if (whisper) R.string.whisper_notification_title else R.string.reader_notification_title))
            .setContentText(context.getString(R.string.reader_notification_progress, percent))
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(if (whisper) openAddRecipe(context) else openSettings(context))
            .build()
        return ForegroundInfo(if (whisper) WHISPER_NOTIFICATION_ID else NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private fun openAddRecipe(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        1,
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_ADD_RECIPE, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openSettings(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        1,
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_SETTINGS, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        /** Input data key naming the model to download; absent means the Recipe reader model. */
        const val KEY_MODEL = "model"
        const val MODEL_WHISPER = "whisper"
        private const val TAG = "ModelDownloadWorker"
        private const val CHANNEL = "reader_download"
        private const val NOTIFICATION_ID = 2
        private const val WHISPER_NOTIFICATION_ID = 3
    }
}
