package com.simmer.app.ui.flow

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simmer.app.data.PrefsRepository
import com.simmer.app.data.RecipeRepository
import com.simmer.app.data.ShoppingRepository
import com.simmer.app.domain.Phase1State
import com.simmer.app.domain.PlannedChoice
import com.simmer.app.domain.stepServings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Confirmation shown after "Add to list". */
data class AddedToast(val items: Int, val servings: Int)

sealed interface Phase1Ui {
    data object Loading : Phase1Ui
    data object NotFound : Phase1Ui
    data class Content(
        val state: Phase1State,
        val units: String,
        val spices: String,
        /** Non-null only while the toast should be visible. */
        val toast: AddedToast?,
    ) : Phase1Ui
}

@HiltViewModel
class RecipeFlowViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val recipes: RecipeRepository,
    private val shopping: ShoppingRepository,
    prefs: PrefsRepository,
) : ViewModel() {

    private val recipeId: String = checkNotNull(savedState["recipeId"])

    private val phase1 = MutableStateFlow<Phase1State?>(null)
    private val toast = MutableStateFlow<AddedToast?>(null)
    private val notFound = MutableStateFlow(false)

    val ui: StateFlow<Phase1Ui> = combine(
        phase1, toast, notFound, prefs.units, prefs.spices,
    ) { state, toast, missing, units, spices ->
        when {
            state != null -> Phase1Ui.Content(
                state = state,
                units = units,
                spices = spices,
                // The toast only holds while the list still matches the current choice.
                toast = toast.takeIf { state.isOnList },
            )
            missing -> Phase1Ui.NotFound
            else -> Phase1Ui.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Phase1Ui.Loading)

    init {
        viewModelScope.launch {
            val planned = shopping.getPlanned(recipeId)
            recipes.recipes.collect { all ->
                val recipe = all.firstOrNull { it.id == recipeId }
                notFound.value = recipe == null
                phase1.update { current ->
                    when {
                        recipe == null -> null
                        current == null -> Phase1State.initial(recipe, planned)
                        else -> current.copy(recipe = recipe)
                    }
                }
            }
        }
    }

    fun stepServings(delta: Int) =
        phase1.update { it?.withServings(stepServings(it.servings, delta)) }

    fun selectSwap(variationId: String, value: String) =
        phase1.update { it?.withSwap(variationId, value) }

    fun setAddIn(variationId: String, on: Boolean) =
        phase1.update { it?.withAddIn(variationId, on) }

    fun dismissToast() {
        toast.value = null
    }

    fun addToList() {
        val content = ui.value as? Phase1Ui.Content ?: return
        val state = content.state
        val entries = state.shoppingEntries(content.units, content.spices)
        val choice = PlannedChoice(state.servings, state.selection)
        viewModelScope.launch {
            shopping.addRecipeToList(state.recipe.id, entries, choice)
            phase1.update { it?.copy(added = choice) }
            toast.value = AddedToast(entries.size, state.servings)
        }
    }
}
