package com.simmer.app.domain

import com.simmer.app.domain.model.Recipe

/** Tag filter chips on the Recipes overview, in display order. */
val FILTER_TAGS = listOf("Vegetarian", "Quick", "Spicy", "One-pot", "Make-ahead")

/** What the user typed and picked on the Recipes overview. [category] null means "All". */
data class RecipeFilters(
    val query: String = "",
    val category: String? = null,
    val tags: Set<String> = emptySet(),
)

/** A Planned Recipe with the label of each non-default swap and each add-in that is on. */
data class PlannedEntry(
    val recipe: Recipe,
    val choice: PlannedChoice,
    val variationLabels: List<String>,
)

/** Everything the Recipes overview shows for a set of recipes, Planned Recipes and filters. */
data class RecipesOverview(
    /** "All" is implicit; these are the distinct categories present, in recipe order. */
    val categories: List<String>,
    /** The active category, or null for All (also when the chosen one is no longer present). */
    val category: String?,
    val recipes: List<Recipe>,
    val planned: List<PlannedEntry>,
    val filtersActive: Boolean,
)

/**
 * Filters [all] by title or ingredient name ([RecipeFilters.query]), category and tags, which all AND.
 * The Planned section ignores the filters.
 */
fun recipesOverview(
    all: List<Recipe>,
    planned: Map<String, PlannedChoice>,
    filters: RecipeFilters,
): RecipesOverview {
    val categories = all.map { it.category }.distinct()
    val category = filters.category?.takeIf { it in categories }
    val query = filters.query.trim()
    val visible = all.filter { r ->
        (category == null || r.category == category) &&
            r.tags.containsAll(filters.tags) &&
            (query.isEmpty() ||
                r.title.contains(query, ignoreCase = true) ||
                r.ingredients.any { it.name.contains(query, ignoreCase = true) })
    }
    return RecipesOverview(
        categories = categories,
        category = category,
        recipes = visible,
        planned = all.mapNotNull { r ->
            planned[r.id]?.let { PlannedEntry(r, it, selectionLabels(r, it.selection)) }
        },
        filtersActive = query.isNotEmpty() || category != null || filters.tags.isNotEmpty(),
    )
}
