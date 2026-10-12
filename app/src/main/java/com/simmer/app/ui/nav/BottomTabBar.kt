package com.simmer.app.ui.nav

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.simmer.app.R
import com.simmer.app.ui.theme.LocalSimmerColors
import com.simmer.app.ui.theme.SimmerTextStyle_Micro

// ---------------------------------------------------------------------------
// Bottom tab bar
// ---------------------------------------------------------------------------

private data class TabItem(
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
    val route: Any,
    val routePrefix: String?,
)

private val TAB_ITEMS = listOf(
    TabItem(R.string.tab_recipes, R.drawable.ic_utensils, RecipesTab, RecipesTab::class.qualifiedName),
    TabItem(R.string.tab_shopping_list, R.drawable.ic_shopping_cart, ShoppingListTab, ShoppingListTab::class.qualifiedName),
    TabItem(R.string.tab_settings, R.drawable.ic_settings, SettingsTab, SettingsTab::class.qualifiedName),
)

/**
 * Material3 bottom navigation bar with three tabs.
 *
 * - Active tab: [SimmerColors.accent] (orange) icon + label color
 * - Inactive tabs: [SimmerColors.textTertiary] icon + label color
 * - Shopping list tab shows a [BadgedBox]; badge shows [badgeCount] and is hidden when it is 0
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
        modifier = Modifier.drawWithContent {
            drawContent()
            // 2 dp border-subtle rule on top, as in the design tab bar.
            drawLine(
                color = colors.borderSubtle,
                start = Offset(0f, 1.dp.toPx()),
                end = Offset(size.width, 1.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
            )
        },
    ) {
        TAB_ITEMS.forEach { tab ->
            val label = stringResource(tab.labelRes)
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
                                    val description = pluralStringResource(
                                        R.plurals.shopping_badge_description, badgeCount, badgeCount,
                                    )
                                    Badge(
                                        containerColor = colors.accent,
                                        contentColor = colors.accentFg,
                                        modifier = Modifier.semantics { contentDescription = description },
                                    ) {
                                        Text(badgeCount.toString())
                                    }
                                }
                            },
                        ) {
                            Icon(
                                painter = painterResource(tab.iconRes),
                                contentDescription = label,
                            )
                        }
                    } else {
                        Icon(
                            painter = painterResource(tab.iconRes),
                            contentDescription = label,
                        )
                    }
                },
                label = { Text(label, style = SimmerTextStyle_Micro) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.textAccent,
                    selectedTextColor = colors.textAccent,
                    unselectedIconColor = colors.textTertiary,
                    unselectedTextColor = colors.textTertiary,
                    indicatorColor = Color.Transparent,
                ),
            )
        }
    }
}
