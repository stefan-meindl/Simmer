package com.simmer.app.ui.nav

import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// Bottom tab destinations
// ---------------------------------------------------------------------------

@Serializable
object RecipesTab

@Serializable
object ShoppingListTab

@Serializable
object SettingsTab

// ---------------------------------------------------------------------------
// Destinations that hide the bottom tab bar
// ---------------------------------------------------------------------------

/** Recipe flow – Phase 1 (ingredient overview, etc.) */
@Serializable
data class RecipeFlow(val recipeId: String)

/** Recipe flow – Phase 2 (step-by-step cooking) */
@Serializable
data class RecipeFlowPhase2(val recipeId: String)

/** Recipe flow – Phase 3 (finish / rating) */
@Serializable
data class RecipeFlowPhase3(val recipeId: String)

/** Add recipe screen. Hides tab bar. */
@Serializable
object AddRecipe

/**
 * Edit recipe screen. Hides tab bar.
 * [recipeId] is null when launched from AddRecipe.
 */
@Serializable
data class EditRecipe(val recipeId: String? = null)

/**
 * Your kitchen settings screen. Hides tab bar.
 * Back press should return to Settings tab.
 */
@Serializable
object YourKitchen
