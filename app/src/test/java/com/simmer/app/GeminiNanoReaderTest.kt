package com.simmer.app

import com.simmer.app.domain.DownloadFailure
import com.simmer.app.domain.DownloadProgress
import com.simmer.app.domain.ReaderStatus
import com.simmer.app.reader.GeminiNanoReader
import com.simmer.app.reader.NanoAvailability
import com.simmer.app.reader.NanoBusyException
import com.simmer.app.reader.NanoClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [outcomes] has one entry per generate call: a list of tokens to stream, a Throwable to fail with, or tokens then a Throwable (Pair). */
private class FakeNano(
    var availability: NanoAvailability = NanoAvailability.Available,
    val outcomes: MutableList<Any> = mutableListOf(),
    val downloadEvents: List<DownloadProgress> = emptyList(),
) : NanoClient {
    val calls = mutableListOf<List<Any>>()

    override suspend fun availability() = availability

    override fun download(): Flow<DownloadProgress> = flowOf(*downloadEvents.toTypedArray())

    override fun generate(system: String, user: String, maxTokens: Int, temperature: Double): Flow<String> = flow {
        calls += listOf(system, user, maxTokens, temperature)
        when (val outcome = outcomes.removeAt(0)) {
            is Throwable -> throw outcome
            is Pair<*, *> -> {
                (outcome.first as List<*>).forEach { emit(it as String) }
                throw outcome.second as Throwable
            }
            is List<*> -> outcome.forEach { emit(it as String) }
            else -> error("bad outcome")
        }
    }
}

class GeminiNanoReaderTest {
    private val waits = mutableListOf<Long>()
    private fun reader(nano: FakeNano) = GeminiNanoReader(nano, wait = { waits += it }, retryDelayMs = 1_500)

    /** Collects, returning the tokens received and the failure (if any). */
    private suspend fun collect(reader: GeminiNanoReader): Pair<List<String>, Throwable?> {
        val tokens = mutableListOf<String>()
        return try {
            reader.generate("s", "u", 10).collect { tokens += it }
            tokens to null
        } catch (e: Exception) {
            tokens to e
        }
    }

    @Test
    fun streamsTokensWithGemmasTemperature() = runTest {
        val nano = FakeNano(outcomes = mutableListOf(listOf("a", "b")))
        assertEquals(listOf("a", "b"), reader(nano).generate("sys", "usr", 100).toList())
        assertEquals(listOf<Any>("sys", "usr", 100, 0.2), nano.calls.single())
    }

    @Test
    fun busyWaitsThenRetriesOnce() = runTest {
        val nano = FakeNano(outcomes = mutableListOf(NanoBusyException(null), listOf("ok")))
        assertEquals(listOf("ok") to null, collect(reader(nano)))
        assertEquals(listOf(1_500L), waits)
    }

    @Test
    fun busyWaitUsesTheHintFromTheErrorWhenGiven() = runTest {
        val nano = FakeNano(outcomes = mutableListOf(NanoBusyException(4_000), listOf("ok")))
        collect(reader(nano))
        assertEquals(listOf(4_000L), waits)
    }

    @Test
    fun busyTwiceSurfacesTheError() = runTest {
        val nano = FakeNano(outcomes = mutableListOf(NanoBusyException(null), NanoBusyException(null)))
        val (_, failure) = collect(reader(nano))
        assertTrue(failure is NanoBusyException)
        assertEquals(2, nano.calls.size)
        assertEquals(1, waits.size)
    }

    @Test
    fun otherErrorsAreNotRetried() = runTest {
        val nano = FakeNano(outcomes = mutableListOf(IllegalStateException("boom"), listOf("never")))
        val (_, failure) = collect(reader(nano))
        assertTrue(failure is IllegalStateException)
        assertEquals(1, nano.calls.size)
        assertEquals(emptyList<Long>(), waits)
    }

    @Test
    fun busyAfterTokensWereEmittedIsNotRetried() = runTest {
        // A retry would repeat tokens the caller already received.
        val nano = FakeNano(outcomes = mutableListOf(listOf("a") to NanoBusyException(null), listOf("never")))
        val (tokens, failure) = collect(reader(nano))
        assertEquals(listOf("a"), tokens)
        assertTrue(failure is NanoBusyException)
        assertEquals(emptyList<Long>(), waits)
    }

    @Test
    fun statusFollowsAvailability() = runTest {
        suspend fun statusOf(a: NanoAvailability) = reader(FakeNano(availability = a)).status()
        assertEquals(ReaderStatus.Ready, statusOf(NanoAvailability.Available))
        assertEquals(ReaderStatus.NotDownloaded, statusOf(NanoAvailability.Downloadable))
        assertEquals(ReaderStatus.Downloading(0, false), statusOf(NanoAvailability.Downloading))
        assertEquals(ReaderStatus.NotSupported, statusOf(NanoAvailability.Unavailable))
    }

    @Test
    fun downloadPassesProgressThrough() = runTest {
        val events = listOf(DownloadProgress.Running(0, false), DownloadProgress.Complete)
        assertEquals(events, reader(FakeNano(downloadEvents = events)).download().toList())
        val failed = listOf(DownloadProgress.Failed(DownloadFailure.Unknown))
        assertEquals(failed, reader(FakeNano(downloadEvents = failed)).download().toList())
    }
}
