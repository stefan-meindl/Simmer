package com.simmer.app.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.simmer.app.ui.theme.LocalSimmerColors

// ---------------------------------------------------------------------------
// Bottom tab bar
// ---------------------------------------------------------------------------

private data class TabItem(
    val label: String,
    val icon: ImageVector,
    val route: Any,
    val routePrefix: String?,
)

private val TAB_ITEMS = listOf(
    TabItem("Recipes", Icons.Outlined.RestaurantMenu, RecipesTab, RecipesTab::class.qualifiedName),
    TabItem("Shopping", Icons.Outlined.ShoppingCart, ShoppingListTab, ShoppingListTab::class.qualifiedName),
    TabItem("Settings", Icons.Outlined.Settings, SettingsTab, SettingsTab::class.qualifiedName),
)

/**
 * Material3 bottom navigation bar with three tabs.
 *
 * - Active tab: [SimmerColors.accent] (orange) icon + label color
 * - Inactive tabs: [SimmerColors.textTertiary] icon + label color
 * - Shopping list tab shows a [BadgedBox]; badge is hidden when [badgeCount] == 0
 */
@Composable
fun BottomTabBar(
    navController: NavHostController,
    badgeCount: Int,
) {
    val colors = LocalSimmerColors.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(
        containerColor = colors.surfacePage,
    ) {
        TAB_ITEMS.forEach { tab ->
            val isSelected = tab.routePrefix != null &&
                currentRoute != null &&
                currentRoute.startsWith(tab.routePrefix)

            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    navController.navigate(tab.route) {
                        // Pop up to the start destination of the graph to
                        // avoid building up a large back stack.
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    if (tab.route == ShoppingListTab) {
                        BadgedBox(
                            badge = {
                                if (badgeCount > 0) {
                                    Badge { Text(badgeCount.toString()) }
                                }
                            },
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                            )
                        }
                    } else {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                        )
                    }
                },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.accent,
                    selectedTextColor = colors.accent,
                    unselectedIconColor = colors.textTertiary,
                    unselectedTextColor = colors.textTertiary,
                    indicatorColor = colors.surfaceWarm,
                ),
            )
        }
    }
}
