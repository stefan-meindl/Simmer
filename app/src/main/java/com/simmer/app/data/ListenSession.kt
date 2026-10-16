package com.simmer.app.data

import com.simmer.app.domain.DownloadWork
import com.simmer.app.domain.LISTEN_MAX_SECONDS
import com.simmer.app.domain.ListenFailure
import com.simmer.app.domain.ListenState
import com.simmer.app.domain.cleanTranscript
import com.simmer.app.domain.isSilent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** The Reel's internal audio. [stop] returns what was captured as 16 kHz mono floats in -1..1 and releases the capture. */
interface AudioSource {
    /** Throws when the capture cannot start. */
    fun start()
    fun stop(): FloatArray
}

/** On-device speech to text; the language is detected. Returns blank text when it heard no words. */
fun interface Transcriber {
    suspend fun transcribe(pcm: FloatArray): String
}

/** The Whisper model file: whether it is there, how big it is, and the download task ([DownloadWork]). */
interface ListenModel {
    val sizeBytes: Long
    fun isInstalled(): Boolean
    val downloadWork: Flow<DownloadWork>

    /** Enqueues the download task. */
    fun startDownload()

    /** Cancels the download task (the partial file stays for a later resume). */
    fun cancelDownload()
}

/** What survives process death of a Listen run. Audio is never saved. */
sealed interface PersistedListen {
    data object Capturing : PersistedListen
    data object Transcribing : PersistedListen

    /** Finished, not yet put in the text field. */
    data class Done(val transcript: String) : PersistedListen
}

interface ListenPersistence {
    fun load(): PersistedListen?
    fun save(value: PersistedListen?)
}

/**
 * The one Listen run, owned by the app and not the screen, so the notification's Stop and the capture keep working
 * with the app in the background. Audio is buffered in memory by [audio] and discarded after transcription.
 * A finished transcript stays saved until [acknowledge]d; a capture or transcription cut off by process death
 * comes back as [ListenState.Interrupted] ([restore]).
 *
 * Threading: public functions come from the main thread and the service, the ticks and the transcription from
 * [scope]'s dispatcher, so every change of state happens under [lock]. The blocking [AudioSource.stop] runs outside it.
 */
