package com.simmer.app.ui.shopping

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simmer.app.data.ShoppingRepository
import com.simmer.app.domain.ShoppingListView
import com.simmer.app.domain.shoppingListView
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val KEY_DRAFT = "draft"
private const val KEY_BASKET_OPEN = "basketOpen"

/** The typed-but-unsent item and the basket's open state live in [SavedStateHandle]; the list itself is in Room. */
@HiltViewModel
class ShoppingListViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val shopping: ShoppingRepository,
) : ViewModel() {

    /** Null until the first read from Room. */
    val listView: StateFlow<ShoppingListView?> = shopping.items
        .map { shoppingListView(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

    fun setChecked(id: String, checked: Boolean) {
        viewModelScope.launch { shopping.setChecked(id, checked) }
    }
}
