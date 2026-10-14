package com.simmer.app.reader

import com.simmer.app.domain.DownloadProgress
import com.simmer.app.domain.ReaderStatus
import com.simmer.app.domain.RecipeReader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** Which engine runs on this phone and what the Settings "Recipe reader" section should show for it. */
data class ReaderState(val engine: ReaderEngine, val status: ReaderStatus)

/**
 * The reader state for the UI. Gemma phones show [gemmaStatus] (the model file and its download task).
 * Gemini Nano phones show the system model's status; a Nano download is run here and its outcome
 * overrides the polled status until the screen next loads.
 */
class ReaderStateSource(
    private val selector: ReaderSelector,
    private val gemmaStatus: Flow<ReaderStatus>,
    private val nano: RecipeReader,
) {
    private val nanoOverride = MutableStateFlow<ReaderStatus?>(null)

    val state: Flow<ReaderState> = flow {
        when (selector.engine()) {
            ReaderEngine.Gemma -> emitAll(gemmaStatus.map { ReaderState(ReaderEngine.Gemma, it) })
            ReaderEngine.Nano -> {
                val polled = nano.status()
                emitAll(nanoOverride.map { ReaderState(ReaderEngine.Nano, it ?: polled) })
            }
        }
    }

    /** Runs the Gemini Nano download to its end; call from a scope that outlives the screen. */
    suspend fun downloadNano() {
        nano.download().collect { progress ->
            nanoOverride.value = when (progress) {
                is DownloadProgress.Running -> ReaderStatus.Downloading(progress.percent, progress.waiting)
                DownloadProgress.Complete -> ReaderStatus.Ready
                is DownloadProgress.Failed -> ReaderStatus.Failed(progress.reason)
            }
        }
    }
}
