package com.simmer.app.ui.nav

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute

enum class ScreenTransition { Fade, Slide }

/** Tab to tab fades; anything involving a stack screen slides, unless the user removed animations. */
fun screenTransition(fromTab: Boolean, toTab: Boolean, reducedMotion: Boolean): ScreenTransition =
    if (reducedMotion || (fromTab && toTab)) ScreenTransition.Fade else ScreenTransition.Slide

/** True when the system "remove animations" setting (animator duration scale 0) is on. */
fun Context.animationsRemoved(): Boolean =
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

private fun NavDestination.isTab() = hasRoute<RecipesTab>() || hasRoute<ShoppingListTab>() || hasRoute<SettingsTab>()

private const val TRANSITION_MS = 300
private const val PARALLAX = 4 // the screen underneath moves a quarter of its width

/** Enter/exit/popEnter/popExit specs for the whole [androidx.navigation.compose.NavHost]. */
class NavTransitions(private val reducedMotion: () -> Boolean) {
    private fun AnimatedContentTransitionScope<NavBackStackEntry>.slides() = screenTransition(
        fromTab = initialState.destination.isTab(),
        toTab = targetState.destination.isTab(),
        reducedMotion = reducedMotion(),
    ) == ScreenTransition.Slide

    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (slides()) slideInHorizontally(tween(TRANSITION_MS)) { it } else fadeIn(tween(TRANSITION_MS))
    }
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (slides()) slideOutHorizontally(tween(TRANSITION_MS)) { -it / PARALLAX } else fadeOut(tween(TRANSITION_MS))
    }
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (slides()) slideInHorizontally(tween(TRANSITION_MS)) { -it / PARALLAX } else fadeIn(tween(TRANSITION_MS))
    }
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (slides()) slideOutHorizontally(tween(TRANSITION_MS)) { it } else fadeOut(tween(TRANSITION_MS))
    }
}
