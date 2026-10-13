package com.simmer.app

import com.simmer.app.domain.DownloadProgress
import com.simmer.app.domain.ReaderStatus
import com.simmer.app.domain.RecipeReader
import com.simmer.app.reader.NanoAvailability
import com.simmer.app.reader.ReaderSelector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertSame
import org.junit.Test

private class NamedReader : RecipeReader {
    override suspend fun status(): ReaderStatus = ReaderStatus.Ready
    override fun download(): Flow<DownloadProgress> = emptyFlow()
    override suspend fun generate(system: String, user: String, maxTokens: Int): Flow<String> = emptyFlow()
}

class ReaderSelectorTest {
    private val nano = NamedReader()
    private val gemma = NamedReader()

    private fun select(availability: NanoAvailability, nanoReader: RecipeReader? = nano) =
        ReaderSelector(availability = { availability }, nano = nanoReader, gemma = gemma)

    @Test
    fun nanoWhenAvailable() = runTest {
        assertSame(nano, select(NanoAvailability.Available).current())
    }

    @Test
    fun nanoWhenDownloadable() = runTest {
        assertSame(nano, select(NanoAvailability.Downloadable).current())
    }

    @Test
    fun gemmaWhenNanoUnavailable() = runTest {
        assertSame(gemma, select(NanoAvailability.Unavailable).current())
    }

    @Test
    fun gemmaWhenNanoIsNotImplementedYet() = runTest {
        assertSame(gemma, select(NanoAvailability.Available, nanoReader = null).current())
    }

    @Test
    fun gemmaWhenTheAvailabilityCheckFails() = runTest {
        val selector = ReaderSelector(availability = { error("ML Kit missing") }, nano = nano, gemma = gemma)
        assertSame(gemma, selector.current())
    }
}
