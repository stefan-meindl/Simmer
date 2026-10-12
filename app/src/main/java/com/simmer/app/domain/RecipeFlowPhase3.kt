package com.simmer.app.domain

import com.simmer.app.domain.model.Recipe
import com.simmer.app.domain.model.ResolvedStep
import com.simmer.app.domain.model.Selection

/** One Step on the cooking screen with its done state and Needs split by whether they are finished. */
data class CookingStep(
    val step: ResolvedStep,
    val done: Boolean,
    val active: Boolean,
    /** Prep steps this step needs that are not done yet (shown as a warning). */
    val pendingNeeds: List<ResolvedStep>,
    /** Prep steps this step needs that are done (shown as a quiet line). */
    val doneNeeds: List<ResolvedStep>,
)

/** Flow Phase 3 content: every Step in display order plus the Cooking Progress totals. */
data class CookingView(val steps: List<CookingStep>, val doneCount: Int) {
    val total: Int get() = steps.size
    val fraction: Float get() = if (total == 0) 0f else doneCount.toFloat() / total
    val allDone: Boolean get() = total > 0 && doneCount == total

    /** The first unfinished Step, or null when everything is done. */
    val activeId: String? get() = steps.firstOrNull { it.active }?.step?.id
}

/** [steps] must already be in display order (prep first, as returned by [resolve]). */
fun cookingView(steps: List<ResolvedStep>, done: Set<String>): CookingView {
    val doneIds = steps.map { it.id }.filter { it in done }.toSet()
    val activeId = steps.firstOrNull { it.id !in doneIds }?.id
    return CookingView(
        steps = steps.map { s ->
            val (finished, pending) = s.needs.partition { it.id in doneIds }
            CookingStep(
                step = s,
                done = s.id in doneIds,
                active = s.id == activeId,
                pendingNeeds = pending,
                doneNeeds = finished,
            )
        },
        doneCount = doneIds.size,
    )
}

/**
 * "4 servings · Plant-based · Dark chocolate": [servingsText] followed by every swap that
 * differs from its default and every add-in that is on.
 */
fun selectionSummary(recipe: Recipe, selection: Selection, servingsText: String): String =
    (listOf(servingsText) + selectionLabels(recipe, selection)).joinToString(" · ")

/** Label of every swap option that differs from its default and every add-in that is on. */
fun selectionLabels(recipe: Recipe, selection: Selection): List<String> {
    val bits = mutableListOf<String>()
    recipe.variations.forEach { v ->
        if (v.kind == "swap") {
            val picked = selection.choice[v.id]
            if (picked != null && picked != v.def) v.options.firstOrNull { it.value == picked }?.let { bits += it.label }
        } else if (selection.add[v.id] == true) {
            bits += v.label
        }
    }
    return bits
}

/** Timer label such as "45 s", "5 min", "1 h 30 min". Units read the same in English and German. */
fun formatDuration(secs: Int): String {
    if (secs < 60) return "$secs s"
    val minutes = Math.round(secs / 60.0).toInt()
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m min"
        m == 0 -> "$h h"
        else -> "$h h $m min"
    }
}
