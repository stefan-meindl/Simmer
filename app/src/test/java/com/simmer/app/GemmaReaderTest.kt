package com.simmer.app

import com.simmer.app.domain.DownloadFailure
import com.simmer.app.domain.DownloadProgress
import com.simmer.app.domain.ReaderStatus
import com.simmer.app.reader.GemmaReader
import com.simmer.app.reader.InferenceBackend
import com.simmer.app.reader.LlmEngine
import com.simmer.app.reader.LlmEngineFactory
import com.simmer.app.reader.ReaderModelSource
import com.simmer.app.reader.ThermalMonitor
import com.simmer.app.reader.ThermalPauseTimeout
import com.simmer.app.reader.isSevereThermalStatus
import com.simmer.app.reader.shouldUnloadOnTrim
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors

private class FakeSource(initial: ReaderStatus = ReaderStatus.Ready) : ReaderModelSource {
    override val status = MutableStateFlow(initial)
    var downloads = 0
    override suspend fun download() {
        downloads++
    }
    override fun modelPath() = "/models/gemma.litertlm"
}

private class FakeEngine(val backend: InferenceBackend, val log: MutableList<String>) : LlmEngine {
    var closed = false
    val temperatures = mutableListOf<Double>()
    val maxTokens = mutableListOf<Int>()
    val prompts = mutableListOf<Pair<String, String>>()
    var gate: CompletableDeferred<Unit>? = null
    override fun generate(system: String, user: String, maxTokens: Int, temperature: Double): Flow<String> {
        log += "generate"
        prompts += system to user
        this.maxTokens += maxTokens
        temperatures += temperature
        gate?.let { g -> return flow { emit("Hel"); g.await(); emit("lo") } }
        return flowOf("Hel", "lo")
    }
    override fun close() {
        closed = true
        log += "close"
    }
}

