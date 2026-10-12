package com.simmer.app.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Steps marked done per recipe, in memory. It survives configuration changes and leaving the
 * flow; surviving process death is the flow ViewModel's job (it mirrors this into SavedStateHandle
 * and calls [restore] on start).
 */
@Singleton
class CookingProgressStore @Inject constructor() {
    private val done = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

    fun doneSteps(recipeId: String): Set<String> = done.value[recipeId].orEmpty()

    fun observe(recipeId: String): Flow<Set<String>> =
        done.map { it[recipeId].orEmpty() }.distinctUntilChanged()

    fun markDone(recipeId: String, stepId: String) = change(recipeId) { it + stepId }

    fun undo(recipeId: String, stepId: String) = change(recipeId) { it - stepId }

    /**
     * Replaces this recipe's progress with [stepIds] saved by the flow. Saved state belongs to the
     * same servings and Selection the flow restored with it, so it wins over whatever is in memory.
     */
    fun restore(recipeId: String, stepIds: Collection<String>) = change(recipeId) { stepIds.toSet() }

    fun reset(recipeId: String) = done.update { it - recipeId }

    private fun change(recipeId: String, transform: (Set<String>) -> Set<String>) =
        done.update { it + (recipeId to transform(it[recipeId].orEmpty())) }
}
