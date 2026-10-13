package com.simmer.app.reader

import com.simmer.app.domain.DownloadFailure
import com.simmer.app.domain.DownloadProgress
import com.simmer.app.domain.ReaderStatus
import com.simmer.app.domain.RecipeReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Thrown by [GemmaReader.generate] when the phone stayed too hot for too long to start a pass. */
class ThermalPauseTimeout : IllegalStateException("The phone stayed too hot to start generating")

/**
 * The [RecipeReader] that runs Gemma 4 E2B through LiteRT-LM. It keeps at most one loaded engine:
 * created on the first [generate] (off the caller's thread, GPU first and CPU as fallback), reused
 * by later calls, and closed after [idleTimeoutMs] without use or on [unload] (low memory).
 * [scope] hosts the idle timer; [engineDispatcher] runs the slow load and close.
 *
 * Generations are single-flight on purpose: one engine runs one conversation at a time, so a second
 * [generate] waits for the first to finish. [unload] and the idle timer never wait for a collector:
 * while a generation runs they only mark the engine to be closed when it ends.
 */
class GemmaReader(
    private val source: ReaderModelSource,
    private val factory: LlmEngineFactory,
    private val thermal: ThermalMonitor,
    private val scope: CoroutineScope,
    private val engineDispatcher: CoroutineDispatcher,
    private val idleTimeoutMs: Long = IDLE_TIMEOUT_MS,
    private val thermalPollMs: Long = THERMAL_POLL_MS,
    private val thermalMaxWaitMs: Long = THERMAL_MAX_WAIT_MS,
) : RecipeReader {
    /** Serialises generations. */
    private val singleFlight = Mutex()

    /** Guards the fields below; only held briefly (and for the engine load). */
    private val state = Mutex()
    private var engine: LlmEngine? = null
    private var generating = false
    private var closeWhenDone = false
    private var idleTimer: Job? = null
    private var epoch = 0L

    /** THROWAWAY: which backend the last load used. */
    @Volatile var lastBackend: String = "not loaded yet"

    override suspend fun status(): ReaderStatus = source.status.first()

    override fun download(): Flow<DownloadProgress> = flow {
        // A failure left over from an earlier run shows until the new task replaces it; skip it.
        val before = source.status.first()
        source.download()
        emitAll(
            source.status
                .dropWhile { it is ReaderStatus.Failed && it == before }
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
        awaitCoolDown()
        singleFlight.withLock {
            val loaded = state.withLock {
                epoch++
                idleTimer?.cancel()
                closeWhenDone = false
                generating = true
                try {
                    engine ?: load().also { engine = it }
                } catch (e: Throwable) {
                    generating = false
                    startIdleTimerLocked()
                    throw e
                }
            }
            try {
                emitAll(loaded.generate(system, user, maxTokens, TEMPERATURE))
            } finally {
                withContext(NonCancellable) {
                    state.withLock {
                        generating = false
                        if (closeWhenDone) closeEngineLocked() else startIdleTimerLocked()
                    }
                }
            }
        }
    }

    /** Closes the engine now (low memory), or as soon as a running generation ends. Never waits for a collector. */
    fun unload() {
        scope.launch {
            state.withLock {
                if (generating) closeWhenDone = true else closeEngineLocked()
            }
        }
    }

    /** Between passes: wait for the phone to cool down instead of heating it further, up to [thermalMaxWaitMs]. */
    private suspend fun awaitCoolDown() {
        var waited = 0L
        while (thermal.isSevere()) {
            if (waited >= thermalMaxWaitMs) throw ThermalPauseTimeout()
            delay(thermalPollMs)
            waited += thermalPollMs
        }
    }

    private fun startIdleTimerLocked() {
        val ticket = epoch
        idleTimer = scope.launch {
            delay(idleTimeoutMs)
            state.withLock { if (ticket == epoch && !generating) closeEngineLocked() }
        }
    }

    private suspend fun closeEngineLocked() {
        val loaded = engine ?: return
        engine = null
        closeWhenDone = false
        withContext(engineDispatcher) { loaded.close() }
    }

    private suspend fun load(): LlmEngine {
        check(source.status.first() == ReaderStatus.Ready) { "The Reader model is not downloaded" }
        val path = source.modelPath()
        return withContext(engineDispatcher) {
            attempt { factory.create(path, InferenceBackend.Gpu).also { lastBackend = "GPU" } }
                .getOrElse { err -> factory.create(path, InferenceBackend.Cpu).also { lastBackend = "CPU (GPU failed: ${err.message})" } }
        }
    }

    companion object {
        const val IDLE_TIMEOUT_MS = 60_000L
        const val THERMAL_POLL_MS = 5_000L
        const val THERMAL_MAX_WAIT_MS = 5 * 60_000L
        const val TEMPERATURE = 0.2
    }
}

/** Runs [block]; an [Exception] is returned as a failure, while [Error]s (e.g. out of memory) and cancellation propagate. */
internal inline fun <T> attempt(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
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
