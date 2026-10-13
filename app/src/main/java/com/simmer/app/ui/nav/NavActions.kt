package com.simmer.app.ui.nav

import androidx.navigation.NavHostController
import androidx.navigation.NavDestination.Companion.hasRoute
import com.simmer.app.domain.hasSelectableMethod
import com.simmer.app.domain.model.Recipe

private fun NavHostController.isTab(route: Any): Boolean = currentDestination?.hasRoute(route::class) == true

private fun NavHostController.hasAddRecipeOnStack(): Boolean =
    runCatching { getBackStackEntry<AddRecipe>() }.isSuccess

/**
 * Switches to a bottom tab, as the tab bar does.
 *
 * A screen that is not a tab (Add recipe or the recipe flow, with a tab opened on top of it) is abandoned, not saved:
 * `popUpTo { saveState }` would store it with the tab's state, and `restoreState` would bring it back
 * the next time that tab is tapped (the Recipes tab then showed Settings on top of Add recipe again).
 * The same holds for the recipe flow under the Shopping list tab ("View list").
 * Tapping the tab that is already shown does nothing, so Add recipe and the flow keep their state.
 */
fun NavHostController.navigateToTab(route: Any) {
    if (isTab(route)) return
    if (backOrigin() != null) popBackStack(graph.startDestinationId, inclusive = false)
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

/** Key on the Settings entry's saved state: set when Settings should scroll to "Recipe creator", cleared once done. */
const val SCROLL_TO_CREATOR_KEY = "scrollToCreator"

/**
 * Add recipe's pencil: Settings opens on top and scrolls to its "Recipe creator" section, so Back or the
 * banner returns to Add recipe with its text.
 */
fun NavHostController.openSettingsOnTop() {
    navigate(SettingsTab) { launchSingleTop = true }
    getBackStackEntry<SettingsTab>().savedStateHandle[SCROLL_TO_CREATOR_KEY] = true
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

/** A Pipeline notification was tapped: show Add recipe (its Working, Review or error stage), unless it is already shown. */
fun NavHostController.openAddRecipe() {
    if (currentDestination?.hasRoute<AddRecipe>() == true) return
    if (hasAddRecipeOnStack()) popBackStack<AddRecipe>(inclusive = false) else navigate(AddRecipe) { launchSingleTop = true }
}

/**
 * Recipes tab "Cooking now" Continue: opens Flow Phase 3 with the earlier phases underneath, so
 * Back and the progress segments work as if the user had walked there. The flow ViewModel on the
 * first entry restores the servings and Selection the Steps were marked under.
 */
fun NavHostController.continueCooking(recipe: Recipe) {
    navigate(RecipeFlow(recipe.id))
    if (hasSelectableMethod(recipe)) navigate(RecipeFlowPhase2(recipe.id))
    navigate(RecipeFlowPhase3(recipe.id))
}

/** Recipe flow's "View list": Shopping list opens on top of the flow, which stays underneath. */
fun NavHostController.openShoppingFromFlow() {
    navigate(ShoppingListTab) { launchSingleTop = true }
}
