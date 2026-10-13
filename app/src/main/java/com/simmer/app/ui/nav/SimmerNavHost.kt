package com.simmer.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.simmer.app.ui.edit.EditRecipeScreen
import com.simmer.app.ui.flow.RecipeFlowPhase1Screen
import com.simmer.app.ui.flow.RecipeFlowPhase2Screen
import com.simmer.app.ui.flow.RecipeFlowPhase3Screen
import com.simmer.app.ui.recipes.RecipesScreen
import com.simmer.app.ui.settings.SettingsScreen
import com.simmer.app.ui.add.AddRecipeScreen
import com.simmer.app.ui.shopping.ShoppingListScreen
import com.simmer.app.ui.kitchen.YourKitchenScreen

/**
 * Navigation host for the Simmer app.
 *
 * All destinations are registered here. The start destination is [RecipesTab].
 *
 * @param navController the nav controller shared with [BottomTabBar] and callers
 * @param isDarkTheme   current resolved dark-theme state (for theme toggle icon)
 * @param onThemeToggle callback to toggle the theme
 * @param modifier      applied to the [NavHost]
 */
@Composable
fun SimmerNavHost(
    navController: NavHostController,
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackBannerHost(navController, modifier) {
        NavHost(
            navController = navController,
            startDestination = RecipesTab,
        ) {
            // ----------------------------------------------------------------
            // Bottom tab destinations
            // ----------------------------------------------------------------

            composable<RecipesTab> { backStackEntry ->
                val saved by backStackEntry.savedStateHandle
                    .getStateFlow(RECIPE_SAVED_KEY, false).collectAsStateWithLifecycle()
                RecipesScreen(
                    recipeSaved = saved,
                    onRecipeSavedShown = { backStackEntry.savedStateHandle[RECIPE_SAVED_KEY] = false },
                    isDarkTheme = isDarkTheme,
                    onThemeToggle = onThemeToggle,
                    onAddRecipe = { navController.navigate(AddRecipe) },
                    onOpenRecipe = { navController.navigate(RecipeFlow(it)) },
                    onContinueCooking = { navController.continueCooking(it) },
                )
            }

            composable<ShoppingListTab> {
                ShoppingListScreen()
            }

            composable<SettingsTab> { backStackEntry ->
                val scrollToCreator by backStackEntry.savedStateHandle
                    .getStateFlow(SCROLL_TO_CREATOR_KEY, false).collectAsStateWithLifecycle()
                SettingsScreen(
                    onEditKitchen = { navController.navigate(YourKitchen) },
                    scrollToCreator = scrollToCreator,
                    onScrolledToCreator = { backStackEntry.savedStateHandle[SCROLL_TO_CREATOR_KEY] = false },
                )
            }

            // ----------------------------------------------------------------
            // Recipe flow (hides tab bar)
            // ----------------------------------------------------------------

            composable<RecipeFlow> { backStackEntry ->
                val route: RecipeFlow = backStackEntry.toRoute()
                RecipeFlowPhase1Screen(
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(EditRecipe(route.recipeId)) },
                    // The flow stays underneath, so Shopping list shows "Back to recipe".
                    onViewList = { navController.openShoppingFromFlow() },
                    onPreparation = { navController.navigate(RecipeFlowPhase2(route.recipeId)) },
                    // No Method to pick: Phase 3 sits directly on Phase 1, so Back returns here.
                    onStartCooking = { navController.navigate(RecipeFlowPhase3(route.recipeId)) },
                )
            }

            composable<RecipeFlowPhase2> { backStackEntry ->
                val route: RecipeFlowPhase2 = backStackEntry.toRoute()
                // Phases 1 and 2 edit one selection, so share the phase 1 entry's ViewModel.
                val flowEntry = remember(backStackEntry) { navController.getBackStackEntry<RecipeFlow>() }
                RecipeFlowPhase2Screen(
                    viewModel = hiltViewModel(flowEntry),
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(EditRecipe(route.recipeId)) },
                    onStartCooking = { navController.navigate(RecipeFlowPhase3(route.recipeId)) },
                    onNothingToPick = { navController.popBackStack() },
                    // Phase 3 is reached only via Start cooking.
                    onPhaseClick = { phase -> if (phase == 1) navController.popBackStack() },
                )
            }

            composable<RecipeFlowPhase3> { backStackEntry ->
                val route: RecipeFlowPhase3 = backStackEntry.toRoute()
                val flowEntry = remember(backStackEntry) { navController.getBackStackEntry<RecipeFlow>() }
                RecipeFlowPhase3Screen(
                    viewModel = hiltViewModel(flowEntry),
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(EditRecipe(route.recipeId)) },
                    onBackToRecipes = {
                        navController.navigate(RecipesTab) {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    },
                    onPhaseClick = { phase ->
                        when (phase) {
                            1 -> navController.popBackStack<RecipeFlow>(inclusive = false)
                            2 -> navController.popBackStack()
                        }
                    },
                )
            }

            // ----------------------------------------------------------------
            // Add / Edit Recipe (hides tab bar)
            // ----------------------------------------------------------------

            composable<AddRecipe> {
                AddRecipeScreen(
                    onBack = { navController.popBackStack() },
                    // Settings opens on top (with a Back banner), so back returns here with the pasted text.
                    onChangeKitchen = { navController.openSettingsOnTop() },
                    onEditDetails = { navController.navigate(EditRecipe()) },
                    // The new recipe is listed first on the Recipes tab, which scrolls there.
                    onSaved = { navController.finishAddRecipe() },
                )
            }

            composable<EditRecipe> { backStackEntry ->
                // Back to the screen that opened Edit: the recipe flow at the same phase, or the Add recipe review.
                EditRecipeScreen(
                    onClose = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                    onDeleted = {
                        // The recipe is gone, so the flow underneath would have nothing to show.
                        navController.navigate(RecipesTab) {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    },
                )
            }

            // ----------------------------------------------------------------
            // Your Kitchen (hides tab bar; back -> Settings)
            // ----------------------------------------------------------------

            composable<YourKitchen> {
                // Settings is always underneath (also when it was opened on top of Add recipe), so Back and Done pop to it.
                YourKitchenScreen(onDone = { navController.popBackStack() })
            }
        }
    }
}
