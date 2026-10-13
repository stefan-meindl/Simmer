package com.simmer.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.simmer.app.ui.nav.AddRecipe
import com.simmer.app.ui.nav.BottomTabBar
import com.simmer.app.ui.nav.EditRecipe
import com.simmer.app.ui.nav.RecipeFlow
import com.simmer.app.ui.nav.RecipeFlowPhase2
import com.simmer.app.ui.nav.RecipeFlowPhase3
import com.simmer.app.ui.nav.SettingsTab
import com.simmer.app.ui.nav.SimmerNavHost
import com.simmer.app.ui.nav.navigateToTab
import com.simmer.app.ui.nav.openAddRecipe
import com.simmer.app.ui.nav.YourKitchen
import com.simmer.app.ui.theme.SimmerTheme

/**
 * Root composable for the Simmer app.
 *
 * Responsibilities:
 * - Observes theme preference and resolves the effective dark-mode value
 *   (including "system" delegation to [isSystemInDarkTheme]).
 * - Wraps the entire UI in [SimmerTheme].
 * - Hosts the [Scaffold] with a conditional [BottomTabBar].
 * - Delegates navigation to [SimmerNavHost].
 */
@Composable
fun MainScreen(
    openSettings: Boolean = false,
    onSettingsOpened: () -> Unit = {},
    openAddRecipe: Boolean = false,
    onAddRecipeOpened: () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel(),
) {
    val themePreference by viewModel.themePreference.collectAsStateWithLifecycle()
    val badgeCount by viewModel.shoppingListBadgeCount.collectAsStateWithLifecycle()
    val isSystemDark = isSystemInDarkTheme()

    // Resolve the effective dark-theme value from the stored preference.
    val effectiveDark = when (themePreference) {
        "dark" -> true
        "light" -> false
        else -> isSystemDark // "system" or any unrecognised value
    }

    SimmerTheme(darkTheme = effectiveDark) {
        val navController = rememberNavController()
        // A notification (the model download) asked for Settings.
        LaunchedEffect(openSettings) {
            if (openSettings) {
                navController.navigateToTab(SettingsTab)
                onSettingsOpened()
            }
        }
        // A Pipeline notification asked for Add recipe.
        LaunchedEffect(openAddRecipe) {
            if (openAddRecipe) {
                navController.openAddRecipe()
                onAddRecipeOpened()
            }
        }
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        // Hide the tab bar on screens that belong to sub-flows.
        val hideTabBar = currentDestination?.shouldHideTabBar() ?: false

        Scaffold(
            bottomBar = {
                if (!hideTabBar) {
                    BottomTabBar(
                        navController = navController,
                        badgeCount = badgeCount,
                    )
                }
            },
        ) { innerPadding ->
            SimmerNavHost(
                navController = navController,
                isDarkTheme = effectiveDark,
                onThemeToggle = { viewModel.setTheme(!effectiveDark) },
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

private val TAB_HIDDEN_ROUTE_PREFIXES = listOf(
    RecipeFlow::class.qualifiedName,
    RecipeFlowPhase2::class.qualifiedName,
    RecipeFlowPhase3::class.qualifiedName,
    AddRecipe::class.qualifiedName,
    EditRecipe::class.qualifiedName,
    YourKitchen::class.qualifiedName,
)

/**
 * Returns true for destinations that should hide the bottom tab bar.
 *
 * Uses the route string which is based on the fully-qualified class name
 * of the route object/data class.
 */
private fun NavDestination.shouldHideTabBar(): Boolean {
    val routeStr = route ?: return false
    return TAB_HIDDEN_ROUTE_PREFIXES.any { prefix ->
        prefix != null && routeStr.startsWith(prefix)
    }
}
