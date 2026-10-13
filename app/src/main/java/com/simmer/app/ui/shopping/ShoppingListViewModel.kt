package com.simmer.app.ui.shopping

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.os.SystemClock
import com.simmer.app.data.ShoppingRepository
import com.simmer.app.data.ShoppingSnapshot
import com.simmer.app.domain.GracePeriod
import com.simmer.app.domain.GracePhase
import com.simmer.app.domain.ShoppingListView
import com.simmer.app.domain.shoppingListView
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val KEY_DRAFT = "draft"
private const val KEY_BASKET_OPEN = "basketOpen"

/** How long "Cleared N items · Undo" stays offered. */
private const val CLEARED_UNDO_MS = 5_000L

/** The typed-but-unsent item and the basket's open state live in [SavedStateHandle]; the list itself is in Room. */
@HiltViewModel
class ShoppingListViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val shopping: ShoppingRepository,
) : ViewModel() {

    private val grace = GracePeriod(clock = SystemClock::elapsedRealtime)
    /** Main thread only, like [grace]. */
    private val graceJobs = HashMap<String, Job>()

    /** Ids unticked from the grace period whose Room write may not have been emitted yet; shown unchecked meanwhile. */
    private val unticked = MutableStateFlow<Set<String>>(emptySet())

    /** Phase of each recently ticked item still held in its aisle; absent items follow Room. */
    val gracePhases: StateFlow<Map<String, GracePhase>> = grace.phases

    /** Null until the first read from Room. Display only: ticks are held in place by the grace period. */
    val listView: StateFlow<ShoppingListView?> = combine(shopping.items, grace.phases, unticked) { items, phases, unticked ->
        shoppingListView(items, phases.keys, unticked)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Whether the header menu offers "Clear ticked off". */
    val canClearTicked: StateFlow<Boolean> = shopping.items.map { all -> all.any { it.checked } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Whether the header menu offers "Clear all": any item, or Planned state left without items. */
    val canClearAll: StateFlow<Boolean> = combine(shopping.items, shopping.planned) { items, planned ->
        items.isNotEmpty() || planned.isNotEmpty()
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _cleared = MutableStateFlow<ShoppingSnapshot?>(null)
    private var clearedJob: Job? = null

    /** What the last clear removed, for the snackbar. Null after Undo, after [CLEARED_UNDO_MS], or once the tab is left. */
    val cleared: StateFlow<ShoppingSnapshot?> = _cleared

    fun clearTicked() {
        viewModelScope.launch { announce(shopping.clearTicked()) }
    }

    fun clearAll() {
        viewModelScope.launch { announce(shopping.clearAll()) }
    }

    /** Only a clear that removed list items is announced: "Cleared 0 items" would be wrong for Planned state alone. */
    private fun announce(snapshot: ShoppingSnapshot) {
        clearedJob?.cancel()
        if (snapshot.items.isEmpty()) {
            _cleared.value = null
            return
        }
        _cleared.value = snapshot
        clearedJob = viewModelScope.launch {
            delay(CLEARED_UNDO_MS)
            _cleared.value = null
        }
    }

    /** Snackbar Undo: items, `checked` states and Planned choices come back. */
    fun undoClear() {
        val last = _cleared.value ?: return
        dismissCleared()
        viewModelScope.launch { shopping.restore(last) }
    }

    fun dismissCleared() {
        clearedJob?.cancel()
        _cleared.value = null
    }

    val draft: StateFlow<String> = savedState.getStateFlow(KEY_DRAFT, "")

    /** The "In your basket" section starts collapsed. */
    val basketOpen: StateFlow<Boolean> = savedState.getStateFlow(KEY_BASKET_OPEN, false)

    fun setDraft(text: String) {
        savedState[KEY_DRAFT] = text
    }

    fun toggleBasket() {
        savedState[KEY_BASKET_OPEN] = !basketOpen.value
    }

    fun addDraft() {
        val name = draft.value
        if (name.isBlank()) return
        savedState[KEY_DRAFT] = ""
        viewModelScope.launch { shopping.addManualItem(name) }
    }

    /**
     * Writes to Room at once; ticking additionally holds the row in place for the grace period. Unticking is the undo.
     * With [reducedMotion] (system "remove animations") the row skips the fade. Main thread only.
     */
    fun setChecked(id: String, checked: Boolean, reducedMotion: Boolean = false) {
        graceJobs.remove(id)?.cancel()
        if (checked) {
            unticked.update { it - id }
            val fadeMs = if (reducedMotion) 0L else GracePeriod.FADE_MS
            grace.tick(id, fadeMs)
            graceJobs[id] = viewModelScope.launch {
                // Wait exactly as long as the injected clock says, so an early wake-up cannot skip a phase.
                while (true) {
                    delay(grace.msUntilChange(id) ?: break)
                    grace.advance()
                }
                graceJobs.remove(id)
            }
        } else {
            if (id in grace.phases.value) unticked.update { it + id }
            grace.untick(id)
        }
        viewModelScope.launch { shopping.setChecked(id, checked) }
    }

    /** The tab was left or the app backgrounded: ticked items go straight to the basket. */
    fun leaveScreen() {
        graceJobs.values.forEach { it.cancel() }
        graceJobs.clear()
        grace.clear()
        unticked.value = emptySet()
        dismissCleared()
    }
}
