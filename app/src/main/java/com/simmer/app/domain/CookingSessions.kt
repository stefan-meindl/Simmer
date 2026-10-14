package com.simmer.app.domain

import com.simmer.app.domain.model.Recipe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** A recipe in progress and the servings and Selection it is being cooked with. */
data class InProgressEntry(val recipe: Recipe, val choice: PlannedChoice)

/** What Stop cooking cleared that Undo can bring back: the done Steps. Cancelled StepTimers are not kept. */
data class StoppedCooking(val recipeId: String, val doneSteps: Set<String>)

/**
 * Which recipes are in progress: at least one StepTimer running or paused, or at least one Step
 * marked done, and not every Step done (that is a finished recipe). Built over the two stores that
 * hold that state, both persisted, so it holds across process death too.
 */
class CookingSessions(
    private val progress: CookingProgressStore,
    private val timers: StepTimerStore,
) {
    /** In progress right now, for [recipe] cooked with [choice]. */
    fun isInProgress(recipe: Recipe, choice: PlannedChoice): Boolean =
        inProgress(recipe, choice, progress.snapshot.value, timers.timers.value)

    /** Every recipe of [recipes] that is in progress, in order, each with the choice it is cooked with. */
    fun observe(recipes: List<Recipe>): Flow<List<InProgressEntry>> =
        combine(progress.snapshot, timers.timers) { snapshot, timerStates ->
            recipes.mapNotNull { recipe ->
                val choice = choiceOf(recipe, snapshot)
                InProgressEntry(recipe, choice).takeIf { inProgress(recipe, choice, snapshot, timerStates) }
            }
        }

    /** The choice a recipe in progress is cooked with, so reopening it keeps the marked Steps valid; null when not in progress. */
    fun resumeChoice(recipe: Recipe): PlannedChoice? {
        val choice = choiceOf(recipe, progress.snapshot.value)
        return choice.takeIf { isInProgress(recipe, it) }
    }

    /** Stop cooking: cancels this recipe's timers and clears its Cooking Progress. Servings and Selection are kept. Returns the cleared done Steps for [restore]. */
    fun stop(recipeId: String): StoppedCooking {
        val stopped = StoppedCooking(recipeId, progress.doneSteps(recipeId))
        timers.cancelRecipe(recipeId)
        progress.reset(recipeId)
        return stopped
    }

    /** Undo of [stop]: the done Steps come back; the cancelled StepTimers do not. */
    fun restore(stopped: StoppedCooking) = progress.restore(stopped.recipeId, stopped.doneSteps)

    private fun choiceOf(recipe: Recipe, snapshot: CookingSnapshot): PlannedChoice =
        snapshot.choices[recipe.id] ?: Phase1State.initial(recipe, planned = null).toChoice()

    private fun inProgress(
        recipe: Recipe,
        choice: PlannedChoice,
        snapshot: CookingSnapshot,
        timerStates: Map<String, TimerState>,
    ): Boolean {
        val stepIds = resolve(recipe, choice.servings, choice.selection).steps.map { it.id }.toSet()
        val done = snapshot.done[recipe.id].orEmpty().count { it in stepIds }
        val finished = stepIds.isNotEmpty() && done == stepIds.size
        val ticking = timerStates.any { (key, t) ->
            isTimerOf(recipe.id, key) && (t.status == TimerStatus.Running || t.status == TimerStatus.Paused)
        }
        return !finished && (done > 0 || ticking)
    }
}
