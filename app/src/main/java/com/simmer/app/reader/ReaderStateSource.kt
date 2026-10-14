package com.simmer.app.reader

import com.simmer.app.domain.DownloadProgress
import com.simmer.app.domain.ReaderStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Which engine runs on this phone and what the Settings "Recipe reader" section should show for it. */
data class ReaderState(val engine: ReaderEngine, val status: ReaderStatus)

/**
 * The reader state for the UI, and the Settings actions routed to the engine in use.
 *
 * Gemma phones show the model file's status and download, cancel and remove it through [gemma].
 * Gemini Nano phones show the system model's status, polled afresh each time [state] is collected;
 * a Nano download runs in [scope] (app lifetime, so it outlives the screen) and its progress overrides
 * the polled status. A finished download clears the override (the next poll tells the truth); a failure
 * stays shown until [state] is collected again or a new download starts. Nano has no cancel or remove:
 * the system owns the model.
 */
class ReaderStateSource(
    private val selector: ReaderSelector,
    private val gemma: GemmaModelControl,
    private val scope: CoroutineScope,
) {
    private val nanoOverride = MutableStateFlow<ReaderStatus?>(null)
    private var nanoDownload: Job? = null

    /** The engine last shown; the actions follow it. */
    @Volatile private var shown: ReaderSelector.Selection? = null

    val state: Flow<ReaderState> = flow {
        val running = shown?.takeIf { nanoDownload?.isActive == true }
        // While our own Nano download runs, ML Kit says Downloading, which would flip the pick to Gemma.
        val selection = running ?: selector.select()
        shown = selection
        when (selection.engine) {
            ReaderEngine.Gemma -> emitAll(gemma.status.map { ReaderState(ReaderEngine.Gemma, it) })
            ReaderEngine.Nano -> {
                if (running == null) nanoOverride.value = null
                // Polls again whenever the override changes (including back to null).
                emitAll(nanoOverride.map { ReaderState(ReaderEngine.Nano, it ?: selection.reader.status()) })
            }
        }
    }

    /** Starts the download on the engine in use. For Nano it returns at once; progress arrives through [state]. */
    suspend fun download() {
        val selection = shown ?: selector.select().also { shown = it }
        when (selection.engine) {
            ReaderEngine.Gemma -> gemma.download()
            ReaderEngine.Nano -> startNanoDownload(selection)
        }
    }

    fun cancel() {
        if (shown?.engine != ReaderEngine.Nano) gemma.cancel()
    }

    fun remove() {
        if (shown?.engine != ReaderEngine.Nano) gemma.remove()
    }

    private fun startNanoDownload(selection: ReaderSelector.Selection) {
        if (nanoDownload?.isActive == true) return
        nanoDownload = scope.launch {
            selection.reader.download().collect { progress ->
                nanoOverride.value = when (progress) {
                    is DownloadProgress.Running -> ReaderStatus.Downloading(progress.percent, progress.waiting)
                    DownloadProgress.Complete -> null
                    is DownloadProgress.Failed -> ReaderStatus.Failed(progress.reason)
                }
            }
        }
    }
}
