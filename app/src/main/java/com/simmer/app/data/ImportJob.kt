package com.simmer.app.data

import com.simmer.app.domain.ImportState
import com.simmer.app.domain.PipelineResult
import com.simmer.app.domain.PipelineStage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Runs the Pipeline once; [ImportJob] gives it the text and listens to its stages. */
fun interface ImportRunner {
    suspend fun run(text: String, kitchenRule: String, lang: String, id: String, onStage: (PipelineStage) -> Unit): PipelineResult
}

/** What survives process death of a conversion. */
sealed interface PersistedImport {
    /** Unfinished. [attempts] counts how often it was restarted after the process died. */
    data class Running(val text: String, val kitchenRule: String, val lang: String, val attempts: Int) : PersistedImport

    /** Finished, not yet reviewed: the recipe as draft JSON. */
    data class Ready(val recipeJson: String) : PersistedImport
}

interface ImportPersistence {
    fun load(): PersistedImport?
    fun save(value: PersistedImport?)
}

/** Starts the foreground service that keeps the process alive while the job runs. */
fun interface ImportHost {
    fun startService()
}

/**
 * The one Pipeline run, owned by the app and not the screen: it keeps going when the Add recipe
 * screen is left, and is remembered so a process death resumes it once ([restore]) before showing
 * the error stage. A finished recipe goes into the [RecipeDraftStore].
 */
class ImportJob(
    private val runner: ImportRunner,
    private val drafts: RecipeDraftStore,
    private val persistence: ImportPersistence,
    private val host: ImportHost,
    private val scope: CoroutineScope,
    private val newId: () -> String,
) {
    private val mutableState = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = mutableState

    private var running: Job? = null
    private var restored = false

    /** Starts converting [text]; ignored while a run is working. */
    fun start(text: String, kitchenRule: String, lang: String) {
        if (mutableState.value is ImportState.Working) return
        launch(PersistedImport.Running(text, kitchenRule, lang, attempts = 0))
    }

    /** Stops generation and goes back to the paste stage. */
    fun cancel() {
        running?.cancel()
        running = null
        persistence.save(null)
        mutableState.value = ImportState.Idle
    }

    /** The error stage was dismissed. */
    fun dismissError() {
        if (mutableState.value == ImportState.Failed) mutableState.value = ImportState.Idle
    }

    /** The finished recipe was taken over by the review (saved, edited away or left). */
    fun acknowledge() {
        if (mutableState.value != ImportState.Ready) return
        persistence.save(null)
        mutableState.value = ImportState.Idle
    }

    /** After the process was recreated: resume an unfinished run once, or hand over a finished recipe. Once per process. */
    fun restore() {
        if (restored) return
        restored = true
        when (val saved = persistence.load()) {
            null -> Unit
            is PersistedImport.Ready -> {
                drafts.set(draftFromJson(saved.recipeJson))
                mutableState.value = ImportState.Ready
            }
            is PersistedImport.Running ->
                if (saved.attempts < MAX_RESUMES) launch(saved.copy(attempts = saved.attempts + 1))
                else {
                    persistence.save(null)
                    mutableState.value = ImportState.Failed
                }
        }
    }

    private fun launch(job: PersistedImport.Running) {
        persistence.save(job)
        mutableState.value = ImportState.Working(PipelineStage.Ingredients)
        host.startService()
        running = scope.launch {
            try {
                val result = runner.run(job.text, job.kitchenRule, job.lang, newId()) { stage ->
                    mutableState.value = ImportState.Working(stage)
                }
                when (result) {
                    is PipelineResult.Done -> {
                        drafts.set(result.recipe)
                        persistence.save(PersistedImport.Ready(result.recipe.toDraftJson()))
                        mutableState.value = ImportState.Ready
                    }
                    is PipelineResult.Failed -> fail()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail()
            }
        }
    }

    private fun fail() {
        persistence.save(null)
        mutableState.value = ImportState.Failed
    }

    companion object {
        /** A job that was running when the process died is restarted this many times. */
        const val MAX_RESUMES = 1
    }
}
