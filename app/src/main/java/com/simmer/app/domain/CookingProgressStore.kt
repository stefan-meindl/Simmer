package com.simmer.app.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Steps marked done per recipe (in memory; persistence is owned by #7). */
@Singleton
class CookingProgressStore @Inject constructor() {
    private val done = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

    fun doneSteps(recipeId: String): Set<String> = done.value[recipeId].orEmpty()

    fun markDone(recipeId: String, stepId: String) =
        done.update { it + (recipeId to it[recipeId].orEmpty() + stepId) }

    fun reset(recipeId: String) = done.update { it - recipeId }
}
