package com.simmer.app.ui.nav

import androidx.navigation.NavHostController
import androidx.navigation.NavDestination.Companion.hasRoute

private fun NavHostController.isTab(route: Any): Boolean = when (route) {
    RecipesTab -> currentDestination?.hasRoute<RecipesTab>() == true
    ShoppingListTab -> currentDestination?.hasRoute<ShoppingListTab>() == true
    SettingsTab -> currentDestination?.hasRoute<SettingsTab>() == true
    else -> false
}

/** Add recipe is the one pushed screen that can sit under a tab (Settings opened by its Change link). */
private fun NavHostController.hasAddRecipeOnStack(): Boolean =
    runCatching { getBackStackEntry<AddRecipe>() }.isSuccess

/**
 * Switches to a bottom tab, as the tab bar does.
 *
 * A screen that is not a tab (Add recipe, with Settings opened on top of it) is abandoned, not saved:
 * `popUpTo { saveState }` would store it with the tab's state, and `restoreState` would bring it back
 * the next time that tab is tapped (the Recipes tab then showed Settings on top of Add recipe again).
 * Tapping the tab that is already shown does nothing, so Add recipe keeps its draft.
 */
fun NavHostController.navigateToTab(route: Any) {
    if (isTab(route)) return
    if (hasAddRecipeOnStack()) popBackStack(graph.startDestinationId, inclusive = false)
    navigate(route) {
        // Pop up to the start destination of the graph to
        // avoid building up a large back stack.
        popUpTo(graph.startDestinationId) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

/** Add recipe's "Change" link: Settings opens on top, so Back returns to Add recipe with its text. */
fun NavHostController.openSettingsOnTop() {
    navigate(SettingsTab) { launchSingleTop = true }
}

/** Key on the Recipes entry's saved state: set when a new recipe was saved, cleared once shown. */
const val RECIPE_SAVED_KEY = "recipeSaved"

/**
 * Add recipe's Save recipe: drops Add recipe and lands on the Recipes tab, which scrolls to its
 * top where the new card is listed first.
 */
fun NavHostController.finishAddRecipe() {
    getBackStackEntry<RecipesTab>().savedStateHandle[RECIPE_SAVED_KEY] = true
    popBackStack<RecipesTab>(inclusive = false)
}
