package com.simmer.app.data

import com.simmer.app.domain.CookingSessions
import com.simmer.app.domain.StoppedCooking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** How long an Undo offer stays: after a swipe removal on the Recipes tab and after a Shopping list clear. */
const val UNDO_WINDOW_MS = 5_000L

/** What a swipe on a Recipes tab card removed, kept for Undo. */
sealed interface Removal {
    val title: String

    /** A Planned card was unplanned: its Shopping items and Planned choice are in [snapshot]. */
    data class Unplanned(override val title: String, val snapshot: ShoppingSnapshot) : Removal

    /** A Cooking-now card was stopped: its done Steps are in [stopped]. */
    data class Stopped(override val title: String, val stopped: StoppedCooking) : Removal
}

/** Deleting Planned and Cooking-now cards, with a time-limited Undo. Shared by swipe and the TalkBack actions. */
class RecipeRemovals(
    private val shopping: ShoppingRepository,
    private val sessions: CookingSessions,
    private val scope: CoroutineScope,
) {
    private val _lastRemoval = MutableStateFlow<Removal?>(null)
    private var expiry: Job? = null

    /** The removal Undo would reverse; null after Undo, after [UNDO_WINDOW_MS] or once dismissed. */
    val lastRemoval: StateFlow<Removal?> = _lastRemoval

    /** Unplan: the recipe's Shopping items and Planned state go. */
    fun unplan(recipeId: String, title: String) {
        scope.launch { announce(Removal.Unplanned(title, shopping.removeRecipe(recipeId))) }
    }

    /** Stop cooking: timers are cancelled and Cooking Progress cleared; servings, Selection and Planned stay. */
    fun stopCooking(recipeId: String, title: String) {
        announce(Removal.Stopped(title, sessions.stop(recipeId)))
    }

    fun undo() {
        val removal = _lastRemoval.value ?: return
        dismiss()
        when (removal) {
            is Removal.Unplanned -> scope.launch { shopping.restore(removal.snapshot) }
            is Removal.Stopped -> sessions.restore(removal.stopped)
        }
    }

    fun dismiss() {
        expiry?.cancel()
        _lastRemoval.value = null
    }

    private fun announce(removal: Removal) {
        expiry?.cancel()
        _lastRemoval.value = removal
        expiry = scope.launch {
            delay(UNDO_WINDOW_MS)
            _lastRemoval.value = null
        }
    }
}
