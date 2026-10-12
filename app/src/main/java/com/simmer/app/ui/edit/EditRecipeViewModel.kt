package com.simmer.app.ui.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simmer.app.data.RecipeEditor
import com.simmer.app.domain.RecipeDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

private const val KEY_DRAFT = "draft"
private const val KEY_OPEN_ING = "openIng"
private const val KEY_OPEN_STEP = "openStep"

sealed interface EditUi {
    data object Loading : EditUi
    data object NotFound : EditUi
    data class Content(val draft: RecipeDraft, val openIngredient: String?, val openStep: String?) : EditUi
}

/**
 * The form lives in [SavedStateHandle] (as JSON), so edits survive rotation and process death.
 * The route's `recipeId` is null when launched from the Add recipe review.
 */
@HiltViewModel
class EditRecipeViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val editor: RecipeEditor,
) : ViewModel() {

    private val recipeId: String? = savedState["recipeId"]

    private val draft = MutableStateFlow<RecipeDraft?>(null)
    private val loaded = MutableStateFlow(false)
    private val openIngredient = savedState.getStateFlow<String?>(KEY_OPEN_ING, null)
    private val openStep = savedState.getStateFlow<String?>(KEY_OPEN_STEP, null)

    val ui: StateFlow<EditUi> = combine(draft, loaded, openIngredient, openStep) { d, loaded, ing, step ->
        when {
            d != null -> EditUi.Content(d, ing, step)
            loaded -> EditUi.NotFound
            else -> EditUi.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditUi.Loading)

    private var busy = false

    init {
        viewModelScope.launch {
            val restored = savedState.get<String>(KEY_DRAFT)?.let { Json.decodeFromString(RecipeDraft.serializer(), it) }
            draft.value = restored ?: editor.open(recipeId)
            loaded.value = true
        }
        viewModelScope.launch {
            draft.collect { d ->
                if (d != null) savedState[KEY_DRAFT] = Json.encodeToString(RecipeDraft.serializer(), d)
            }
        }
    }

    fun edit(change: (RecipeDraft) -> RecipeDraft) = draft.update { it?.let(change) }

    fun toggleIngredient(key: String) {
        savedState[KEY_OPEN_ING] = if (openIngredient.value == key) null else key
    }

    fun toggleStep(key: String) {
        savedState[KEY_OPEN_STEP] = if (openStep.value == key) null else key
    }

    /** Appends a blank ingredient and opens it. */
    fun addIngredient() {
        edit { it.addIngredient() }
        savedState[KEY_OPEN_ING] = draft.value?.ingredients?.last()?.key
    }

    /** Appends a blank step to [phase] (`prep` or `cook`) and opens it. */
    fun addStep(phase: String) {
        edit { it.addStep(phase) }
        savedState[KEY_OPEN_STEP] = draft.value?.steps?.last()?.key
    }

    fun save(onDone: () -> Unit) = launchWrite(onDone) { d -> d.canSave.also { if (it) editor.save(d) } }

    fun restoreOriginal(onDone: () -> Unit) = launchWrite(onDone) { editor.restoreOriginal(checkNotNull(recipeId)); true }

    fun delete(onDone: () -> Unit) = launchWrite(onDone) { editor.delete(checkNotNull(recipeId)); true }

    /** Runs one write at a time, then reports completion. */
    private fun launchWrite(onDone: () -> Unit, action: suspend (RecipeDraft) -> Boolean) {
        val d = draft.value ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                if (action(d)) onDone()
            } finally {
                busy = false
            }
        }
    }
}
