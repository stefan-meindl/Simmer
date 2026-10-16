package com.simmer.app.domain

import kotlin.math.abs

/** The whisper.cpp base model (q5_1) Listen transcribes with, from the ggerganov/whisper.cpp repo on Hugging Face. */
object WhisperModel {
    const val FILE_NAME = "ggml-base-q5_1.bin"

    /** Pinned to a commit of ggerganov/whisper.cpp so [SHA256] always matches the bytes. */
    const val URL =
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-base-q5_1.bin"
    const val SHA256 = "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898"
    const val SIZE_BYTES = 59_707_625L

    /** Free space the download needs before it starts (model plus working room). */
    const val REQUIRED_FREE_BYTES = 300_000_000L
}

/** Listen stops by itself after this long (the 3-minute cap of the capture). */
const val LISTEN_MAX_SECONDS = 180

/** Whisper reads 16 kHz mono; the capture records at this rate. */
const val LISTEN_SAMPLE_RATE = 16_000

/** Below this peak (about -46 dBFS) a capture counts as silence. */
private const val SILENCE_PEAK = 0.005f

/** True when [pcm] (float samples in -1..1) holds no audible sound, or no samples at all. */
fun isSilent(pcm: FloatArray): Boolean = pcm.none { abs(it) >= SILENCE_PEAK }

private val NON_SPEECH_TAG = Regex("""\[[^\]]*]""")

/** Whisper's raw text without its bracketed non-speech tags ("[BLANK_AUDIO]", "[MUSIC]"), single-spaced and trimmed. */
fun cleanTranscript(raw: String): String = raw.replace(NON_SPEECH_TAG, " ").replace(Regex("""\s+"""), " ").trim()

/** Why a Listen run ended without a transcript, apart from silence and cancelling. */
enum class ListenFailure {
    /** The audio capture could not start (no projection, no audio session). */
    CaptureFailed,

    /** Whisper threw while transcribing. */
    TranscribeFailed,
}

/** Where a Listen run stands, as the Reel card and the notification show it. */
sealed interface ListenState {
    data object Idle : ListenState

    /** The Whisper model is missing: ask to download [sizeBytes] first. */
    data class ConfirmDownload(val sizeBytes: Long) : ListenState

    /** The model download task: [waiting] while it waits for a connection. */
    data class Downloading(val percent: Int, val waiting: Boolean) : ListenState
    data class DownloadFailed(val reason: DownloadFailure) : ListenState

    /** The explainer, then the RECORD_AUDIO request. */
    data object AwaitingPermission : ListenState
    data object PermissionDenied : ListenState

    /** The system screen-capture consent. */
    data object AwaitingConsent : ListenState
    data class Capturing(val elapsedSeconds: Int) : ListenState
    data object Transcribing : ListenState

    /** The transcript is ready to go into the text field. */
    data class Done(val transcript: String) : ListenState

    /** No audio was captured ("No audio captured": Retry or Paste). */
    data object Silent : ListenState
    data object Cancelled : ListenState

    /** The process died while capturing or transcribing; the audio is gone. */
    data object Interrupted : ListenState
    data class Failed(val reason: ListenFailure) : ListenState

    /** A run is under way or waiting on the user, so Listen cannot start another. */
    val active: Boolean
        get() = when (this) {
            is ConfirmDownload, is Downloading, AwaitingPermission, AwaitingConsent, is Capturing, Transcribing -> true
            else -> false
        }
}

/** [text] with the Reel's [transcript] added under a [label] line, after anything already there. */
fun withTranscript(text: String, transcript: String, label: String): String = withCaption(text, transcript, label)
