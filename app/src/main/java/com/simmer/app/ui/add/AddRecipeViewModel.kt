package com.simmer.app.ui.add

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simmer.app.data.AddRecipeSession
import com.simmer.app.data.PrefsRepository
import com.simmer.app.data.RecipeDraftStore
import com.simmer.app.domain.AddRecipeReview
import com.simmer.app.domain.EXAMPLE_TEXT
import com.simmer.app.domain.KitchenLine
import com.simmer.app.domain.KitchenMode
import com.simmer.app.domain.ReaderAction
import com.simmer.app.domain.addRecipeReview
import com.simmer.app.domain.canConvert
import com.simmer.app.domain.kitchenLine
import com.simmer.app.domain.readerAction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val KEY_TEXT = "text"
private const val KEY_TAB = "tab"

/** The review's two tabs. */
enum class ReviewTab { Ingredients, Steps }

sealed interface AddRecipeUi {
    data object Loading : AddRecipeUi

    /** The paste stage. */
    data class Paste(
        val text: String,
        /** Convert for the example, otherwise the Download placeholder. */
        val action: ReaderAction,
        /** False below the minimum text length: the bottom button is disabled. */
        val canConvert: Boolean,
        val kitchen: KitchenLine,
        val wifiOnly: Boolean,
    ) : AddRecipeUi

    /** The review stage for the unsaved recipe. */
    data class Review(val review: AddRecipeReview, val tab: ReviewTab, val lang: String) : AddRecipeUi
}

/**
 * The pasted text and the review tab live in [SavedStateHandle]. The converted recipe is the
 * [RecipeDraftStore] draft, which is memory only: after process death the screen is back at the
 * paste stage with the text restored.
 */
@HiltViewModel
class AddRecipeViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val session: AddRecipeSession,
    private val drafts: RecipeDraftStore,
    prefs: PrefsRepository,
) : ViewModel() {

    /** Straight from saved state so typing never lags behind the screen state. */
    val text: StateFlow<String> = savedState.getStateFlow(KEY_TEXT, "")

    private val tab = savedState.getStateFlow(KEY_TAB, ReviewTab.Ingredients.name)

    private val display = combine(prefs.units, prefs.spices, prefs.lang) { units, spices, lang ->
        Triple(units, spices, lang)
    }
    private val kitchen = combine(prefs.appliances, prefs.kitchenMode, prefs.readerWifiOnly) { appliances, mode, wifiOnly ->
        kitchenLine(appliances, KitchenMode.fromKey(mode)) to wifiOnly
    }

    val ui: StateFlow<AddRecipeUi> = combine(text, drafts.current, tab, display, kitchen) { text, draft, tab, (units, spices, lang), (line, wifiOnly) ->
        if (draft != null) {
            AddRecipeUi.Review(addRecipeReview(draft, units, spices, lang), ReviewTab.valueOf(tab), lang)
        } else {
            AddRecipeUi.Paste(text, readerAction(text), canConvert(text), line, wifiOnly)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddRecipeUi.Loading)

    private var saving = false

    fun setText(text: String) {
        savedState[KEY_TEXT] = text
    }

    fun clear() = setText("")

    fun useExample() = setText(EXAMPLE_TEXT)

    /** Converts the text when it is long enough and the stub can read it (the example). */
    fun convert() {
        val text = text.value
        if (!canConvert(text)) return
        session.convert(text) { "u" + System.currentTimeMillis().toString(36) }
    }

    fun editText() = session.editText()

    fun selectTab(tab: ReviewTab) {
        savedState[KEY_TAB] = tab.name
    }

    /** Saves the recipe, then reports its id. One save at a time. */
    fun save(onSaved: (recipeId: String) -> Unit) {
        if (saving) return
        saving = true
        viewModelScope.launch {
            try {
                session.save()?.let { onSaved(it.id) }
            } finally {
                saving = false
            }
        }
    }

    /** Leaving the screen without saving drops the draft. */
    override fun onCleared() {
        session.editText()
    }
}
