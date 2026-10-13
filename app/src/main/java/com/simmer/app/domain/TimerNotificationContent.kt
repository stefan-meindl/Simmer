package com.simmer.app.domain

/** A button on an ongoing timer notification. */
enum class TimerAction { Pause, Resume, Reset }

/**
 * What an ongoing timer notification shows. Equal values mean the posted notification is up to
 * date: it only changes about once a second while running (the system counts down by itself).
 *
 * @param endAtMs When the timer ends while running (the system chronometer counts down to it); 0 while paused.
 * @param elapsedSecs Whole seconds elapsed, for the progress bar.
 * @param clock Time left, as "m:ss".
 */
data class TimerNotificationContent(
    val title: String,
    val recipeTitle: String,
    val paused: Boolean,
    val durationSecs: Int,
    val elapsedSecs: Int,
    val endAtMs: Long,
    val clock: String,
    val actions: List<TimerAction>,
)

/** The notification of an active [timer] at [now]: Pause while running; Resume and Reset when paused. */
fun timerNotificationContent(timer: TimerState, now: Long): TimerNotificationContent {
    val paused = timer.status != TimerStatus.Running
    val remainingMs = timer.remainingMsAt(now)
    return TimerNotificationContent(
        title = timer.title,
        recipeTitle = timer.recipeTitle,
        paused = paused,
        durationSecs = timer.durationSecs,
        elapsedSecs = ((timer.durationSecs * 1000L - remainingMs) / 1000).toInt().coerceIn(0, timer.durationSecs),
        endAtMs = if (paused) 0 else timer.endAtMs,
        clock = formatClock(remainingMs),
        actions = if (paused) listOf(TimerAction.Resume, TimerAction.Reset) else listOf(TimerAction.Pause),
    )
}
