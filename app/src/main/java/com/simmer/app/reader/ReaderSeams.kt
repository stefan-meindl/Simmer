package com.simmer.app.reader

import com.simmer.app.domain.ReaderStatus
import kotlinx.coroutines.flow.Flow

/** Where the Reader model lives and how it gets there. Implemented by `ReaderController`. */
interface ReaderModelSource {
    val status: Flow<ReaderStatus>

    /** Starts (or restarts) the download; resumes a partial file. */
    suspend fun download()

    /** Absolute path of the verified model file (only meaningful once [status] is Ready). */
    fun modelPath(): String
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

enum class NanoAvailability { Available, Downloadable, Unavailable }

/** Whether Gemini Nano (ML Kit GenAI) can run on this phone. */
fun interface NanoAvailabilityCheck {
    suspend fun check(): NanoAvailability
}
