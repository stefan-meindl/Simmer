package com.simmer.app.ui.nav

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.simmer.app.R
import com.simmer.app.ui.theme.LocalSimmerColors
import com.simmer.app.ui.theme.SimmerTextStyle_BodyMdMedium

/** A non-tab screen a tab was opened from; the tab then shows a Back banner naming it. */
enum class BackOrigin(@StringRes val label: Int) {
    RecipeCreator(R.string.back_to_recipe_creator),
    Recipe(R.string.back_to_recipe),
}

/**
 * The origin a tab's Back banner names, or null for no banner. [currentRoute] must be a tab and
 * [previousRoute] the route of the entry underneath it. One branch per origin.
 */
fun backOriginFor(currentRoute: String?, previousRoute: String?): BackOrigin? {
    if (currentRoute !in TAB_ROUTES) return null
    // Routes of data classes carry their arguments ("...RecipeFlow/{recipeId}"); the name ends before that.
    return when (previousRoute?.substringBefore('/')?.substringBefore('?')) {
        AddRecipe::class.qualifiedName -> BackOrigin.RecipeCreator
        RecipeFlow::class.qualifiedName -> BackOrigin.Recipe
        else -> null
    }
}

private val TAB_ROUTES = listOf(RecipesTab, ShoppingListTab, SettingsTab).map { it::class.qualifiedName }

/**
 * A tab screen with the pinned Back banner above it when a [BackOrigin] sits underneath the tab.
 * The banner pops to the origin, as device Back does; the origin keeps its state.
 */
@Composable
fun BackBannerHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val entry by navController.currentBackStackEntryAsState()
    val origin = backOriginFor(entry?.destination?.route, navController.previousBackStackEntry?.destination?.route)
    Column(modifier) {
        if (origin != null) BackBanner(origin, onClick = { navController.popBackStack() })
        // The banner already sits below the status bar, so the screen must not pad for it again.
        val below = if (origin != null) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier
        Box(Modifier.weight(1f).then(below)) { content() }
    }
}

@Composable
private fun BackBanner(origin: BackOrigin, onClick: () -> Unit) {
    val colors = LocalSimmerColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceWarm)
            .windowInsetsPadding(WindowInsets.statusBars)
            .clickable(role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 16.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_arrow_left),
            contentDescription = null,
            tint = colors.textLink,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(origin.label),
            style = SimmerTextStyle_BodyMdMedium,
            color = colors.textLink,
        )
    }
}
