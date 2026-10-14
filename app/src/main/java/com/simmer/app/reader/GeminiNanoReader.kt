package com.simmer.app.reader

import com.simmer.app.domain.DownloadProgress
import com.simmer.app.domain.ReaderStatus
import com.simmer.app.domain.RecipeReader
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * The [RecipeReader] that runs Gemini Nano through ML Kit GenAI (Pixel 9 and other supported phones).
 * The model is owned by the system (AICore), so there is nothing to load, unload or cool down here.
 * Same temperature as [GemmaReader]; the Pipeline above it is identical.
 *
 * When the per-app quota is hit ([NanoBusyException]) before any token arrived, it waits
 * ([retryDelayMs], or the service's hint) and retries once; a second failure reaches the caller.
 * After tokens have been emitted a retry would repeat them, so that failure is passed on at once.
 */
class GeminiNanoReader(
    private val client: NanoClient,
    private val wait: suspend (Long) -> Unit = { delay(it) },
    private val retryDelayMs: Long = BUSY_RETRY_DELAY_MS,
) : RecipeReader {

    override suspend fun status(): ReaderStatus = when (client.availability()) {
        NanoAvailability.Available -> ReaderStatus.Ready
        NanoAvailability.Downloadable -> ReaderStatus.NotDownloaded
        NanoAvailability.Downloading -> ReaderStatus.Downloading(percent = 0, waiting = false)
        NanoAvailability.Unavailable -> ReaderStatus.NotSupported
    }

    override fun download(): Flow<DownloadProgress> = client.download()

    override suspend fun generate(system: String, user: String, maxTokens: Int): Flow<String> = flow {
        var emitted = false
        var retried = false
        while (true) {
            try {
                client.generate(system, user, maxTokens, GemmaReader.TEMPERATURE).collect {
                    emitted = true
                    emit(it)
                }
                return@flow
            } catch (busy: NanoBusyException) {
                if (emitted || retried) throw busy
                retried = true
                wait(busy.retryAfterMs ?: retryDelayMs)
            }
        }
    }

    companion object {
        const val BUSY_RETRY_DELAY_MS = 2_000L
    }
}
