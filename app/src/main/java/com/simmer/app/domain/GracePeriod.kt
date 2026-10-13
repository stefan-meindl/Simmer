package com.simmer.app.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Where a recently ticked Shopping Item is in its grace period. */
enum class GracePhase {
    /** Struck through, still in its aisle. */
    Ticked,

    /** Fading out and collapsing; about to show in "In your basket". */
    Fading,
}

/**
 * The UI-only grace period after ticking a Shopping Item: the row keeps its place for [graceMs], then fades for
 * its own `fadeMs`, then is gone. Every item runs its own countdown from its own tick. Pure state: the caller
 * schedules [advance] calls; this reads the [clock] and moves each item to the phase it is in by now.
 */
class GracePeriod(
    private val clock: () -> Long,
    private val graceMs: Long = GRACE_MS,
) {
    private class Entry(val tickedAt: Long, val fadeMs: Long)

    private val entries = LinkedHashMap<String, Entry>()
    private val _phases = MutableStateFlow<Map<String, GracePhase>>(emptyMap())

    /** The items still held in place, with their phase. Absent means "belongs wherever Room says". */
    val phases: StateFlow<Map<String, GracePhase>> = _phases

    fun tick(id: String, fadeMs: Long) {
        entries[id] = Entry(clock(), fadeMs)
        advance()
    }

    /** Undo: the item is released at once and its countdown is dropped. */
    fun untick(id: String) {
        entries.remove(id)
        advance()
    }

    /** Leaving the screen: every item is released at once, so nothing lingers on return. */
    fun clear() {
        entries.clear()
        advance()
    }

    /** Re-reads the clock and publishes the current phases. */
    fun advance() {
        val now = clock()
        entries.entries.removeAll { now - it.value.tickedAt >= graceMs + it.value.fadeMs }
        _phases.value = entries.mapValues { (_, e) ->
            if (now - e.tickedAt >= graceMs) GracePhase.Fading else GracePhase.Ticked
        }
    }

    companion object {
        const val GRACE_MS = 3_000L
    }
}
