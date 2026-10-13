package com.simmer.app.ui.recipes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simmer.app.data.RecipeRepository
import com.simmer.app.data.ShoppingRepository
import com.simmer.app.domain.CookingSessions
import com.simmer.app.domain.InProgressEntry
import com.simmer.app.domain.RecipeFilters
import com.simmer.app.domain.RecipesOverview
import com.simmer.app.domain.recipesOverview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private const val KEY_QUERY = "query"
private const val KEY_CATEGORY = "category"
private const val KEY_TAGS = "tags"

sealed interface RecipesUi {
    data object Loading : RecipesUi
    data class Content(
        val overview: RecipesOverview,
        /** Recipes left mid-cooking, for the "Cooking now" cards; they ignore search and filters. */
        val cooking: List<InProgressEntry>,
    ) : RecipesUi
}

/** Filters live in [SavedStateHandle], so they survive rotation and process death. */
@HiltViewModel
class RecipesViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    recipes: RecipeRepository,
    shopping: ShoppingRepository,
    sessions: CookingSessions,
) : ViewModel() {

    /** Straight from saved state so typing never lags behind the filtered list. */
    val query: StateFlow<String> = savedState.getStateFlow(KEY_QUERY, "")

    private val category = savedState.getStateFlow<String?>(KEY_CATEGORY, null)
    private val tags = savedState.getStateFlow(KEY_TAGS, ArrayList<String>())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val cooking = recipes.recipes.flatMapLatest { sessions.observe(it) }

    private val filters = combine(query, category, tags) { query, category, tags ->
        RecipeFilters(query, category, tags.toSet())
    }

    val ui: StateFlow<RecipesUi> = combine(
        recipes.recipes, shopping.planned, cooking, filters,
    ) { all, planned, cooking, filters ->
        RecipesUi.Content(recipesOverview(all, planned, filters), cooking)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipesUi.Loading)

    fun setQuery(query: String) {
        savedState[KEY_QUERY] = query
    }

    fun selectCategory(category: String?) {
        savedState[KEY_CATEGORY] = category
    }

    fun toggleTag(tag: String) {
        val current = savedState.get<ArrayList<String>>(KEY_TAGS).orEmpty()
        savedState[KEY_TAGS] = ArrayList(if (tag in current) current - tag else current + tag)
    }

    fun clearFilters() {
        savedState[KEY_QUERY] = ""
        savedState[KEY_CATEGORY] = null
        savedState[KEY_TAGS] = ArrayList<String>()
    }
}
