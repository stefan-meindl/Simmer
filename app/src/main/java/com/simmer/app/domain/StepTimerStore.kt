package com.simmer.app.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Key of a StepTimer: `recipeId/stepId`. */
fun timerKey(recipeId: String, stepId: String) = "$recipeId/$stepId"

/** Whether the StepTimer under [key] belongs to [recipeId]. */
fun isTimerOf(recipeId: String, key: String) = key.startsWith("$recipeId/")

/** Where timers survive process death. */
interface TimerPersistence {
    fun load(): Map<String, TimerState>
    fun save(timers: Map<String, TimerState>)
}

/** The Android side of timers: the foreground service and the finished alert (vibration, sound). */
interface TimerHost {
    /** A timer is running; make sure the foreground service is up to keep the process alive and tick. */
    fun ensureRunning()

    /** The timer under [key] reached zero. */
    fun finished(key: String, title: String)

    /** The Done timer under [key] was reset or restarted; its finished alert is obsolete. */
    fun dismissFinished(key: String)
}

/**
 * All StepTimers of the app, keyed by `recipeId/stepId`. Idle timers are not kept: no entry means
 * idle. State is persisted on every change and restored on creation, so running timers pick up
 * where the clock says they are after process death.
 */
class StepTimerStore(
    private val clock: () -> Long,
    private val persistence: TimerPersistence,
    private val host: TimerHost,
) {
    private val state = MutableStateFlow(persistence.load())
    val timers: StateFlow<Map<String, TimerState>> = state.asStateFlow()

    init {
        tick()
        if (runningKeys().isNotEmpty()) host.ensureRunning()
    }

    fun runningKeys(): Set<String> =
        state.value.filterValues { it.status == TimerStatus.Running }.keys

    /** Tap on the timer button; [durationSecs] and [title] are used when the timer is not kept yet. */
    fun toggle(key: String, title: String, durationSecs: Int) {
        val current = state.value[key] ?: TimerState.idle(durationSecs, title)
        val next = current.toggle(clock())
        put(key, next)
        if (current.status == TimerStatus.Done) host.dismissFinished(key)
        if (next.status == TimerStatus.Running) host.ensureRunning()
    }

    fun reset(key: String) {
        val current = state.value[key] ?: return
        put(key, current.reset())
        if (current.status == TimerStatus.Done) host.dismissFinished(key)
    }

    /**
     * Drops every timer of [recipeId], whatever its state (Stop cooking). The service sees the
     * running ones go, removes their notifications and stops itself when none is left; the finished
     * alerts of Done ones are dismissed here.
     */
    fun cancelRecipe(recipeId: String) {
        val mine = state.value.filterKeys { isTimerOf(recipeId, it) }
        if (mine.isEmpty()) return
        change { it - mine.keys }
        mine.filterValues { it.status == TimerStatus.Done }.keys.forEach(host::dismissFinished)
    }

    /**
     * Finishes every running timer whose time is up and alerts for each, once. Safe to call from
     * several places (the service and the visible timers); a finished timer is no longer Running.
     */
    fun tick() {
        val now = clock()
        val finished = state.value
            .filterValues { it.status == TimerStatus.Running }
            .mapValues { (_, t) -> t.tick(now) }
            .filterValues { it.status == TimerStatus.Done }
        if (finished.isEmpty()) return
        change { map -> map + finished }
        finished.forEach { (key, t) -> host.finished(key, t.title) }
    }

    private fun put(key: String, timer: TimerState) = change { map ->
        if (timer.status == TimerStatus.Idle) map - key else map + (key to timer)
    }

    private fun change(transform: (Map<String, TimerState>) -> Map<String, TimerState>) {
        state.update(transform)
        persistence.save(state.value)
    }
}
