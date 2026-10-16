package com.simmer.app.data

import android.content.Context
import android.os.StatFs
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.simmer.app.domain.DownloadWork
import com.simmer.app.domain.WhisperModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Whisper model of Listen: downloaded on the first Listen through the same resumable [ModelDownloader] and
 * WorkManager task ([ModelDownloadWorker]) as the Recipe reader model.
 */
@Singleton
class WhisperModelController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PrefsRepository,
) : ListenModel {
    private val modelDir = File(context.filesDir, "models")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val downloader = ModelDownloader(
        dir = modelDir,
        fileName = WhisperModel.FILE_NAME,
        sha256 = WhisperModel.SHA256,
        source = HttpModelSource(WhisperModel.URL),
        freeBytes = {
            modelDir.mkdirs()
            StatFs(context.filesDir.path).availableBytes
        },
        requiredFreeBytes = WhisperModel.REQUIRED_FREE_BYTES,
    )

    private val work get() = WorkManager.getInstance(context)

    override val sizeBytes: Long = WhisperModel.SIZE_BYTES

    override fun isInstalled(): Boolean = downloader.isInstalled()

    fun modelPath(): String = File(modelDir, WhisperModel.FILE_NAME).path

    override val downloadWork: Flow<DownloadWork> =
        work.getWorkInfosForUniqueWorkFlow(WORK_NAME).map { infos ->
            val info: WorkInfo? = infos.firstOrNull()
            toDownloadWork(
                info?.state,
                info?.progress?.getInt(ReaderController.KEY_PERCENT, 0) ?: 0,
                info?.outputData?.getString(ReaderController.KEY_FAILURE),
            )
        }

    override fun startDownload() {
        scope.launch {
            val wifiOnly = prefs.readerWifiOnly.first()
            val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setInputData(workDataOf(ModelDownloadWorker.KEY_MODEL to ModelDownloadWorker.MODEL_WHISPER))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.SECONDS)
                .build()
            work.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }

    companion object {
        const val WORK_NAME = "whisper-model-download"
    }
}
