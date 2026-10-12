package com.simmer.app.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
        if (next.status == TimerStatus.Running) host.ensureRunning()
    }

    fun reset(key: String) {
        state.value[key]?.let { put(key, it.reset()) }
    }

    /** Finishes every running timer whose time is up and alerts for each, once. */
    fun tick() {
        val now = clock()
        val finished = state.value.filter { (_, t) -> t.status == TimerStatus.Running && t.tick(now).status == TimerStatus.Done }
        if (finished.isEmpty()) return
        change { map -> map.mapValues { (_, t) -> t.tick(now) } }
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
