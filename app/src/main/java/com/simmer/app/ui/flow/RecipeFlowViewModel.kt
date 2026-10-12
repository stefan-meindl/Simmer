package com.simmer.app.ui.flow

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simmer.app.data.PrefsRepository
import com.simmer.app.data.RecipeRepository
import com.simmer.app.data.ShoppingRepository
import com.simmer.app.data.decodeSelection
import com.simmer.app.data.encodeSelection
import com.simmer.app.domain.CookingProgressStore
import com.simmer.app.domain.Phase1State
import com.simmer.app.domain.StepPlan
import com.simmer.app.domain.availableMethods
import com.simmer.app.domain.coerceMethodChoices
import com.simmer.app.domain.model.Method
import com.simmer.app.domain.model.Recipe
import com.simmer.app.domain.stepPlan
import com.simmer.app.domain.PlannedChoice
import com.simmer.app.domain.stepServings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val KEY_SERVINGS = "servings"
private const val KEY_SELECTION = "selection"

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

sealed interface Phase2Ui {
    data object Loading : Phase2Ui
    data class Content(
        val recipe: Recipe,
        val servings: Int,
        val units: String,
        /** Methods limited to the user's kitchen. */
        val methods: List<Method>,
        val picks: Map<String, String>,
        val plan: StepPlan,
    ) : Phase2Ui
}

@HiltViewModel
class RecipeFlowViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val recipes: RecipeRepository,
    private val shopping: ShoppingRepository,
    private val progress: CookingProgressStore,
    private val prefs: PrefsRepository,
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

    /** Phase 2 view: Methods filtered by the user's appliances, with the step plan for the current choices. */
    val phase2: StateFlow<Phase2Ui> = combine(
        phase1, prefs.units, prefs.appliances, prefs.kitchenMode,
    ) { state, units, appliances, mode ->
        if (state == null) Phase2Ui.Loading else {
            val methods = availableMethods(state.recipe, appliances, mode)
            // The init collector keeps state.selection coerced to the offered options.
            Phase2Ui.Content(
                recipe = state.recipe,
                servings = state.servings,
                units = units,
                methods = methods,
                picks = state.selection.choice,
                plan = stepPlan(state.recipe, state.servings, state.selection),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Phase2Ui.Loading)

    /** Servings and selection saved before process death, if any. */
    private fun savedDraft(): PlannedChoice? {
        val servings = savedState.get<Int>(KEY_SERVINGS) ?: return null
        val selection = savedState.get<String>(KEY_SELECTION)?.let(::decodeSelection) ?: return null
        return PlannedChoice(servings, selection)
    }

    init {
        // Keep the stored Method picks to options the kitchen still offers (the default pick may be hidden).
        viewModelScope.launch {
            combine(phase1.filterNotNull(), prefs.appliances, prefs.kitchenMode) { state, appliances, mode ->
                coerceMethodChoices(state.selection, availableMethods(state.recipe, appliances, mode))
            }.collect { coerced ->
                if (phase1.value?.selection != coerced) edit { it.copy(selection = coerced) }
            }
        }
        viewModelScope.launch {
            phase1.filterNotNull().collect {
                savedState[KEY_SERVINGS] = it.servings
                savedState[KEY_SELECTION] = encodeSelection(it.selection)
            }
        }
        viewModelScope.launch {
            val planned = shopping.getPlanned(recipeId)
            recipes.recipes.collect { all ->
                val recipe = all.firstOrNull { it.id == recipeId }
                notFound.value = recipe == null
                phase1.update { current ->
                    when {
                        recipe == null -> null
                        current == null -> Phase1State.initial(recipe, planned, draft = savedDraft())
                        else -> current.copy(recipe = recipe)
                    }
                }
            }
        }
    }

    /** Applies a change to the flow state; cooking progress made on the old plan is dropped. */
    private fun edit(change: (Phase1State) -> Phase1State) {
        phase1.update { old ->
            if (old == null) return@update null
            val new = change(old)
            if (old.changesCookingPlan(new)) progress.reset(recipeId)
            new
        }
    }

    fun stepServings(delta: Int) = edit { it.withServings(stepServings(it.servings, delta)) }

    fun selectSwap(variationId: String, value: String) = edit { it.withSwap(variationId, value) }

    fun selectMethod(methodId: String, value: String) = edit { it.withSwap(methodId, value) }

    fun setAddIn(variationId: String, on: Boolean) = edit { it.withAddIn(variationId, on) }

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
