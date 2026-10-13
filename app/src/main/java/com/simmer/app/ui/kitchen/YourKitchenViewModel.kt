package com.simmer.app.ui.kitchen

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simmer.app.data.PrefsRepository
import com.simmer.app.domain.KitchenChoice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Your kitchen: every toggle and every added appliance is written to DataStore at once.
 * The search text lives in [SavedStateHandle] so it survives rotation and process death.
 */
@HiltViewModel
class YourKitchenViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val prefs: PrefsRepository,
) : ViewModel() {

    /** Null until DataStore has been read. */
    val choice: StateFlow<KitchenChoice?> =
        prefs.kitchen.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val query: StateFlow<String> = savedState.getStateFlow(KEY_QUERY, "")

    fun setQuery(text: String) {
        savedState[KEY_QUERY] = text
    }

    fun toggle(key: String) = update { it.toggle(key) }

    /** Adds [name] as an appliance of the user's own, selects it and clears the search. */
    fun addCustom(name: String) {
        update { it.addCustom(name) }
        setQuery("")
    }

    // Launches run in order on the main dispatcher and each reads the stored value, so quick taps do not overwrite each other.
    private fun update(change: (KitchenChoice) -> KitchenChoice) {
        viewModelScope.launch { prefs.setKitchen(change(prefs.kitchen.first())) }
    }

    private companion object {
        const val KEY_QUERY = "query"
    }
}
