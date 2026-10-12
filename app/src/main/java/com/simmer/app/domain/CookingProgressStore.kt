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

    fun markDone(recipeId: String, stepId: String) =
        done.update { it + (recipeId to it[recipeId].orEmpty() + stepId) }

    fun undo(recipeId: String, stepId: String) =
        done.update { it + (recipeId to it[recipeId].orEmpty() - stepId) }

    /** Loads [stepIds] saved before process death, unless this recipe already has progress in memory. */
    fun restore(recipeId: String, stepIds: Collection<String>) =
        done.update { if (it[recipeId].isNullOrEmpty() && stepIds.isNotEmpty()) it + (recipeId to stepIds.toSet()) else it }

    fun reset(recipeId: String) = done.update { it - recipeId }
}
