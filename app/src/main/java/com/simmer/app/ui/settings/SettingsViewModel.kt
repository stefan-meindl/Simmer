package com.simmer.app.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simmer.app.data.PrefsRepository
import com.simmer.app.data.ReaderController
import com.simmer.app.reader.ReaderEngine
import com.simmer.app.reader.ReaderStateSource
import com.simmer.app.data.RecipeRepository
import com.simmer.app.domain.ReaderStatus
import com.simmer.app.domain.KitchenMode
import com.simmer.app.domain.orderAppliances
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
    val reader: ReaderStatus,
    val readerEngine: ReaderEngine,
    val readerWifiOnly: Boolean,
)

/** Every Settings choice is written to DataStore; the language is also applied to the running app. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: PrefsRepository,
    recipes: RecipeRepository,
    private val reader: ReaderController,
    private val readerState: ReaderStateSource,
) : ViewModel() {

    private val previewRecipe = recipes.recipes.map { list -> list.firstOrNull { it.id == PREVIEW_RECIPE_ID } }

    private val display = combine(prefs.lang, prefs.units, prefs.spices) { lang, units, spices ->
        Triple(lang, units, spices)
    }
    private val kitchen = combine(prefs.appliances, prefs.kitchenMode) { appliances, mode -> appliances to mode }

    private val readerUi = combine(readerState.state, prefs.readerWifiOnly) { state, wifiOnly -> state to wifiOnly }

    val ui: StateFlow<SettingsUi?> = combine(display, kitchen, previewRecipe, readerUi) { (lang, units, spices), (appliances, mode), recipe, (readerUiState, wifiOnly) ->
        SettingsUi(
            lang = lang,
            units = units,
            spices = spices,
            appliances = orderAppliances(appliances),
            kitchenMode = KitchenMode.fromKey(mode),
            preview = recipe?.let { settingsPreview(it, units, spices, lang) },
            reader = readerUiState.status,
            readerEngine = readerUiState.engine,
            readerWifiOnly = wifiOnly,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setLang(lang: String) {
        viewModelScope.launch {
            // The write has finished when setLang returns; only then recreate the activity.
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

    fun setReaderWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch {
            prefs.setReaderWifiOnly(wifiOnly)
            reader.networkRuleChanged()
        }
    }

    fun downloadReader() {
        viewModelScope.launch {
            if (ui.value?.readerEngine == ReaderEngine.Nano) readerState.downloadNano() else reader.download()
        }
    }

    fun cancelReaderDownload() = reader.cancel()

    fun removeReader() = reader.remove()
}
