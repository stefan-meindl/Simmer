package com.simmer.app.ui.shopping

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.os.SystemClock
import com.simmer.app.data.ShoppingRepository
import com.simmer.app.domain.GracePeriod
import com.simmer.app.domain.GracePhase
import com.simmer.app.domain.ShoppingListView
import com.simmer.app.domain.shoppingListView
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** `duration-base`: how long a row takes to fade out and collapse. */
private const val FADE_MS = 200L
private const val KEY_DRAFT = "draft"
private const val KEY_BASKET_OPEN = "basketOpen"

/** The typed-but-unsent item and the basket's open state live in [SavedStateHandle]; the list itself is in Room. */
@HiltViewModel
class ShoppingListViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val shopping: ShoppingRepository,
) : ViewModel() {

    private val grace = GracePeriod(clock = SystemClock::elapsedRealtime)
    private val graceJobs = HashMap<String, Job>()

    /** Set by the screen from the system "remove animations" setting; ticked rows then skip the fade. */
    var reducedMotion: Boolean = false

    /** Phase of each recently ticked item still held in its aisle; absent items follow Room. */
    val gracePhases: StateFlow<Map<String, GracePhase>> = grace.phases

    /** Null until the first read from Room. Display only: ticks are held in place by the grace period. */
    val listView: StateFlow<ShoppingListView?> = combine(shopping.items, grace.phases) { items, phases ->
        shoppingListView(items, phases.keys)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

    /** Writes to Room at once; ticking additionally holds the row in place for the grace period. Unticking is the undo. */
    fun setChecked(id: String, checked: Boolean) {
        graceJobs.remove(id)?.cancel()
        if (checked) {
            val fadeMs = if (reducedMotion) 0L else FADE_MS
            grace.tick(id, fadeMs)
            graceJobs[id] = viewModelScope.launch {
                delay(GracePeriod.GRACE_MS)
                grace.advance()
                delay(fadeMs)
                grace.advance()
                graceJobs.remove(id)
            }
        } else {
            grace.untick(id)
        }
        viewModelScope.launch { shopping.setChecked(id, checked) }
    }

    /** The tab was left or the app backgrounded: ticked items go straight to the basket. */
    fun leaveScreen() {
        graceJobs.values.forEach { it.cancel() }
        graceJobs.clear()
        grace.clear()
    }
}
