package com.simmer.app.domain

/** How often running timers are checked and their clock redrawn. */
const val TIMER_TICK_MS = 250L

enum class TimerStatus { Idle, Running, Paused, Done }

/**
 * One StepTimer. Immutable; every transition returns a new state. Times are epoch milliseconds
 * passed in by the caller, so a running timer keeps counting while the process is gone.
 *
 * @param title Step title, shown on the ongoing notification.
 * @param recipeTitle Recipe title, shown beside the step title on the notification.
 * @param remainingMs Time left while Idle or Paused; stale while Running (see [remainingMsAt]).
 * @param endAtMs When a Running timer finishes.
 * @param pausedFromNotification A Paused timer was paused with the notification's Pause action, so
 *   its notification stays (with Resume and Reset) and the service stays alive. A pause in the app
 *   clears the notification instead.
 */
data class TimerState(
    val durationSecs: Int,
    val status: TimerStatus = TimerStatus.Idle,
    val remainingMs: Long = durationSecs * 1000L,
    val endAtMs: Long = 0,
    val title: String = "",
    val pausedFromNotification: Boolean = false,
    val recipeTitle: String = "",
) {
    private val durationMs get() = durationSecs * 1000L

    fun remainingMsAt(now: Long): Long = when (status) {
        TimerStatus.Idle, TimerStatus.Paused -> remainingMs
        TimerStatus.Running -> (endAtMs - now).coerceIn(0, durationMs)
        TimerStatus.Done -> 0
    }

    /** 0 at the start, 1 when finished. */
    fun progressAt(now: Long): Float =
        if (durationMs == 0L) 0f else 1f - remainingMsAt(now).toFloat() / durationMs

    /** The timer button was tapped: start, pause, resume, or (when Done) back to Idle. */
    fun toggle(now: Long): TimerState = when (status) {
        TimerStatus.Idle, TimerStatus.Paused ->
            copy(status = TimerStatus.Running, endAtMs = now + remainingMs, pausedFromNotification = false)
        TimerStatus.Running -> copy(status = TimerStatus.Paused, remainingMs = remainingMsAt(now))
        TimerStatus.Done -> reset()
    }

    /** The notification's Pause action: like pausing in the app, but the notification stays. */
    fun pauseFromNotification(now: Long): TimerState =
        if (status == TimerStatus.Running) toggle(now).copy(pausedFromNotification = true) else this

    fun reset(): TimerState =
        copy(status = TimerStatus.Idle, remainingMs = durationMs, endAtMs = 0, pausedFromNotification = false)

    /** Moves a Running timer whose time is up to Done. */
    fun tick(now: Long): TimerState =
        if (status == TimerStatus.Running && now >= endAtMs) copy(status = TimerStatus.Done, remainingMs = 0) else this

    companion object {
        fun idle(durationSecs: Int, title: String = "", recipeTitle: String = "") =
            TimerState(durationSecs, title = title, recipeTitle = recipeTitle)
    }
}

/** Countdown text: "m:ss", or "h:mm:ss" from one hour. Partial seconds round up. */
fun formatClock(ms: Long): String {
    val total = (ms.coerceAtLeast(0) + 999) / 1000
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (h > 0) String.format(java.util.Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(java.util.Locale.ROOT, "%d:%02d", m, s)
}
