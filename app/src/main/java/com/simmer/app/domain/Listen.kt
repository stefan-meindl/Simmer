package com.simmer.app.domain

import kotlin.math.abs

/** Listen stops by itself after this long (the 3-minute cap of the capture). */
const val LISTEN_MAX_SECONDS = 180

/** Whisper reads 16 kHz mono; the capture records at this rate. */
const val LISTEN_SAMPLE_RATE = 16_000

/** Below this peak (about -46 dBFS) a capture counts as silence. */
private const val SILENCE_PEAK = 0.005f

/** True when [pcm] (float samples in -1..1) holds no audible sound, or no samples at all. */
fun isSilent(pcm: FloatArray): Boolean = pcm.none { abs(it) >= SILENCE_PEAK }

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
