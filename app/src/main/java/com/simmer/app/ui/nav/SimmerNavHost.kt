package com.simmer.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.simmer.app.ui.flow.RecipeFlowPhase1Screen
import com.simmer.app.ui.flow.RecipeFlowPhase2Screen
import com.simmer.app.ui.flow.RecipeFlowPhase3Screen
import com.simmer.app.ui.screens.AddRecipePlaceholder
import com.simmer.app.ui.screens.EditRecipePlaceholder
import com.simmer.app.ui.screens.RecipesPlaceholder
import com.simmer.app.ui.screens.SettingsPlaceholder
import com.simmer.app.ui.screens.ShoppingListPlaceholder
import com.simmer.app.ui.screens.YourKitchenPlaceholder

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
    NavHost(
        navController = navController,
        startDestination = RecipesTab,
        modifier = modifier,
    ) {
        // ----------------------------------------------------------------
        // Bottom tab destinations
        // ----------------------------------------------------------------

        composable<RecipesTab> {
            // THROWAWAY: temporary entry points into the recipe flow. Never merge.
            androidx.compose.foundation.layout.Box {
                RecipesPlaceholder(
                    isDarkTheme = isDarkTheme,
                    onThemeToggle = onThemeToggle,
                )
                androidx.compose.foundation.layout.Column(Modifier.align(androidx.compose.ui.Alignment.BottomCenter)) {
                    listOf("chilli", "curry", "tagliatelle").forEach { id ->
                        androidx.compose.material3.Button(onClick = { navController.navigate(RecipeFlow(id)) }) {
                            androidx.compose.material3.Text("TEST flow: $id")
                        }
                    }
                }
            }
        }

        composable<ShoppingListTab> {
            ShoppingListPlaceholder()
        }

        composable<SettingsTab> {
            SettingsPlaceholder()
        }

        // ----------------------------------------------------------------
        // Recipe flow (hides tab bar)
        // ----------------------------------------------------------------

        composable<RecipeFlow> { backStackEntry ->
            val route: RecipeFlow = backStackEntry.toRoute()
            RecipeFlowPhase1Screen(
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(EditRecipe(route.recipeId)) },
                onViewList = {
                    navController.navigate(ShoppingListTab) {
                        popUpTo(navController.graph.startDestinationId)
                        launchSingleTop = true
                    }
                },
                onPreparation = { navController.navigate(RecipeFlowPhase2(route.recipeId)) },
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
            AddRecipePlaceholder(
                onBack = { navController.popBackStack() },
            )
        }

        composable<EditRecipe> { backStackEntry ->
            val route: EditRecipe = backStackEntry.toRoute()
            EditRecipePlaceholder(
                recipeId = route.recipeId,
                onBack = { navController.popBackStack() },
            )
        }

        // ----------------------------------------------------------------
        // Your Kitchen (hides tab bar; back -> Settings)
        // ----------------------------------------------------------------

        composable<YourKitchen> {
            YourKitchenPlaceholder(
                onBack = {
                    // Back from YourKitchen returns to Settings tab.
                    navController.navigate(SettingsTab) {
                        popUpTo(navController.graph.startDestinationId)
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}
