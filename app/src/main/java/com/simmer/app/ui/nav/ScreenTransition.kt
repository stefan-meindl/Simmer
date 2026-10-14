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

/** What a destination is, for choosing how to move to or from it. */
enum class ScreenKind { Tab, Stack }

/** Tab to tab fades; anything involving a stack screen slides, unless the user removed animations. */
fun screenTransition(from: ScreenKind, to: ScreenKind, reducedMotion: Boolean): ScreenTransition =
    if (reducedMotion || (from == ScreenKind.Tab && to == ScreenKind.Tab)) ScreenTransition.Fade else ScreenTransition.Slide

/** True when the system "remove animations" setting (animator duration scale 0) is on. */
fun Context.animationsRemoved(): Boolean =
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

private fun NavDestination.kind() = if (TABS.any { hasRoute(it::class) }) ScreenKind.Tab else ScreenKind.Stack

private const val TRANSITION_MS = 300
private const val PARALLAX_DIVISOR = 4 // the screen underneath moves 1/4 of its width

/** Enter/exit/popEnter/popExit specs for the whole [androidx.navigation.compose.NavHost]. */
class NavTransitions(private val reducedMotion: () -> Boolean) {
    private fun AnimatedContentTransitionScope<NavBackStackEntry>.slides() = screenTransition(
        from = initialState.destination.kind(),
        to = targetState.destination.kind(),
        reducedMotion = reducedMotion(),
    ) == ScreenTransition.Slide

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.enterWith(slideOffset: (Int) -> Int): EnterTransition =
        if (slides()) slideInHorizontally(tween(TRANSITION_MS), slideOffset) else fadeIn(tween(TRANSITION_MS))

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.exitWith(slideOffset: (Int) -> Int): ExitTransition =
        if (slides()) slideOutHorizontally(tween(TRANSITION_MS), slideOffset) else fadeOut(tween(TRANSITION_MS))

    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = { enterWith { it } }
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = { exitWith { -it / PARALLAX_DIVISOR } }
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = { enterWith { -it / PARALLAX_DIVISOR } }
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = { exitWith { it } }
}
