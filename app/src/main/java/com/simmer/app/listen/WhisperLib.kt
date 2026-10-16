package com.simmer.app.listen

import com.simmer.app.data.Transcriber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The JNI surface of whisper.cpp (`app/src/main/cpp`). Keep this class name: the native function names depend on it. */
internal object WhisperLib {
    init {
        System.loadLibrary("simmer_whisper")
    }

    /** Loads the model file; 0 when it could not be loaded. */
    external fun init(path: String): Long
    external fun free(handle: Long)

    /** Transcript as UTF-8 bytes with the language detected, or null when whisper failed. */
    external fun transcribe(handle: Long, pcm: FloatArray, threads: Int): ByteArray?
}

/**
 * [Transcriber] on whisper.cpp. Loads the model for one transcription and frees it right after, so the
 * model's memory is not held while the user cooks. Runs off the main thread, one transcription at a time.
 */
class WhisperTranscriber(private val modelPath: () -> String) : Transcriber {
    private val one = Mutex()

    override suspend fun transcribe(pcm: FloatArray): String = one.withLock {
        withContext(Dispatchers.Default) {
            val handle = WhisperLib.init(modelPath())
            check(handle != 0L) { "Could not load the Whisper model" }
            try {
                val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
                val bytes = WhisperLib.transcribe(handle, pcm, threads) ?: error("Whisper failed")
                String(bytes, Charsets.UTF_8).trim()
            } finally {
                WhisperLib.free(handle)
            }
        }
    }
}
