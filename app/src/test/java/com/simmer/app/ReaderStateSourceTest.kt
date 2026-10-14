package com.simmer.app

import com.simmer.app.domain.DownloadFailure
import com.simmer.app.domain.DownloadProgress
import com.simmer.app.domain.ReaderStatus
import com.simmer.app.domain.RecipeReader
import com.simmer.app.reader.GemmaModelControl
import com.simmer.app.reader.NanoAvailability
import com.simmer.app.reader.ReaderEngine
import com.simmer.app.reader.ReaderSelector
import com.simmer.app.reader.ReaderState
import com.simmer.app.reader.ReaderStateSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Test

private class StubReader(var status: ReaderStatus, var downloads: List<DownloadProgress> = emptyList()) : RecipeReader {
    override suspend fun status() = status
    override fun download(): Flow<DownloadProgress> = flowOf(*downloads.toTypedArray())
    override suspend fun generate(system: String, user: String, maxTokens: Int): Flow<String> = emptyFlow()
}

private class FakeGemma(override val status: MutableStateFlow<ReaderStatus>) : GemmaModelControl {
    val calls = mutableListOf<String>()
    override suspend fun download() { calls += "download" }
    override fun modelPath() = "/model"
    override fun cancel() { calls += "cancel" }
    override fun remove() { calls += "remove" }
}

class ReaderStateSourceTest {
    private val gemmaStatus = MutableStateFlow<ReaderStatus>(ReaderStatus.NotDownloaded)
    private val gemmaControl = FakeGemma(gemmaStatus)
    private val nano = StubReader(ReaderStatus.NotDownloaded)
    private val gemma = StubReader(ReaderStatus.NotDownloaded)
    private var availability = NanoAvailability.Unavailable

    private fun source(scope: CoroutineScope, a: NanoAvailability): ReaderStateSource {
        availability = a
        return ReaderStateSource(ReaderSelector({ availability }, nano, gemma), gemmaControl, scope)
    }

    @Test
    fun gemmaPhonesShowGemmasOwnStatus() = runTest {
        gemmaStatus.value = ReaderStatus.Ready
        assertEquals(ReaderState(ReaderEngine.Gemma, ReaderStatus.Ready), source(this, NanoAvailability.Unavailable).state.first())
    }

    @Test
    fun nanoPhonesShowNanosStatus() = runTest {
        nano.status = ReaderStatus.Ready
        assertEquals(ReaderState(ReaderEngine.Nano, ReaderStatus.Ready), source(this, NanoAvailability.Available).state.first())
    }

    @Test
    fun nanoStatusIsPolledAgainOnEachCollection() = runTest {
        val source = source(this, NanoAvailability.Downloadable)
        assertEquals(ReaderStatus.NotDownloaded, source.state.first().status)
        nano.status = ReaderStatus.Ready
        assertEquals(ReaderStatus.Ready, source.state.first().status)
    }

    /** A Nano reader whose download reports Running and then waits on [gate] before completing. */
    private class GatedNano(val gate: CompletableDeferred<Unit>) : RecipeReader {
        var status: ReaderStatus = ReaderStatus.NotDownloaded
        override suspend fun status() = status
        override fun download(): Flow<DownloadProgress> = flow {
            emit(DownloadProgress.Running(null, false))
            gate.await()
            status = ReaderStatus.Ready
            emit(DownloadProgress.Complete)
        }
        override suspend fun generate(system: String, user: String, maxTokens: Int): Flow<String> = emptyFlow()
    }

    @Test
    fun nanoDownloadShowsIndeterminateProgressThenTheTruth() = runTest {
        val gated = GatedNano(CompletableDeferred())
        val source = ReaderStateSource(ReaderSelector({ NanoAvailability.Downloadable }, gated, gemma), gemmaControl, this)
        val seen = mutableListOf<ReaderStatus>()
        val collector = launch { source.state.collect { seen += it.status } }
        advanceUntilIdle()
        source.download()
        advanceUntilIdle()
        assertEquals(ReaderStatus.Downloading(null, false), seen.last())
        gated.gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(ReaderStatus.Ready, seen.last())
        collector.cancel()
    }

    @Test
    fun nanoDownloadFailureIsShownUntilTheScreenReloads() = runTest {
        nano.downloads = listOf(DownloadProgress.Failed(DownloadFailure.Unknown))
        val source = source(this, NanoAvailability.Downloadable)
        val seen = mutableListOf<ReaderStatus>()
        val collector = launch { source.state.collect { seen += it.status } }
        advanceUntilIdle()
        source.download()
        advanceUntilIdle()
        assertEquals(ReaderStatus.Failed(DownloadFailure.Unknown), seen.last())
        collector.cancel()
        // A new collection starts clean and reports the polled status.
        assertEquals(ReaderStatus.NotDownloaded, source.state.first().status)
    }

    @Test
    fun aRunningNanoDownloadKeepsTheNanoEngineWhileMlKitSaysDownloading() = runTest {
        val gated = GatedNano(CompletableDeferred())
        availability = NanoAvailability.Downloadable
        val source = ReaderStateSource(ReaderSelector({ availability }, gated, gemma), gemmaControl, this)
        source.state.first()
        source.download()
        advanceUntilIdle()
        availability = NanoAvailability.Downloading
        assertEquals(ReaderState(ReaderEngine.Nano, ReaderStatus.Downloading(null, false)), source.state.first())
        gated.gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun gemmaActionsGoToTheGemmaController() = runTest {
        val source = source(this, NanoAvailability.Unavailable)
        source.state.first()
        source.download()
        source.cancel()
        source.remove()
        assertEquals(listOf("download", "cancel", "remove"), gemmaControl.calls)
    }

    @Test
    fun nanoHasNoCancelOrRemoveAndDoesNotTouchGemma() = runTest {
        val source = source(this, NanoAvailability.Available)
        source.state.first()
        source.cancel()
        source.remove()
        source.download()
        advanceUntilIdle()
        assertEquals(emptyList<String>(), gemmaControl.calls)
    }
}
