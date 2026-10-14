package com.simmer.app

import com.simmer.app.domain.DownloadFailure
import com.simmer.app.domain.DownloadProgress
import com.simmer.app.domain.ReaderStatus
import com.simmer.app.domain.RecipeReader
import com.simmer.app.reader.NanoAvailability
import com.simmer.app.reader.ReaderEngine
import com.simmer.app.reader.ReaderSelector
import com.simmer.app.reader.ReaderState
import com.simmer.app.reader.ReaderStateSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private class StubReader(var status: ReaderStatus, var downloads: List<DownloadProgress> = emptyList()) : RecipeReader {
    override suspend fun status() = status
    override fun download(): Flow<DownloadProgress> = flowOf(*downloads.toTypedArray())
    override suspend fun generate(system: String, user: String, maxTokens: Int): Flow<String> = emptyFlow()
}

class ReaderStateSourceTest {
    private val gemmaStatus = MutableStateFlow<ReaderStatus>(ReaderStatus.NotDownloaded)
    private val nano = StubReader(ReaderStatus.NotDownloaded)
    private val gemma = StubReader(ReaderStatus.NotDownloaded)

    private fun source(availability: NanoAvailability) =
        ReaderStateSource(ReaderSelector({ availability }, nano, gemma), gemmaStatus, nano)

    @Test
    fun gemmaPhonesShowGemmasOwnStatus() = runTest {
        gemmaStatus.value = ReaderStatus.Ready
        assertEquals(ReaderState(ReaderEngine.Gemma, ReaderStatus.Ready), source(NanoAvailability.Unavailable).state.first())
    }

    @Test
    fun nanoPhonesShowNanosStatus() = runTest {
        nano.status = ReaderStatus.Ready
        assertEquals(ReaderState(ReaderEngine.Nano, ReaderStatus.Ready), source(NanoAvailability.Available).state.first())
    }

    @Test
    fun nanoDownloadMovesToReady() = runTest {
        nano.downloads = listOf(DownloadProgress.Running(0, false), DownloadProgress.Complete)
        val source = source(NanoAvailability.Downloadable)
        assertEquals(ReaderStatus.NotDownloaded, source.state.first().status)
        source.downloadNano()
        assertEquals(ReaderStatus.Ready, source.state.first().status)
    }

    @Test
    fun nanoDownloadFailureIsShown() = runTest {
        nano.downloads = listOf(DownloadProgress.Failed(DownloadFailure.Unknown))
        val source = source(NanoAvailability.Downloadable)
        source.downloadNano()
        assertEquals(ReaderStatus.Failed(DownloadFailure.Unknown), source.state.first().status)
    }
}
