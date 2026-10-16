package com.simmer.app

import com.simmer.app.data.AudioSource
import com.simmer.app.data.ListenModel
import com.simmer.app.data.ListenPersistence
import com.simmer.app.data.ListenSession
import com.simmer.app.data.PersistedListen
import com.simmer.app.data.Transcriber
import com.simmer.app.domain.DownloadFailure
import com.simmer.app.domain.DownloadWork
import com.simmer.app.domain.LISTEN_MAX_SECONDS
import com.simmer.app.domain.ListenFailure
import com.simmer.app.domain.ListenState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val SPEECH = FloatArray(16_000) { if (it % 2 == 0) 0.4f else -0.4f }
private val QUIET = FloatArray(16_000)

private class FakeAudio(var pcm: FloatArray = SPEECH, var failToStart: Boolean = false) : AudioSource {
    var onStop: () -> Unit = {}
    var started = 0
    var stopped = 0
    override fun start() {
        if (failToStart) error("no projection")
        started++
    }
    override fun stop(): FloatArray {
        stopped++
        onStop()
        return pcm
    }
}

private class FakeTranscriber(var text: String = "Add two eggs", var gate: CompletableDeferred<Unit>? = null, var fail: Boolean = false) : Transcriber {
    var calls = 0
    override suspend fun transcribe(pcm: FloatArray): String {
        calls++
        gate?.await()
        if (fail) error("whisper crashed")
        return text
    }
}

private class FakeModel(var installed: Boolean = true) : ListenModel {
    override val sizeBytes = 59_000_000L
    val work = MutableStateFlow<DownloadWork>(DownloadWork.Idle)
    var downloads = 0
    override fun isInstalled() = installed
    override val downloadWork get() = work
    override fun startDownload() { downloads++ }
    var cancelledDownloads = 0
    override fun cancelDownload() { cancelledDownloads++ }
}

private class FakeListenPersistence(var saved: PersistedListen? = null) : ListenPersistence {
    override fun load() = saved
    override fun save(value: PersistedListen?) { saved = value }
}

/** Seam: [ListenSession], the one Listen run that outlives the Add recipe screen, with every platform piece faked. */
@OptIn(ExperimentalCoroutinesApi::class)
class ListenSessionTest {
    private val audio = FakeAudio()
    private val transcriber = FakeTranscriber()
    private val model = FakeModel()
    private val persistence = FakeListenPersistence()

    private fun TestScope.session() = ListenSession(audio, transcriber, model, persistence, scope = backgroundScope)

    private fun TestScope.capturing(): ListenSession = session().also {
        it.listen()
        it.permissionGranted()
        it.consentGranted()
        runCurrent()
    }

    @Test fun `starts idle`() = runTest {
        assertEquals(ListenState.Idle, session().state.value)
    }

    @Test fun `listen with the model installed asks for the microphone permission`() = runTest {
        val s = session()
        s.listen()
        assertEquals(ListenState.AwaitingPermission, s.state.value)
    }

    @Test fun `listen without the model asks to confirm the download size first`() = runTest {
        model.installed = false
        val s = session()
        s.listen()
        assertEquals(ListenState.ConfirmDownload(59_000_000L), s.state.value)
        assertEquals(0, model.downloads)
    }

    @Test fun `declining the download cancels`() = runTest {
        model.installed = false
        val s = session()
        s.listen()
        s.cancel()
        assertEquals(ListenState.Cancelled, s.state.value)
    }

    @Test fun `confirming the download shows its progress and continues to the permission when it is installed`() = runTest {
        model.installed = false
        val s = session()
        s.listen()
        s.confirmDownload()
        runCurrent()
        assertEquals(1, model.downloads)

        model.work.value = DownloadWork.Queued(0)
        runCurrent()
        assertEquals(ListenState.Downloading(0, waiting = true), s.state.value)
        model.work.value = DownloadWork.Running(40)
        runCurrent()
        assertEquals(ListenState.Downloading(40, waiting = false), s.state.value)

        model.installed = true
        model.work.value = DownloadWork.Idle
        runCurrent()
        assertEquals(ListenState.AwaitingPermission, s.state.value)
    }

    @Test fun `a failed download is reported and can be retried`() = runTest {
        model.installed = false
        val s = session()
        s.listen()
        s.confirmDownload()
        model.work.value = DownloadWork.Failed(DownloadFailure.NotEnoughStorage)
        runCurrent()
        assertEquals(ListenState.DownloadFailed(DownloadFailure.NotEnoughStorage), s.state.value)

        s.confirmDownload()
        assertEquals(2, model.downloads)
    }

