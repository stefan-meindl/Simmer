package com.simmer.app.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.simmer.app.domain.DownloadFailure
import com.simmer.app.domain.DownloadWork
import com.simmer.app.domain.ReaderModel
import com.simmer.app.domain.READER_REQUIRED_FREE_BYTES
import com.simmer.app.domain.ReaderStatus
import com.simmer.app.domain.readerStatus
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Gives [ModelDownloadWorker] the app's [ReaderController]. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReaderEntryPoint {
    fun readerController(): ReaderController
}

/**
 * The Recipe reader's model: its live [status] and the actions Settings offers. The download itself
 * runs as a unique WorkManager task ([ModelDownloadWorker]) so it survives leaving the app.
 */
@Singleton
class ReaderController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PrefsRepository,
) {
    private val modelDir = File(context.filesDir, "models")

    val downloader = ModelDownloader(
        dir = modelDir,
        fileName = ReaderModel.FILE_NAME,
        sha256 = ReaderModel.SHA256,
        source = HttpModelSource(ReaderModel.URL),
        freeBytes = { freeBytes() },
        requiredFreeBytes = READER_REQUIRED_FREE_BYTES,
    )

    private val work get() = WorkManager.getInstance(context)

    /** Bumped when the files change outside WorkManager's knowledge (remove, cancel). */
    private val filesChanged = MutableStateFlow(0)

    val status: Flow<ReaderStatus> = combine(
        work.getWorkInfosForUniqueWorkFlow(WORK_NAME).map { it.firstOrNull().toDownloadWork() },
        filesChanged,
    ) { downloadWork, _ ->
        readerStatus(
            supported = isSupported(),
            installed = downloader.isInstalled(),
            work = downloadWork,
            freeBytes = freeBytes(),
        )
    }

    /** Starts (or restarts) the download; resumes a partial file. Honours "Download on Wi-Fi only". */
    suspend fun download() {
        val wifiOnly = prefs.readerWifiOnly.first()
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.SECONDS)
            .build()
        work.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /** The Wi-Fi-only toggle changed: a download already queued or running picks up the new constraint. */
    suspend fun networkRuleChanged() {
        val info = work.getWorkInfosForUniqueWork(WORK_NAME).get().firstOrNull()
        if (info != null && !info.state.isFinished) download()
    }

    /** Stops the task and deletes the partial file. */
    fun cancel() {
        work.cancelUniqueWork(WORK_NAME)
        downloader.discardPartial()
        filesChanged.value++
    }

    /** Deletes the model; the status goes back to Not downloaded. */
    fun remove() {
        work.cancelUniqueWork(WORK_NAME)
        work.pruneWork()
        downloader.remove()
        filesChanged.value++
    }

    /** Called by the worker when it wrote or deleted files. */
    fun filesChanged() {
        filesChanged.value++
    }

    private fun freeBytes(): Long {
        modelDir.mkdirs()
        return StatFs(context.filesDir.path).availableBytes
    }

    /**
     * Gemma 4 E2B needs a 64-bit phone with about 4 GB of RAM. This threshold is an assumption to
     * revisit with real-device results (the target Pixel 7 Pro has 12 GB).
     */
    private fun isSupported(): Boolean {
        val memory = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
        return Build.SUPPORTED_64_BIT_ABIS.isNotEmpty() && memory.totalMem >= MIN_RAM_BYTES
    }

    companion object {
        const val WORK_NAME = "reader-model-download"
        const val KEY_PERCENT = "percent"
        const val KEY_FAILURE = "failure"
        private const val MIN_RAM_BYTES = 3_500_000_000L
    }
}

private fun WorkInfo?.toDownloadWork(): DownloadWork {
    if (this == null) return DownloadWork.Idle
    val percent = progress.getInt(ReaderController.KEY_PERCENT, 0)
    return when (state) {
        WorkInfo.State.RUNNING -> DownloadWork.Running(percent)
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadWork.Queued(percent)
        WorkInfo.State.FAILED -> {
            val name = outputData.getString(ReaderController.KEY_FAILURE)
            DownloadWork.Failed(DownloadFailure.entries.firstOrNull { it.name == name } ?: DownloadFailure.ChecksumMismatch)
        }
        WorkInfo.State.SUCCEEDED, WorkInfo.State.CANCELLED -> DownloadWork.Idle
    }
}

internal fun failureData(reason: DownloadFailure): Data =
    Data.Builder().putString(ReaderController.KEY_FAILURE, reason.name).build()
