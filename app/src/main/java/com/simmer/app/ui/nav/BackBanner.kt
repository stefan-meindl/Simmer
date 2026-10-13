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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
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

private val TAB_ROUTES = TABS.map { it::class.qualifiedName }

/** The origin the current tab's Back banner names, or null for no banner. */
fun NavHostController.backOrigin(): BackOrigin? =
    backOriginFor(currentDestination?.route, previousBackStackEntry?.destination?.route)

/**
 * The origin of the tab [entry], from the entry that sits directly underneath it, or null for no banner.
 * Only the shown entry has a known entry underneath; an entry is composed first while it is shown.
 */
private fun NavHostController.backOriginOf(entry: NavBackStackEntry): BackOrigin? {
    val below = if (currentBackStackEntry == entry) previousBackStackEntry else null
    return backOriginFor(entry.destination.route, below?.destination?.route)
}

/**
 * Frames one tab screen: the pinned Back banner sits above it when a [BackOrigin] sits underneath the
 * tab [entry], and [bottomBar] below it. The banner pops to the origin, as device Back does; the origin
 * keeps its state.
 *
 * Every tab entry draws its own frame inside the nav host's transition, so the banner and the tab bar fade
 * with the screen instead of switching instantly beside it. The origin is captured once per entry, so the
 * outgoing screen keeps its own banner while it fades out.
 */
@Composable
fun BackBannerFrame(
    navController: NavHostController,
    entry: NavBackStackEntry,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val origin = remember(entry) { navController.backOriginOf(entry) }
    Column(modifier) {
        if (origin != null) BackBanner(origin, onClick = { navController.popBackStack() })
        // The banner already sits below the status bar, so the screen must not pad for it again.
        val below = if (origin != null) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier
        Box(Modifier.weight(1f).then(below)) { content() }
        bottomBar()
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
