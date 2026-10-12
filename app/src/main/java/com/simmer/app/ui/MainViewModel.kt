package com.simmer.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simmer.app.data.PrefsRepository
import com.simmer.app.data.ShoppingRepository
import com.simmer.app.domain.uncheckedCount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel shared across the app shell (theme, badge counts, etc.).
 *
 * [isDarkTheme] is a tri-state resolved value:
 *   - theme="dark"   → true
 *   - theme="light"  → false
 *   - theme="system" → delegates to [systemIsDark] passed from the UI layer
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val prefs: PrefsRepository,
    shopping: ShoppingRepository,
) : ViewModel() {

    /** Raw theme preference: "system" | "light" | "dark". */
    val themePreference: StateFlow<String> = prefs.theme
        .stateIn(viewModelScope, SharingStarted.Eagerly, "system")

    /**
     * Resolved dark theme state.
     *
     * Callers that need to react to "system" mode must additionally
     * observe [themePreference] and check `isSystemInDarkTheme()` themselves
     * to determine the effective value when the preference is "system".
     *
     * For convenience, this flow maps "dark" → true, "light" → false,
     * and "system" → false (system resolution done in the UI layer).
     */
    val isDarkTheme: StateFlow<Boolean> = prefs.theme
        .map { it == "dark" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Unchecked Shopping list items, for the tab badge. */
    val shoppingListBadgeCount: StateFlow<Int> = shopping.items
        .map(::uncheckedCount)
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /**
     * Toggles theme between "dark" and "light".
     *
     * @param isDark if true writes "dark", otherwise "light".
     */
    fun setTheme(isDark: Boolean) {
        viewModelScope.launch {
            prefs.setTheme(if (isDark) "dark" else "light")
        }
    }
}