class ListenSession(
    private val audio: AudioSource,
    private val transcriber: Transcriber,
    private val model: ListenModel,
    private val persistence: ListenPersistence,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow<ListenState>(ListenState.Idle)
    val state: StateFlow<ListenState> = mutableState

    private val lock = Any()
    private var restored = false
    private var generation = 0
    private var ticks: Job? = null
    private var work: Job? = null

    /** [AudioSource.stop] is under way (outside [lock]); the state is still Capturing until it returns. */
    private var stopping = false

    /** The Listen button: starts a run, asking for the model download first when it is missing. Ignored while a run is active. */
    fun listen() = synchronized(lock) {
        // A finished transcript nobody took yet is not thrown away by a new run.
        if (mutableState.value.active || mutableState.value is ListenState.Done) return@synchronized
        generation++
        mutableState.value =
            if (model.isInstalled()) ListenState.AwaitingPermission else ListenState.ConfirmDownload(model.sizeBytes)
    }

    /** The size was confirmed (or Retry after a failed download): start the task and follow it. */
    fun confirmDownload() = synchronized(lock) {
        val current = mutableState.value
        if (current !is ListenState.ConfirmDownload && current !is ListenState.DownloadFailed) return@synchronized
        val mine = ++generation
        mutableState.value = ListenState.Downloading(0, waiting = true)
        model.startDownload()
        work?.cancel()
        work = scope.launch {
            model.downloadWork.collect { downloadWork ->
                ifCurrent(mine) {
                    when (downloadWork) {
                        is DownloadWork.Running -> mutableState.value = ListenState.Downloading(downloadWork.percent, waiting = false)
                        is DownloadWork.Queued -> mutableState.value = ListenState.Downloading(downloadWork.percent, waiting = true)
                        is DownloadWork.Failed -> mutableState.value = ListenState.DownloadFailed(downloadWork.reason)
                        DownloadWork.Idle -> if (model.isInstalled()) {
                            mutableState.value = ListenState.AwaitingPermission
                            work?.cancel()
                        }
                    }
                }
            }
        }
    }

    fun permissionGranted() = synchronized(lock) {
        if (mutableState.value == ListenState.AwaitingPermission) mutableState.value = ListenState.AwaitingConsent
    }

    fun permissionDenied() = synchronized(lock) {
        if (mutableState.value == ListenState.AwaitingPermission) mutableState.value = ListenState.PermissionDenied
    }

    fun consentDenied() = synchronized(lock) {
        if (mutableState.value == ListenState.AwaitingConsent) mutableState.value = ListenState.Cancelled
    }

    /** The system consent was given and the projection is attached to [audio]: start capturing. */
    fun consentGranted() = synchronized(lock) {
        if (mutableState.value != ListenState.AwaitingConsent) {
            // Nothing is waiting for this consent (the process was recreated while the dialog was up): say so.
            if (mutableState.value == ListenState.Idle) mutableState.value = ListenState.Interrupted
            return@synchronized
        }
        try {
            audio.start()
        } catch (e: Exception) {
            mutableState.value = ListenState.Failed(ListenFailure.CaptureFailed)
            return@synchronized
        }
        val mine = ++generation
        persistence.save(PersistedListen.Capturing)
        mutableState.value = ListenState.Capturing(0)
        ticks = scope.launch {
            for (second in 1..LISTEN_MAX_SECONDS) {
                delay(1_000)
                val last = second == LISTEN_MAX_SECONDS
                ifCurrent(mine) {
                    if (!last) mutableState.value = ListenState.Capturing(second)
                }
                if (last) stopCapture(mine)
            }
        }
    }

    /** Stop (notification, button) and the projection ending (screen lock, status bar chip) both end the capture. */
    fun stop() = stopCapture(null)

    fun projectionEnded() = stop()

    /** [expected]: the generation of the tick that asks, so a tick of an older run does nothing. */
    private fun stopCapture(expected: Int?) {
        val mine = synchronized(lock) {
            if (mutableState.value !is ListenState.Capturing || stopping || (expected != null && expected != generation)) return
            ticks?.cancel()
            stopping = true
            ++generation
        }
        // Joins the capture thread: not under the lock, and not while the main thread is waiting for it.
        val pcm = try {
            audio.stop()
        } catch (e: Exception) {
            ifCurrent(mine) {
                stopping = false
                persistence.save(null)
                mutableState.value = ListenState.Failed(ListenFailure.CaptureFailed)
            }
            return
        }
        ifCurrent(mine) {
            stopping = false
            if (isSilent(pcm)) {
                persistence.save(null)
                mutableState.value = ListenState.Silent
            } else {
                transcribe(pcm, mine)
            }
        }
    }

    /** Callers hold [lock]. */
    private fun transcribe(pcm: FloatArray, mine: Int) {
        persistence.save(PersistedListen.Transcribing)
        mutableState.value = ListenState.Transcribing
        work = scope.launch {
            try {
                val text = cleanTranscript(transcriber.transcribe(pcm))
                ifCurrent(mine) {
                    if (text.isEmpty()) {
                        persistence.save(null)
                        mutableState.value = ListenState.Silent
                    } else {
                        persistence.save(PersistedListen.Done(text))
                        mutableState.value = ListenState.Done(text)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ifCurrent(mine) {
                    persistence.save(null)
                    mutableState.value = ListenState.Failed(ListenFailure.TranscribeFailed)
                }
            }
        }
    }

    /** Ends any active run: the audio is thrown away, nothing is transcribed and a model download stops. */
    fun cancel() {
        val before = synchronized(lock) {
            val current = mutableState.value
            if (!current.active) return
            generation++
            ticks?.cancel()
            work?.cancel()
            persistence.save(null)
            mutableState.value = ListenState.Cancelled
            // A stop already under way releases the capture itself.
            val release = current is ListenState.Capturing && !stopping
            stopping = false
            current to release
        }
        if (before.second) audio.stop()
        if (before.first is ListenState.Downloading) model.cancelDownload()
    }

    /** The transcript went into the text field. */
    fun acknowledge() = synchronized(lock) {
        if (mutableState.value !is ListenState.Done) return@synchronized
        persistence.save(null)
        mutableState.value = ListenState.Idle
    }

    /** The user closed a finished run's message (silence, failure, cancelled). An active run stays. */
    fun dismiss() = synchronized(lock) {
        if (mutableState.value.active) return@synchronized
        persistence.save(null)
        mutableState.value = ListenState.Idle
    }

    /** After the process was recreated. Once per process. */
    fun restore() = synchronized(lock) {
        if (restored) return@synchronized
        restored = true
        when (val saved = persistence.load()) {
            null -> Unit
            is PersistedListen.Done -> mutableState.value = ListenState.Done(saved.transcript)
            PersistedListen.Capturing, PersistedListen.Transcribing -> {
                persistence.save(null)
                mutableState.value = ListenState.Interrupted
            }
        }
    }

    private fun ifCurrent(run: Int, block: () -> Unit) = synchronized(lock) {
        if (run == generation) block()
    }
}
