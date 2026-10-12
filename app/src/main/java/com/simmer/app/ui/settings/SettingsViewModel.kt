package com.simmer.app.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simmer.app.data.PrefsRepository
import com.simmer.app.data.RecipeRepository
import com.simmer.app.domain.KitchenMode
import com.simmer.app.domain.SettingsPreview
import com.simmer.app.domain.settingsPreview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The recipe the preview panel shows. */
private const val PREVIEW_RECIPE_ID = "chilli"

data class SettingsUi(
    val lang: String,
    val units: String,
    val spices: String,
    /** Appliance keys the user owns (custom ones start with "c:"), in a stable order. */
    val appliances: List<String>,
    val kitchenMode: KitchenMode,
    /** Null until the built-in recipes are seeded. */
    val preview: SettingsPreview?,
)

/** Every Settings choice is written to DataStore; the language is also applied to the running app. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: PrefsRepository,
    recipes: RecipeRepository,
) : ViewModel() {

    private val previewRecipe = recipes.recipes.map { list -> list.firstOrNull { it.id == PREVIEW_RECIPE_ID } }

    val ui: StateFlow<SettingsUi?> = combine(
        prefs.lang,
        prefs.units,
        prefs.spices,
        prefs.appliances,
        prefs.kitchenMode,
        previewRecipe,
    ) { values ->
        val lang = values[0] as String
        val units = values[1] as String
        val spices = values[2] as String

        @Suppress("UNCHECKED_CAST")
        val appliances = values[3] as Set<String>
        val mode = values[4] as String
        val recipe = values[5] as com.simmer.app.domain.model.Recipe?
        SettingsUi(
            lang = lang,
            units = units,
            spices = spices,
            appliances = appliances.sorted(),
            kitchenMode = KitchenMode.fromKey(mode),
            preview = recipe?.let { settingsPreview(it, units, spices, lang) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setLang(lang: String) {
        viewModelScope.launch {
            prefs.setLang(lang)
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang))
        }
    }

    fun setUnits(units: String) {
        viewModelScope.launch { prefs.setUnits(units) }
    }

    fun setSpices(spices: String) {
        viewModelScope.launch { prefs.setSpices(spices) }
    }

    fun setKitchenMode(mode: KitchenMode) {
        viewModelScope.launch { prefs.setKitchenMode(mode.key) }
    }
}
