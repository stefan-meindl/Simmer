package com.simmer.app.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Everything the store keeps: the Steps marked done per recipe, and the servings and Selection
 * those Steps were marked under (the Steps differ with them, so resuming needs both).
 */
data class CookingSnapshot(
    val done: Map<String, Set<String>> = emptyMap(),
    val choices: Map<String, PlannedChoice> = emptyMap(),
)

/** Where Cooking Progress survives process death. */
interface CookingPersistence {
    fun load(): CookingSnapshot
    fun save(snapshot: CookingSnapshot)
}

/**
 * Steps marked done per recipe, with the servings and Selection they belong to. It survives
 * configuration changes, leaving the flow and (through [persistence]) process death: the state is
 * written on every change and read on creation. The flow ViewModel additionally mirrors the done
 * Steps into SavedStateHandle and calls [restore] on start.
 */
class CookingProgressStore(private val persistence: CookingPersistence? = null) {
    private val state = MutableStateFlow(persistence?.load() ?: CookingSnapshot())

    /** Whole state, for the Recipes tab's "Cooking now" cards. */
    val snapshot: StateFlow<CookingSnapshot> = state.asStateFlow()

    fun doneSteps(recipeId: String): Set<String> = state.value.done[recipeId].orEmpty()

    fun observe(recipeId: String): Flow<Set<String>> =
        state.map { it.done[recipeId].orEmpty() }.distinctUntilChanged()

    fun markDone(recipeId: String, stepId: String) = changeDone(recipeId) { it + stepId }

    fun undo(recipeId: String, stepId: String) = changeDone(recipeId) { it - stepId }

    /**
     * Replaces this recipe's progress with [stepIds] saved by the flow. Saved state belongs to the
     * same servings and Selection the flow restored with it, so it wins over whatever is in memory.
     */
    fun restore(recipeId: String, stepIds: Collection<String>) = changeDone(recipeId) { stepIds.toSet() }

    /** Clears the marked Steps of this recipe; its servings and Selection are kept. */
    fun reset(recipeId: String) = change { it.copy(done = it.done - recipeId) }

    /** The servings and Selection the flow last had for this recipe, if it was ever opened. */
    fun choice(recipeId: String): PlannedChoice? = state.value.choices[recipeId]

    fun setChoice(recipeId: String, choice: PlannedChoice) {
        if (state.value.choices[recipeId] == choice) return
        change { it.copy(choices = it.choices + (recipeId to choice)) }
    }

    private fun changeDone(recipeId: String, transform: (Set<String>) -> Set<String>) =
        change { it.copy(done = it.done + (recipeId to transform(it.done[recipeId].orEmpty()))) }

    private fun change(transform: (CookingSnapshot) -> CookingSnapshot) {
        state.update(transform)
        persistence?.save(state.value)
    }
}
