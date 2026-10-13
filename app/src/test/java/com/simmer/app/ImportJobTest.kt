package com.simmer.app

import com.simmer.app.data.ImportHost
import com.simmer.app.data.ImportJob
import com.simmer.app.data.ImportPersistence
import com.simmer.app.data.ImportRunner
import com.simmer.app.data.PersistedImport
import com.simmer.app.data.RecipeDraftStore
import com.simmer.app.domain.ImportState
import com.simmer.app.domain.PipelineResult
import com.simmer.app.domain.PipelineStage
import com.simmer.app.domain.model.Recipe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val RECIPE = Recipe(id = "u1", title = "Soup", category = "Soups", time = 20, difficulty = "Easy", base = 4, mine = true)

private class FakePersistence(var saved: PersistedImport? = null) : ImportPersistence {
    override fun load() = saved
    override fun save(value: PersistedImport?) { saved = value }
}

private class FakeHost : ImportHost {
    var starts = 0
    override fun startService() { starts++ }
}

/** Seam: [ImportJob], the one running conversion that outlives the Add recipe screen. */
@OptIn(ExperimentalCoroutinesApi::class)
class ImportJobTest {
    private val drafts = RecipeDraftStore()
    private val persistence = FakePersistence()
    private val host = FakeHost()

    private fun TestScope.job(runner: ImportRunner) =
        ImportJob(runner, drafts, persistence, host, scope = this, newId = { "u1" })

    @Test fun `start shows the first stage, starts the service and remembers the job`() = runTest {
        val gate = CompletableDeferred<PipelineResult>()
        val job = job { _, _, _, _, _ -> gate.await() }

        job.start("some recipe text", "rule", "en")

        assertEquals(ImportState.Working(PipelineStage.Ingredients), job.state.value)
        assertEquals(1, host.starts)
        assertEquals(PersistedImport.Running("some recipe text", "rule", "en", attempts = 0), persistence.saved)
        gate.cancel()
    }

    @Test fun `the state follows the passes and a finished run puts the recipe in the draft store`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val job = job { _, _, _, _, onStage ->
            onStage(PipelineStage.Ingredients)
            onStage(PipelineStage.Phases)
            gate.await()
            PipelineResult.Done(RECIPE, repairs = 0)
        }

        job.start("text", "", "en")
        runCurrent()
        assertEquals(ImportState.Working(PipelineStage.Phases), job.state.value)

        gate.complete(Unit)
        runCurrent()

        assertEquals(ImportState.Ready, job.state.value)
        assertEquals("u1", drafts.current.value!!.id)
        assertTrue(persistence.saved is PersistedImport.Ready)
    }

    @Test fun `a failed result shows the error stage and forgets the job`() = runTest {
        val job = job { _, _, _, _, _ -> PipelineResult.Failed(PipelineStage.Phases) }

        job.start("text", "", "en")
        runCurrent()

        assertEquals(ImportState.Failed, job.state.value)
        assertNull(persistence.saved)
        assertNull(drafts.current.value)
    }

    @Test fun `an exception from the reader shows the error stage`() = runTest {
        val job = job { _, _, _, _, _ -> error("model not downloaded") }

        job.start("text", "", "en")
        runCurrent()

        assertEquals(ImportState.Failed, job.state.value)
    }

    @Test fun `cancel stops the run and goes back to the paste stage`() = runTest {
        var finished = false
        val job = job { _, _, _, _, _ ->
            try {
                CompletableDeferred<Unit>().await()
                PipelineResult.Done(RECIPE, 0)
            } finally {
                finished = true
            }
        }
        job.start("text", "", "en")
        runCurrent()

        job.cancel()
        runCurrent()

        assertTrue(finished)
        assertEquals(ImportState.Idle, job.state.value)
        assertNull(persistence.saved)
        assertNull(drafts.current.value)
    }

    @Test fun `starting while a run is working is ignored`() = runTest {
        val gate = CompletableDeferred<PipelineResult>()
        var runs = 0
        val job = job { _, _, _, _, _ -> runs++; gate.await() }

        job.start("one", "", "en")
        job.start("two", "", "en")
        runCurrent()

        assertEquals(1, runs)
        assertEquals(1, host.starts)
        gate.cancel()
    }

    @Test fun `dismissing the error goes back to the paste stage`() = runTest {
        val job = job { _, _, _, _, _ -> PipelineResult.Failed(PipelineStage.Ingredients) }
        job.start("text", "", "en")
        runCurrent()

        job.dismissError()

        assertEquals(ImportState.Idle, job.state.value)
    }

    @Test fun `after process death an unfinished job runs again from the start`() = runTest {
        persistence.saved = PersistedImport.Running("text", "rule", "de", attempts = 0)
        var got: List<String>? = null
        val job = job { text, rule, lang, _, _ -> got = listOf(text, rule, lang); PipelineResult.Done(RECIPE, 0) }

        job.restore()
        runCurrent()

        assertEquals(listOf("text", "rule", "de"), got)
        assertEquals(ImportState.Ready, job.state.value)
        assertEquals(1, host.starts)
    }

    @Test fun `a job that already restarted once and died again shows the error stage`() = runTest {
        persistence.saved = PersistedImport.Running("text", "", "en", attempts = 1)
        val job = job { _, _, _, _, _ -> error("must not run") }

        job.restore()
        runCurrent()

        assertEquals(ImportState.Failed, job.state.value)
        assertNull(persistence.saved)
    }

    @Test fun `a restart counts the attempt so a crash loop ends`() = runTest {
        persistence.saved = PersistedImport.Running("text", "", "en", attempts = 0)
        val gate = CompletableDeferred<PipelineResult>()
        val job = job { _, _, _, _, _ -> gate.await() }

        job.restore()

        assertEquals(PersistedImport.Running("text", "", "en", attempts = 1), persistence.saved)
        gate.cancel()
    }

    @Test fun `after process death a finished recipe is waiting in the draft store`() = runTest {
        job { _, _, _, _, _ -> PipelineResult.Done(RECIPE, 0) }.start("text", "", "en")
        runCurrent()
        val saved = persistence.saved
        val fresh = RecipeDraftStore()

        ImportJob({ _, _, _, _, _ -> error("no run") }, fresh, FakePersistence(saved), FakeHost(), scope = this, newId = { "x" }).restore()

        assertEquals("u1", fresh.current.value!!.id)
    }

    @Test fun `acknowledging a finished recipe clears the job`() = runTest {
        val job = job { _, _, _, _, _ -> PipelineResult.Done(RECIPE, 0) }
        job.start("text", "", "en")
        runCurrent()

        job.acknowledge()

        assertEquals(ImportState.Idle, job.state.value)
        assertNull(persistence.saved)
    }

    @Test fun `restore only runs once per process`() = runTest {
        persistence.saved = PersistedImport.Running("text", "", "en", attempts = 0)
        val gate = CompletableDeferred<PipelineResult>()
        var runs = 0
        val job = job { _, _, _, _, _ -> runs++; gate.await() }

        job.restore()
        job.restore()
        runCurrent()

        assertEquals(1, runs)
        gate.cancel()
    }
}
