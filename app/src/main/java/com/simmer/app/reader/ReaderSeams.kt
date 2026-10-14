package com.simmer.app.reader

import com.simmer.app.domain.ReaderStatus
import com.simmer.app.domain.DownloadProgress
import kotlinx.coroutines.flow.Flow

/** Both engines generate at this temperature; the Pipeline above them is identical. */
const val READER_TEMPERATURE = 0.2

/** Where the Reader model lives and how it gets there. Implemented by `ReaderController`. */
interface ReaderModelSource {
    val status: Flow<ReaderStatus>

    /** Starts (or restarts) the download; resumes a partial file. */
    suspend fun download()

    /** Absolute path of the verified model file (only meaningful once [status] is Ready). */
    fun modelPath(): String
}

/** What Settings can do to the Gemma model file; implemented by `ReaderController`. */
interface GemmaModelControl : ReaderModelSource {
    /** Stops the task; the partial file stays so a later download resumes. */
    fun cancel()

    /** Deletes the model. */
    fun remove()
}

enum class InferenceBackend { Gpu, Cpu }

/** A loaded model. Closing it frees the memory. */
interface LlmEngine : AutoCloseable {
    /** Streams reply tokens for one prompt. */
    fun generate(system: String, user: String, maxTokens: Int, temperature: Double): Flow<String>
}

/** Loads a model on [backend]. Blocking and slow (seconds): callers run it off the main thread. Throws if the backend cannot start. */
fun interface LlmEngineFactory {
    fun create(modelPath: String, backend: InferenceBackend): LlmEngine
}

/** Reads the phone's thermal status. */
fun interface ThermalMonitor {
    /** True when the phone is hot enough that generation should wait (THERMAL_STATUS_SEVERE or worse). */
    fun isSevere(): Boolean
}

enum class NanoAvailability { Available, Downloadable, Downloading, Unavailable }

/** Whether Gemini Nano (ML Kit GenAI) can run on this phone. */
fun interface NanoAvailabilityCheck {
    suspend fun check(): NanoAvailability
}

/** Per-app quota hit (ML Kit `ErrorCode.BUSY`). [retryAfterMs] is the service's own hint, if it gave one. */
class NanoBusyException(val retryAfterMs: Long?) : Exception("Gemini Nano is busy")

/** The Gemini Nano model behind ML Kit GenAI, as [GeminiNanoReader] needs it. The real one is `MlKitNanoClient`. */
interface NanoClient {
    suspend fun availability(): NanoAvailability

    /** Downloads the model; ends with Complete or Failed. */
    fun download(): Flow<DownloadProgress>

    /** Streams reply tokens. Throws [NanoBusyException] when the per-app quota is hit. */
    fun generate(system: String, user: String, maxTokens: Int, temperature: Double): Flow<String>
}
