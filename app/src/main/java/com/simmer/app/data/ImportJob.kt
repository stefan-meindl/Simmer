package com.simmer.app.data

import com.simmer.app.domain.ImportRequest
import com.simmer.app.domain.ImportState
import com.simmer.app.domain.PipelineResult
import com.simmer.app.domain.PipelineStage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Runs the Pipeline once; [ImportJob] gives it the request and listens to its stages. */
fun interface ImportRunner {
    suspend fun run(request: ImportRequest, id: String, onStage: (PipelineStage) -> Unit): PipelineResult
}

/** What survives process death of a conversion. */
sealed interface PersistedImport {
    /** Unfinished. [attempts] counts how often it was restarted after the process died. */
    data class Running(val request: ImportRequest, val attempts: Int) : PersistedImport

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
 *
 * Threading: the public functions are called from the main thread and the run reports from [scope]'s
 * dispatcher (several threads), so every change of state happens under [lock]. A run that was
 * cancelled or replaced (its [generation] is no longer current) cannot change the state any more.
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

    private val lock = Any()
    private var running: Job? = null
    private var generation = 0
    private var restored = false

    /** Starts converting [request]; ignored while a run is working. */
    fun start(request: ImportRequest) = synchronized(lock) {
        if (mutableState.value is ImportState.Working) return@synchronized
        launch(PersistedImport.Running(request, attempts = 0))
    }

    /** Stops generation and goes back to the paste stage. */
    fun cancel() = synchronized(lock) {
        generation++
        running?.cancel()
        running = null
        persistence.save(null)
        mutableState.value = ImportState.Idle
    }

    /** The error stage was dismissed. */
    fun dismissError() {
        mutableState.compareAndSet(ImportState.Failed, ImportState.Idle)
    }

    /** The finished recipe was taken over by the review (saved, edited away or left). */
    fun acknowledge() = synchronized(lock) {
        if (mutableState.value != ImportState.Ready) return@synchronized
        persistence.save(null)
        mutableState.value = ImportState.Idle
    }

    /**
     * Add recipe was left. The draft is dropped, unless a finished recipe was never shown on that screen
     * ([reviewShown] false): it stays for the "Recipe ready to review" notification to open. A running
     * job goes on.
     */
    fun leaveScreen(reviewShown: Boolean) {
        if (mutableState.value == ImportState.Ready && !reviewShown) return
        drafts.set(null)
        acknowledge()
    }

    /** After the process was recreated: resume an unfinished run once, or hand over a finished recipe. Once per process. */
    fun restore() = synchronized(lock) {
        if (restored) return@synchronized
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

    /** Callers hold [lock]. */
    private fun launch(record: PersistedImport.Running) {
        val mine = ++generation
        persistence.save(record)
        mutableState.value = ImportState.Working(PipelineStage.Ingredients)
        host.startService()
        running = scope.launch {
            try {
                val result = runner.run(record.request, newId()) { stage ->
                    ifCurrent(mine) { mutableState.value = ImportState.Working(stage) }
                }
                ifCurrent(mine) {
                    when (result) {
                        is PipelineResult.Done -> {
                            drafts.set(result.recipe)
                            persistence.save(PersistedImport.Ready(result.recipe.toDraftJson()))
                            mutableState.value = ImportState.Ready
                        }
                        is PipelineResult.Failed -> fail()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ifCurrent(mine) { fail() }
            }
        }
    }

    private fun ifCurrent(run: Int, block: () -> Unit) = synchronized(lock) {
        if (run == generation) block()
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
