package com.simmer.app.domain

/** What the Pipeline job is doing, as the Working screen and the notification show it. */
sealed interface ImportState {
    /** Nothing running: the paste stage (or, with an Add recipe draft, the review). */
    data object Idle : ImportState

    /** Running the pass at [stage]. */
    data class Working(val stage: PipelineStage) : ImportState

    /** The recipe is in the Add recipe draft store, waiting for review. */
    data object Ready : ImportState

    /** The run failed: the error stage. */
    data object Failed : ImportState
}

/** How a stage is drawn on the Working screen: orange check, orange ring, grey ring. */
enum class StageMark { Done, Current, Pending }

/** The four stages with their marks while [current] runs: earlier ones Done, later ones Pending. */
fun stageMarks(current: PipelineStage): List<Pair<PipelineStage, StageMark>> =
    PipelineStage.entries.map { stage ->
        stage to when {
            stage.ordinal < current.ordinal -> StageMark.Done
            stage == current -> StageMark.Current
            else -> StageMark.Pending
        }
    }
