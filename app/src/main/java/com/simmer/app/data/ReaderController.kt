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
import com.simmer.app.reader.GemmaModelControl
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
) : GemmaModelControl {
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

    /** Bumped when the files change outside WorkManager's knowledge (remove, worker finished). */
    private val filesVersion = MutableStateFlow(0)

    override val status: Flow<ReaderStatus> = combine(
        work.getWorkInfosForUniqueWorkFlow(WORK_NAME).map { it.firstOrNull().toDownloadWork() },
        filesVersion,
    ) { downloadWork, _ ->
        readerStatus(
            supported = isSupported(),
            installed = downloader.isInstalled(),
            work = downloadWork.keepingPartialProgress(),
            freeBytes = freeBytes(),
        )
    }

    /** Starts (or restarts) the download; resumes a partial file. Honours "Download on Wi-Fi only". */
    override suspend fun download() {
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

    override fun modelPath(): String = File(modelDir, ReaderModel.FILE_NAME).path

    /** Stops the task. The partial file stays, so a later Download resumes where it stopped. */
    override fun cancel() {
        work.cancelUniqueWork(WORK_NAME)
    }

    /**
     * A re-queued task (e.g. after the Wi-Fi toggle) starts with no progress; show at least what the
     * partial file already holds, so the visible percentage does not jump back to 0.
     */
    private fun DownloadWork.keepingPartialProgress(): DownloadWork {
        val onDisk = (downloader.partialBytes() * 100 / ReaderModel.SIZE_BYTES).toInt().coerceIn(0, 99)
        return when (this) {
            is DownloadWork.Running -> copy(percent = maxOf(percent, onDisk))
            is DownloadWork.Queued -> copy(percent = maxOf(percent, onDisk))
            else -> this
        }
    }

    /** Deletes the model; the status goes back to Not downloaded. */
    override fun remove() {
        work.cancelUniqueWork(WORK_NAME)
        work.pruneWork()
        downloader.remove()
        filesVersion.value++
    }

    /** Called by the worker when it wrote or deleted files. */
    fun filesChanged() {
        filesVersion.value++
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

private fun WorkInfo?.toDownloadWork(): DownloadWork = toDownloadWork(
    this?.state,
    this?.progress?.getInt(ReaderController.KEY_PERCENT, 0) ?: 0,
    this?.outputData?.getString(ReaderController.KEY_FAILURE),
)

/** Maps the task's WorkManager [state], reported [percent] and stored failure name to a [DownloadWork]. */
internal fun toDownloadWork(state: WorkInfo.State?, percent: Int, failureName: String?): DownloadWork = when (state) {
    null, WorkInfo.State.SUCCEEDED, WorkInfo.State.CANCELLED -> DownloadWork.Idle
    WorkInfo.State.RUNNING -> DownloadWork.Running(percent)
    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadWork.Queued(percent)
    WorkInfo.State.FAILED ->
        DownloadWork.Failed(DownloadFailure.entries.firstOrNull { it.name == failureName } ?: DownloadFailure.Unknown)
}

internal fun failureData(reason: DownloadFailure): Data =
    Data.Builder().putString(ReaderController.KEY_FAILURE, reason.name).build()