private class FakeFactory(var failGpu: Boolean = false, var failCpu: Boolean = false, var gpuError: Throwable? = null, var gate: CompletableDeferred<Unit>? = null) : LlmEngineFactory {
    val engines = mutableListOf<FakeEngine>()
    val attempts = mutableListOf<InferenceBackend>()
    val threads = mutableListOf<String>()
    val log = mutableListOf<String>()
    override fun create(modelPath: String, backend: InferenceBackend): LlmEngine {
        attempts += backend
        threads += Thread.currentThread().name
        if (backend == InferenceBackend.Gpu) gpuError?.let { throw it }
        if (backend == InferenceBackend.Gpu && failGpu) error("no GPU")
        if (backend == InferenceBackend.Cpu && failCpu) error("no CPU")
        return FakeEngine(backend, log).also { it.gate = gate; engines += it }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class GemmaReaderTest {
    private val source = FakeSource()
    private val factory = FakeFactory()
    private var severe = { false }
    private val thermal = ThermalMonitor { severe() }

    private fun TestScope.reader() = GemmaReader(
        source = source,
        factory = factory,
        thermal = thermal,
        scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
        engineDispatcher = StandardTestDispatcher(testScheduler),
        thermalMaxWaitMs = 60_000,
    )

    @Test
    fun generate_streamsTokensAtTemperatureTwoTenths() = runTest {
        val reader = reader()
        val tokens = reader.generate("sys", "usr", 512).toList()
        assertEquals(listOf("Hel", "lo"), tokens)
        val engine = factory.engines.single()
        assertEquals(listOf(0.2), engine.temperatures)
        assertEquals(listOf(512), engine.maxTokens)
        assertEquals(listOf("sys" to "usr"), engine.prompts)
    }

    @Test
    fun gpuIsTriedFirst_andCpuTakesOverWhenGpuFails() = runTest {
        val reader = reader()
        reader.generate("s", "u", 8).toList()
        assertEquals(listOf(InferenceBackend.Gpu), factory.attempts)

        factory.failGpu = true
        reader.unload()
        runCurrent()
        reader.generate("s", "u", 8).toList()
        assertEquals(listOf(InferenceBackend.Gpu, InferenceBackend.Gpu, InferenceBackend.Cpu), factory.attempts)
        assertEquals(InferenceBackend.Cpu, factory.engines.last().backend)
    }

    @Test
    fun bothBackendsFailing_surfacesTheError() = runTest {
        factory.failGpu = true
        factory.failCpu = true
        val reader = reader()
        val failure = runCatching { reader.generate("s", "u", 8).toList() }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals(listOf(InferenceBackend.Gpu, InferenceBackend.Cpu), factory.attempts)
    }

    @Test
    fun singleEngineInstanceIsReusedAcrossCalls() = runTest {
        val reader = reader()
        reader.generate("s", "u", 8).toList()
        reader.generate("s", "u", 8).toList()
        assertEquals(1, factory.engines.size)
        assertEquals(listOf(0.2, 0.2), factory.engines.single().temperatures)
    }

    @Test
    fun concurrentCallsShareOneEngine() = runTest {
        val reader = reader()
        val a = async { reader.generate("s", "a", 8).toList() }
        val b = async { reader.generate("s", "b", 8).toList() }
        a.await()
        b.await()
        assertEquals(1, factory.engines.size)
    }

    @Test
    fun engineIsCreatedOffTheCallersThread() = runTest {
        val executor = Executors.newSingleThreadExecutor { Thread(it, "engine-init-thread") }
        try {
            val reader = GemmaReader(
                source = source,
                factory = factory,
                thermal = thermal,
                scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
                engineDispatcher = executor.asCoroutineDispatcher(),
            )
            reader.generate("s", "u", 8).toList()
            assertEquals(listOf("engine-init-thread"), factory.threads)
            assertNotEquals(Thread.currentThread().name, factory.threads.single())
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun engineIsUnloadedAfterSixtySecondsIdle() = runTest {
        val reader = reader()
        reader.generate("s", "u", 8).toList()
        advanceTimeBy(59_000)
        runCurrent()
        assertFalse(factory.engines.single().closed)
        advanceTimeBy(1_001)
        runCurrent()
        assertTrue(factory.engines.single().closed)

        reader.generate("s", "u", 8).toList()
        assertEquals(2, factory.engines.size)
    }

    @Test
    fun aNewCallRestartsTheIdleClock() = runTest {
        val reader = reader()
        reader.generate("s", "u", 8).toList()
        advanceTimeBy(40_000)
        reader.generate("s", "u", 8).toList()
        advanceTimeBy(40_000)
        runCurrent()
        assertFalse(factory.engines.single().closed)
        advanceTimeBy(21_000)
        runCurrent()
        assertTrue(factory.engines.single().closed)
    }

    @Test
    fun trimMemoryUnloadsImmediately() = runTest {
        val reader = reader()
        reader.generate("s", "u", 8).toList()
        reader.unload()
        runCurrent()
        assertTrue(factory.engines.single().closed)
        assertEquals(0L, currentTime)
    }

    @Test
    fun unloadWithoutAnEngineIsHarmless() = runTest {
        val reader = reader()
        reader.unload()
        runCurrent()
        assertTrue(factory.engines.isEmpty())
    }

    @Test
    fun generationWaitsWhileThePhoneIsHot() = runTest {
        var checks = 0
        severe = { ++checks <= 2 }
        val reader = reader()
        val tokens = async { reader.generate("s", "u", 8).toList() }
        runCurrent()
        assertTrue(factory.engines.isEmpty())
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(factory.engines.isEmpty())
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf("Hel", "lo"), tokens.await())
        assertEquals(10_000L, currentTime)
    }

    @Test
    fun thermalIsCheckedBeforeEveryCall() = runTest {
        var checks = 0
        severe = { checks++; false }
        val reader = reader()
        reader.generate("s", "u", 8).toList()
        reader.generate("s", "u", 8).toList()
        assertEquals(2, checks)
    }

    @Test
    fun statusComesFromTheModelSource() = runTest {
        source.status.value = ReaderStatus.NotDownloaded
        assertEquals(ReaderStatus.NotDownloaded, reader().status())
    }

    @Test
    fun download_startsTheTaskAndReportsProgressUntilComplete() = runTest {
        source.status.value = ReaderStatus.Downloading(10, waiting = true)
        val reader = reader()
        val seen = async { reader.download().toList() }
        runCurrent()
        assertEquals(1, source.downloads)
        source.status.value = ReaderStatus.Downloading(55, waiting = false)
        runCurrent()
        source.status.value = ReaderStatus.Ready
        assertEquals(
            listOf(
                DownloadProgress.Running(10, waiting = true),
                DownloadProgress.Running(55, waiting = false),
                DownloadProgress.Complete,
            ),
            seen.await(),
        )
    }

    @Test
    fun download_endsWithFailureReason() = runTest {
        source.status.value = ReaderStatus.Downloading(0, waiting = false)
        val reader = reader()
        val seen = async { reader.download().toList() }
        runCurrent()
        source.status.value = ReaderStatus.Failed(DownloadFailure.ServerError)
        assertEquals(
            listOf(DownloadProgress.Running(0, false), DownloadProgress.Failed(DownloadFailure.ServerError)),
            seen.await(),
        )
    }

    @Test
    fun outOfMemoryDuringGpuLoadIsNotSwallowed() = runTest {
        factory.gpuError = OutOfMemoryError("gpu")
        val failure = runCatching { reader().generate("s", "u", 8).toList() }.exceptionOrNull()
        assertTrue(failure is OutOfMemoryError)
        assertEquals(listOf(InferenceBackend.Gpu), factory.attempts)
    }

    @Test
    fun unloadDuringGenerationWaitsForItAndDoesNotBlockTheCaller() = runTest {
        val gate = CompletableDeferred<Unit>()
        factory.gate = gate
        val reader = reader()
        val tokens = async { reader.generate("s", "u", 8).toList() }
        runCurrent()
        reader.unload()
        runCurrent()
        assertFalse(factory.engines.single().closed)
        gate.complete(Unit)
        assertEquals(listOf("Hel", "lo"), tokens.await())
        runCurrent()
        assertTrue(factory.engines.single().closed)
    }

    @Test
    fun idleTimerDoesNotCloseAnEngineThatIsGenerating() = runTest {
        val gate = CompletableDeferred<Unit>()
        factory.gate = gate
        val reader = reader()
        val tokens = async { reader.generate("s", "u", 8).toList() }
        runCurrent()
        advanceTimeBy(120_000)
        runCurrent()
        assertFalse(factory.engines.single().closed)
        gate.complete(Unit)
        tokens.await()
    }

    @Test
    fun unloadIsNotBlockedWhileWaitingForTheThermalPause() = runTest {
        val reader = reader()
        reader.generate("s", "u", 8).toList()
        severe = { true }
        val waiting = async { runCatching { reader.generate("s", "u", 8).toList() } }
        runCurrent()
        reader.unload()
        runCurrent()
        assertTrue(factory.engines.single().closed)
        waiting.cancel()
    }

    @Test
    fun thermalPauseGivesUpAfterTheLimit() = runTest {
        severe = { true }
        val reader = reader()
        val failure = async { runCatching { reader.generate("s", "u", 8).toList() }.exceptionOrNull() }
        advanceTimeBy(60_001)
        runCurrent()
        assertTrue(failure.await() is ThermalPauseTimeout)
        assertTrue(factory.engines.isEmpty())
    }

    @Test
    fun aSlowCollectorOnlyBlocksOtherGenerateCalls() = runTest {
        val gate = CompletableDeferred<Unit>()
        factory.gate = gate
        val reader = reader()
        val first = async { reader.generate("s", "a", 8).toList() }
        runCurrent()
        val second = async { reader.generate("s", "b", 8).toList() }
        runCurrent()
        assertEquals(1, factory.engines.single().prompts.size)
        gate.complete(Unit)
        first.await()
        second.await()
        assertEquals(2, factory.engines.single().prompts.size)
        assertEquals(1, factory.engines.size)
    }

    @Test
    fun download_ignoresAStaleFailureFromAPreviousRun() = runTest {
        source.status.value = ReaderStatus.Failed(DownloadFailure.ServerError)
        val reader = reader()
        val seen = async { reader.download().toList() }
        runCurrent()
        source.status.value = ReaderStatus.Downloading(1, waiting = false)
        runCurrent()
        source.status.value = ReaderStatus.Ready
        assertEquals(listOf(DownloadProgress.Running(1, false), DownloadProgress.Complete), seen.await())
    }

    @Test
    fun download_waitsWhileNotDownloadedAndSkipsIt() = runTest {
        source.status.value = ReaderStatus.NotDownloaded
        val reader = reader()
        val seen = async { reader.download().toList() }
        runCurrent()
        source.status.value = ReaderStatus.Downloading(3, waiting = true)
        runCurrent()
        source.status.value = ReaderStatus.Ready
        assertEquals(listOf(DownloadProgress.Running(3, true), DownloadProgress.Complete), seen.await())
    }

    @Test
    fun trimLevels_unloadOnlyOnRealMemoryPressure() {
        assertFalse(shouldUnloadOnTrim(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE))
        assertTrue(shouldUnloadOnTrim(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW))
        assertTrue(shouldUnloadOnTrim(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL))
        assertFalse(shouldUnloadOnTrim(android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN))
        assertTrue(shouldUnloadOnTrim(android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND))
        assertTrue(shouldUnloadOnTrim(android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE))
        assertTrue(shouldUnloadOnTrim(android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE))
    }

    @Test
    fun thermalStatus_severeAndWorseCount() {
        assertFalse(isSevereThermalStatus(null))
        assertFalse(isSevereThermalStatus(android.os.PowerManager.THERMAL_STATUS_MODERATE))
        assertTrue(isSevereThermalStatus(android.os.PowerManager.THERMAL_STATUS_SEVERE))
        assertTrue(isSevereThermalStatus(android.os.PowerManager.THERMAL_STATUS_SHUTDOWN))
    }
}