    @Test fun `granting the permission asks for screen capture consent, denying it says so`() = runTest {
        val s = session()
        s.listen()
        s.permissionGranted()
        assertEquals(ListenState.AwaitingConsent, s.state.value)

        s.cancel()
        s.listen()
        s.permissionDenied()
        assertEquals(ListenState.PermissionDenied, s.state.value)
    }

    @Test fun `denied consent cancels`() = runTest {
        val s = session()
        s.listen()
        s.permissionGranted()
        s.consentDenied()
        assertEquals(ListenState.Cancelled, s.state.value)
        assertEquals(0, audio.started)
    }

    @Test fun `consent starts capturing and counts seconds`() = runTest {
        val s = capturing()
        assertEquals(1, audio.started)
        assertEquals(ListenState.Capturing(0), s.state.value)
        assertEquals(PersistedListen.Capturing, persistence.saved)

        advanceTimeBy(3_100)
        assertEquals(ListenState.Capturing(3), s.state.value)
    }

    @Test fun `stop transcribes and delivers the transcript`() = runTest {
        val gate = CompletableDeferred<Unit>()
        transcriber.gate = gate
        val s = capturing()

        s.stop()
        runCurrent()
        assertEquals(1, audio.stopped)
        assertEquals(ListenState.Transcribing, s.state.value)
        assertEquals(PersistedListen.Transcribing, persistence.saved)

        gate.complete(Unit)
        runCurrent()
        assertEquals(ListenState.Done("Add two eggs"), s.state.value)
        assertEquals(PersistedListen.Done("Add two eggs"), persistence.saved)
    }

    @Test fun `silence is reported without transcribing`() = runTest {
        audio.pcm = QUIET
        val s = capturing()
        s.stop()
        runCurrent()
        assertEquals(ListenState.Silent, s.state.value)
        assertEquals(0, transcriber.calls)
        assertNull(persistence.saved)
    }

    @Test fun `a transcript with no words counts as silence`() = runTest {
        transcriber.text = "  "
        val s = capturing()
        s.stop()
        runCurrent()
        assertEquals(ListenState.Silent, s.state.value)
    }

    @Test fun `capture stops by itself after the cap`() = runTest {
        val s = capturing()
        advanceTimeBy(LISTEN_MAX_SECONDS * 1_000L - 500)
        assertTrue(s.state.value is ListenState.Capturing)
        advanceTimeBy(1_500)
        runCurrent()
        assertEquals(1, audio.stopped)
        assertEquals(ListenState.Done("Add two eggs"), s.state.value)
    }

    @Test fun `the projection ending (screen lock, status bar chip) stops like Stop`() = runTest {
        val s = capturing()
        s.projectionEnded()
        runCurrent()
        assertEquals(ListenState.Done("Add two eggs"), s.state.value)
    }

    @Test fun `a second stop or a late projection end changes nothing`() = runTest {
        val s = capturing()
        s.stop()
        s.stop()
        s.projectionEnded()
        runCurrent()
        assertEquals(1, audio.stopped)
        assertEquals(1, transcriber.calls)
    }

    @Test fun `cancel while capturing discards the audio`() = runTest {
        val s = capturing()
        s.cancel()
        runCurrent()
        assertEquals(ListenState.Cancelled, s.state.value)
        assertEquals(1, audio.stopped)
        assertEquals(0, transcriber.calls)
        assertNull(persistence.saved)
        advanceTimeBy(LISTEN_MAX_SECONDS * 2_000L)
        assertEquals(ListenState.Cancelled, s.state.value)
    }

    @Test fun `cancel while transcribing drops the transcript`() = runTest {
        val gate = CompletableDeferred<Unit>()
        transcriber.gate = gate
        val s = capturing()
        s.stop()
        runCurrent()
        s.cancel()
        gate.complete(Unit)
        runCurrent()
        assertEquals(ListenState.Cancelled, s.state.value)
    }

    @Test fun `capture that cannot start fails`() = runTest {
        audio.failToStart = true
        val s = session()
        s.listen()
        s.permissionGranted()
        s.consentGranted()
        assertEquals(ListenState.Failed(ListenFailure.CaptureFailed), s.state.value)
    }

    @Test fun `a transcriber that crashes fails`() = runTest {
        transcriber.fail = true
        val s = capturing()
        s.stop()
        runCurrent()
        assertEquals(ListenState.Failed(ListenFailure.TranscribeFailed), s.state.value)
        assertNull(persistence.saved)
    }

    @Test fun `retry after silence starts over`() = runTest {
        audio.pcm = QUIET
        val s = capturing()
        s.stop()
        runCurrent()
        s.listen()
        assertEquals(ListenState.AwaitingPermission, s.state.value)
    }

