package com.simmer.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.simmer.app.ui.nav.SettingsTab
import com.simmer.app.ui.nav.SimmerNavHost
import com.simmer.app.ui.nav.navigateToTab
import com.simmer.app.ui.nav.openAddRecipe
import com.simmer.app.ui.theme.SimmerTheme

/**
 * Root composable for the Simmer app.
 *
 * Responsibilities:
 * - Observes theme preference and resolves the effective dark-mode value
 *   (including "system" delegation to [isSystemInDarkTheme]).
 * - Wraps the entire UI in [SimmerTheme].
 * - Hosts the [Scaffold] around the nav host (each tab screen brings its own [com.simmer.app.ui.nav.BottomTabBar]).
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
        Scaffold(
            // No insets and no bottom bar here: each screen pads for the system bars itself, and each tab screen
            // brings its own tab bar so it moves with the screen's transition (see SimmerNavHost).
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { innerPadding ->
            SimmerNavHost(
                navController = navController,
                isDarkTheme = effectiveDark,
                onThemeToggle = { viewModel.setTheme(!effectiveDark) },
                badgeCount = badgeCount,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}
