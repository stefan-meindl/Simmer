package com.simmer.app.reader

import com.simmer.app.domain.DownloadFailure
import com.simmer.app.domain.DownloadProgress
import com.simmer.app.domain.ReaderStatus
import com.simmer.app.domain.RecipeReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The [RecipeReader] that runs Gemma 4 E2B through LiteRT-LM. It keeps at most one loaded engine:
 * created on the first [generate] (off the caller's thread, GPU first and CPU as fallback), reused
 * by later calls, and closed after [idleTimeoutMs] without use or on [unload] (low memory).
 * [scope] hosts the idle timer; [engineDispatcher] runs the slow load and close.
 */
class GemmaReader(
    private val source: ReaderModelSource,
    private val factory: LlmEngineFactory,
    private val thermal: ThermalMonitor,
    private val scope: CoroutineScope,
    private val engineDispatcher: CoroutineDispatcher,
    private val idleTimeoutMs: Long = IDLE_TIMEOUT_MS,
    private val thermalPollMs: Long = THERMAL_POLL_MS,
) : RecipeReader {
    private val lock = Mutex()

    // Guarded by [lock], except [useCount] which only grows and is read inside the lock.
    private var engine: LlmEngine? = null
    private var idleTimer: Job? = null

    @Volatile
    private var useCount = 0

    override suspend fun status(): ReaderStatus = source.status.first()

    override fun download(): Flow<DownloadProgress> = flow {
        source.download()
        emitAll(
            source.status
                .map { it.toProgress() }
                .filterNotNull()
                .distinctUntilChanged()
                .transformWhile { progress ->
                    emit(progress)
                    progress is DownloadProgress.Running
                },
        )
    }

    override suspend fun generate(system: String, user: String, maxTokens: Int): Flow<String> = flow {
        val ticket = ++useCount
        lock.withLock {
            idleTimer?.cancel()
            try {
                // Between passes: wait for the phone to cool down instead of heating it further.
                while (thermal.isSevere()) delay(thermalPollMs)
                val loaded = engine ?: load().also { engine = it }
                emitAll(loaded.generate(system, user, maxTokens, TEMPERATURE))
            } finally {
                startIdleTimer(ticket)
            }
        }
    }

    /** Closes the engine now (low memory). Waits for a running generation to finish first. */
    fun unload() {
        scope.launch { lock.withLock { closeEngine() } }
    }

    private fun startIdleTimer(ticket: Int) {
        idleTimer = scope.launch {
            delay(idleTimeoutMs)
            lock.withLock { if (ticket == useCount) closeEngine() }
        }
    }

    private suspend fun closeEngine() {
        val loaded = engine ?: return
        engine = null
        withContext(engineDispatcher) { loaded.close() }
    }

    private suspend fun load(): LlmEngine {
        check(source.status.first() == ReaderStatus.Ready) { "The Reader model is not downloaded" }
        val path = source.modelPath()
        return withContext(engineDispatcher) {
            try {
                factory.create(path, InferenceBackend.Gpu)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                factory.create(path, InferenceBackend.Cpu)
            }
        }
    }

    companion object {
        const val IDLE_TIMEOUT_MS = 60_000L
        const val THERMAL_POLL_MS = 5_000L
        const val TEMPERATURE = 0.2
    }
}

private fun ReaderStatus.toProgress(): DownloadProgress? = when (this) {
    is ReaderStatus.Downloading -> DownloadProgress.Running(percent, waiting)
    ReaderStatus.Ready -> DownloadProgress.Complete
    is ReaderStatus.Failed -> DownloadProgress.Failed(reason)
    ReaderStatus.NotEnoughStorage -> DownloadProgress.Failed(DownloadFailure.NotEnoughStorage)
    ReaderStatus.NotSupported -> DownloadProgress.Failed(DownloadFailure.Unknown)
    // Not downloaded yet: the task has not shown up in WorkManager, keep waiting.
    ReaderStatus.NotDownloaded -> null
}