    @Test fun `listen is ignored while a run is active`() = runTest {
        val s = capturing()
        s.listen()
        assertTrue(s.state.value is ListenState.Capturing)
    }

    @Test fun `acknowledging a delivered transcript returns to idle and forgets it`() = runTest {
        val s = capturing()
        s.stop()
        runCurrent()
        s.acknowledge()
        assertEquals(ListenState.Idle, s.state.value)
        assertNull(persistence.saved)
    }

    @Test fun `dismiss leaves a finished run but not an active one`() = runTest {
        val s = capturing()
        s.dismiss()
        assertTrue(s.state.value is ListenState.Capturing)
        audio.pcm = QUIET
        s.stop()
        runCurrent()
        s.dismiss()
        assertEquals(ListenState.Idle, s.state.value)
    }

    @Test fun `after process death a capture or transcription in progress reads as interrupted`() = runTest {
        persistence.saved = PersistedListen.Capturing
        val s = session()
        s.restore()
        assertEquals(ListenState.Interrupted, s.state.value)
        assertNull(persistence.saved)

        persistence.saved = PersistedListen.Transcribing
        val t = session()
        t.restore()
        assertEquals(ListenState.Interrupted, t.state.value)
    }

    @Test fun `after process death an undelivered transcript is still there`() = runTest {
        persistence.saved = PersistedListen.Done("two eggs")
        val s = session()
        s.restore()
        assertEquals(ListenState.Done("two eggs"), s.state.value)
        assertEquals(PersistedListen.Done("two eggs"), persistence.saved)
    }

    @Test fun `restore runs once per process`() = runTest {
        val s = session()
        s.restore()
        s.listen()
        persistence.saved = PersistedListen.Capturing
        s.restore()
        assertEquals(ListenState.AwaitingPermission, s.state.value)
    }

    @Test fun `cancel while downloading stops the download task`() = runTest {
        model.installed = false
        val s = session()
        s.listen()
        s.confirmDownload()
        runCurrent()
        model.work.value = DownloadWork.Running(40)
        runCurrent()
        s.cancel()
        model.work.value = DownloadWork.Idle
        runCurrent()
        assertEquals(1, model.cancelledDownloads)
        assertEquals(ListenState.Cancelled, s.state.value)
    }

    @Test fun `cancel outside a download leaves the download task alone`() = runTest {
        val s = capturing()
        s.cancel()
        assertEquals(0, model.cancelledDownloads)
    }

    @Test fun `a transcript that finishes with no screen listening is waiting for the next one`() = runTest {
        val gate = CompletableDeferred<Unit>()
        transcriber.gate = gate
        val s = capturing()
        s.stop()
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals(ListenState.Done("Add two eggs"), s.state.value)
        assertEquals(PersistedListen.Done("Add two eggs"), persistence.saved)
        // A new screen (and a new process) both find it.
        val after = session()
        after.restore()
        assertEquals(ListenState.Done("Add two eggs"), after.state.value)
    }

    @Test fun `listen does not throw away a transcript nobody took yet`() = runTest {
        persistence.saved = PersistedListen.Done("two eggs")
        val s = session()
        s.restore()
        s.listen()
        assertEquals(ListenState.Done("two eggs"), s.state.value)
        assertEquals(PersistedListen.Done("two eggs"), persistence.saved)
    }

    @Test fun `consent arriving when no run waits for it shows as interrupted`() = runTest {
        val s = session()
        s.consentGranted()
        assertEquals(ListenState.Interrupted, s.state.value)
        assertEquals(0, audio.started)
    }

    @Test fun `consent arriving after cancelling keeps the cancelled message`() = runTest {
        val s = session()
        s.listen()
        s.permissionGranted()
        s.cancel()
        s.consentGranted()
        assertEquals(ListenState.Cancelled, s.state.value)
    }

    @Test fun `cancel while the capture is being stopped does not stop it twice or deliver`() = runTest {
        val s = capturing()
        audio.onStop = { s.cancel() }
        s.stop()
        runCurrent()
        assertEquals(ListenState.Cancelled, s.state.value)
        assertEquals(1, audio.stopped)
        assertEquals(0, transcriber.calls)
    }

    @Test fun `the capture is stopped without holding the session lock`() = runTest {
        val s = capturing()
        var stateReadable = false
        audio.onStop = {
            val t = Thread { s.state.value; s.dismiss(); stateReadable = true }
            t.start()
            t.join(2_000)
        }
        s.stop()
        assertTrue(stateReadable)
    }
}
