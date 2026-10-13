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
import com.simmer.app.domain.CookingSessions
import com.simmer.app.domain.CookingView
import com.simmer.app.domain.StepTimerStore
import com.simmer.app.domain.TimerState
import com.simmer.app.domain.cookingView
import com.simmer.app.domain.model.ResolvedIngredient
import com.simmer.app.domain.model.Selection
import com.simmer.app.domain.Phase1State
import com.simmer.app.domain.StepPlan
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val KEY_SERVINGS = "servings"
private const val KEY_SELECTION = "selection"
private const val KEY_DONE = "done"

/** Confirmation shown after "Add to list". */
data class AddedToast(val items: Int, val servings: Int)

sealed interface Phase1Ui {
    data object Loading : Phase1Ui
    data object NotFound : Phase1Ui
    data class Content(
        val state: Phase1State,
        val units: String,
        val spices: String,
        val lang: String,
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
        val methods: List<Method>,
        val picks: Map<String, String>,
        val plan: StepPlan,
    ) : Phase2Ui
}

sealed interface Phase3Ui {
    data object Loading : Phase3Ui
    data class Content(
        val recipe: Recipe,
        val servings: Int,
        val selection: Selection,
        val view: CookingView,
        /** Resolved ingredients by id, for the reference popovers. */
        val ingredients: Map<String, ResolvedIngredient>,
    ) : Phase3Ui
}

@HiltViewModel
class RecipeFlowViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val recipes: RecipeRepository,
    private val shopping: ShoppingRepository,
    private val progress: CookingProgressStore,
    private val stepTimers: StepTimerStore,
    private val sessions: CookingSessions,
    private val prefs: PrefsRepository,
) : ViewModel() {

    private val recipeId: String = checkNotNull(savedState["recipeId"])

    private val phase1 = MutableStateFlow<Phase1State?>(null)
    private val toast = MutableStateFlow<AddedToast?>(null)
    private val notFound = MutableStateFlow(false)

    private val amountPrefs = combine(prefs.units, prefs.spices, prefs.lang) { units, spices, lang ->
        Triple(units, spices, lang)
    }

    val ui: StateFlow<Phase1Ui> = combine(
        phase1, toast, notFound, amountPrefs,
    ) { state, toast, missing, (units, spices, lang) ->
        when {
            state != null -> Phase1Ui.Content(
                state = state,
                units = units,
                spices = spices,
                lang = lang,
                // The toast only holds while the list still matches the current choice.
                toast = toast.takeIf { state.isOnList },
            )
            missing -> Phase1Ui.NotFound
            else -> Phase1Ui.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Phase1Ui.Loading)

    /** Phase 2 view: every Method with all its options, with the step plan for the current choices. */
    val phase2: StateFlow<Phase2Ui> = combine(
        phase1, prefs.units,
    ) { state, units ->
        if (state == null) Phase2Ui.Loading else {
            Phase2Ui.Content(
                recipe = state.recipe,
                servings = state.servings,
                units = units,
                methods = state.recipe.methods,
                picks = state.selection.choice,
                plan = stepPlan(state.recipe, state.servings, state.selection),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Phase2Ui.Loading)

    /** Phase 3 view: every Step with its done state, Needs and ingredient references. */
    val phase3: StateFlow<Phase3Ui> = combine(
        phase1, prefs.units, prefs.spices, prefs.lang, progress.observe(recipeId),
    ) { state, units, spices, lang, done ->
        if (state == null) Phase3Ui.Loading else {
            val resolved = state.resolved(units, spices, lang)
            Phase3Ui.Content(
                recipe = state.recipe,
                servings = state.servings,
                selection = state.selection,
                view = cookingView(resolved.steps, done),
                ingredients = resolved.ingredients.associateBy { it.id },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Phase3Ui.Loading)

    /** Servings and selection saved before process death, if any. */
    private fun savedDraft(): PlannedChoice? {
        val servings = savedState.get<Int>(KEY_SERVINGS) ?: return null
        val selection = savedState.get<String>(KEY_SELECTION)?.let(::decodeSelection) ?: return null
        return PlannedChoice(servings, selection)
    }

    init {
        // Done steps saved with this flow's servings and Selection; they win over stale memory.
        savedState.get<ArrayList<String>>(KEY_DONE)?.let { progress.restore(recipeId, it) }
        viewModelScope.launch {
            progress.observe(recipeId).collect { savedState[KEY_DONE] = ArrayList(it) }
        }
        viewModelScope.launch {
            phase1.filterNotNull().collect {
                savedState[KEY_SERVINGS] = it.servings
                savedState[KEY_SELECTION] = encodeSelection(it.selection)
                // Persisted with the marked Steps, so a recipe left mid-cooking resumes with the same plan.
                progress.setChoice(recipeId, PlannedChoice(it.servings, it.selection))
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
                        current == null -> Phase1State.initial(recipe, planned, draft = savedDraft() ?: sessions.resumeChoice(recipe))
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

    /** This recipe's StepTimers by step id. Running timers continue after the flow is left. */
    val timers: StateFlow<Map<String, TimerState>> = stepTimers.timers
        .map { all ->
            all.filterKeys { it.startsWith("$recipeId/") }.mapKeys { it.key.removePrefix("$recipeId/") }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun toggleTimer(stepId: String, title: String, durationSecs: Int) =
        stepTimers.toggle("$recipeId/$stepId", title, durationSecs)

    /** Called by visible running timers, so they finish even without the foreground service. */
    fun tickTimers() = stepTimers.tick()

    fun resetTimer(stepId: String) = stepTimers.reset("$recipeId/$stepId")

    fun markStepDone(stepId: String) = progress.markDone(recipeId, stepId)

    fun undoStep(stepId: String) = progress.undo(recipeId, stepId)

    /** Whether leaving Phase 3 backwards should ask first: this recipe is in progress. */
    fun needsLeaveConfirm(): Boolean {
        val state = phase1.value ?: return false
        return sessions.isInProgress(state.recipe, PlannedChoice(state.servings, state.selection))
    }

    /** Stop cooking: cancels this recipe's timers and clears its Cooking Progress; servings and Selection stay. */
    fun stopCooking() = sessions.stop(recipeId)

    fun dismissToast() {
        toast.value = null
    }

    fun addToList() {
        val content = ui.value as? Phase1Ui.Content ?: return
        val state = content.state
        val entries = state.shoppingEntries(content.units, content.spices, content.lang)
        val choice = PlannedChoice(state.servings, state.selection)
        viewModelScope.launch {
            shopping.addRecipeToList(state.recipe.id, entries, choice)
            phase1.update { it?.copy(added = choice) }
            toast.value = AddedToast(entries.size, state.servings)
        }
    }
}
