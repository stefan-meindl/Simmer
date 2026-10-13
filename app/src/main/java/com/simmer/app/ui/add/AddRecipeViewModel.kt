package com.simmer.app.ui.add

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simmer.app.data.AddRecipeSession
import com.simmer.app.data.ImportJob
import com.simmer.app.data.ReaderController
import com.simmer.app.data.PrefsRepository
import com.simmer.app.data.RecipeDraftStore
import com.simmer.app.data.draftFromJson
import com.simmer.app.data.toDraftJson
import com.simmer.app.domain.AddRecipeReview
import com.simmer.app.domain.EXAMPLE_TEXT
import com.simmer.app.domain.KitchenLine
import com.simmer.app.domain.ImportRequest
import com.simmer.app.domain.ImportState
import com.simmer.app.domain.KitchenMode
import com.simmer.app.domain.PipelineStage
import com.simmer.app.domain.ReaderStatus
import com.simmer.app.domain.ReaderAction
import com.simmer.app.domain.addRecipeReview
import com.simmer.app.domain.canConvert
import com.simmer.app.domain.kitchenLine
import com.simmer.app.domain.newRecipeId
import com.simmer.app.domain.readerAction
import com.simmer.app.domain.usesExampleShortcut
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val KEY_TEXT = "text"
private const val KEY_TAB = "tab"
private const val KEY_DRAFT = "draft"

/** How the review shows amounts and labels. */
private data class DisplayPrefs(val units: String, val spices: String, val lang: String)

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
    ) : AddRecipeUi

    /** The Working stage: the Pipeline is running the pass at [stage]. */
    data class Working(val stage: PipelineStage) : AddRecipeUi

    /** The error stage: the Pipeline could not read the text. */
    data object Error : AddRecipeUi

    /** The review stage for the unsaved recipe. */
    data class Review(val review: AddRecipeReview, val tab: ReviewTab) : AddRecipeUi
}

/**
 * The pasted text, the review tab and the converted recipe (the [RecipeDraftStore] draft, as JSON)
 * live in [SavedStateHandle], so the screen comes back at the same stage after process death.
 */
@HiltViewModel
class AddRecipeViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val session: AddRecipeSession,
    private val drafts: RecipeDraftStore,
    private val job: ImportJob,
    private val prefs: PrefsRepository,
    reader: ReaderController,
) : ViewModel() {

    /** Straight from saved state so typing never lags behind the screen state. */
    val text: StateFlow<String> = savedState.getStateFlow(KEY_TEXT, "")

    private val tab = savedState.getStateFlow(KEY_TAB, ReviewTab.Ingredients.name)

    private val display = combine(prefs.units, prefs.spices, prefs.lang, ::DisplayPrefs)
    private val kitchen = combine(prefs.appliances, prefs.kitchenMode) { appliances, mode ->
        kitchenLine(appliances, KitchenMode.fromKey(mode))
    }

    private val readerReady = reader.status.map { it == ReaderStatus.Ready }

    private val paste = combine(text, kitchen, readerReady) { text, line, ready ->
        AddRecipeUi.Paste(text, readerAction(text, ready), canConvert(text), line)
    }

    val ui: StateFlow<AddRecipeUi> = combine(paste, drafts.current, tab, display, job.state) { paste, draft, tab, display, state ->
        when {
            draft != null ->
                AddRecipeUi.Review(addRecipeReview(draft, display.units, display.spices, display.lang), ReviewTab.valueOf(tab))
            state is ImportState.Working -> AddRecipeUi.Working(state.stage)
            state == ImportState.Failed -> AddRecipeUi.Error
            else -> paste
        }
    }.onEach { if (it is AddRecipeUi.Review) reviewShown = true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddRecipeUi.Loading)

    private var saving = false

    /** True once this screen has shown the review: only then is leaving it a decision to drop the recipe. */
    private var reviewShown = false

    init {
        // A conversion that was running when the process died resumes (or shows the error stage).
        job.restore()
        // Back after process death: put the saved draft in the store, then keep the saved copy current.
        savedState.get<String>(KEY_DRAFT)?.let { if (drafts.current.value == null) drafts.set(draftFromJson(it)) }
        viewModelScope.launch {
            drafts.current.collect { savedState[KEY_DRAFT] = it?.toDraftJson() }
        }
    }

    fun setText(text: String) {
        savedState[KEY_TEXT] = text
    }

    fun clear() = setText("")

    fun useExample() = setText(EXAMPLE_TEXT)

    /** Converts the text when it is long enough: the example directly, other text through the Pipeline. */
    fun convert() {
        val text = text.value
        if (!canConvert(text)) return
        if (usesExampleShortcut(text)) {
            session.convert(text, id = newRecipeId())
            return
        }
        viewModelScope.launch {
            job.start(
                ImportRequest(
                    text = text,
                    lang = prefs.lang.first(),
                    appliances = prefs.appliances.first(),
                    kitchenMode = KitchenMode.fromKey(prefs.kitchenMode.first()),
                ),
            )
        }
    }

    /** Cancel on the Working stage: stops generation and returns to the paste stage with the text kept. */
    fun cancel() = job.cancel()

    /** The error stage's button: back to the paste stage with the text kept. */
    fun dismissError() = job.dismissError()

    /** Edit text: back to the paste stage, with the text kept. */
    fun editText() {
        session.discard()
        job.acknowledge()
    }

    fun selectTab(tab: ReviewTab) {
        savedState[KEY_TAB] = tab.name
    }

    /** Saves the recipe, then reports its id. One save at a time. */
    fun save(onSaved: (recipeId: String) -> Unit) {
        if (saving) return
        saving = true
        viewModelScope.launch {
            try {
                session.save()?.let {
                    job.acknowledge()
                    onSaved(it.id)
                }
            } finally {
                saving = false
            }
        }
    }

    /** Leaving the screen without saving drops the draft. */
    override fun onCleared() {
        job.leaveScreen(reviewShown)
    }
}
