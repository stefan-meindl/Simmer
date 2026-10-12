package com.simmer.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

// ---------------------------------------------------------------------------
// Material3 color schemes derived from Simmer tokens
// ---------------------------------------------------------------------------

private val SimmerLightColorScheme = lightColorScheme(
    primary = LightSimmerColors.accent,
    onPrimary = LightSimmerColors.accentFg,
    primaryContainer = LightSimmerColors.surfaceWarm,
    onPrimaryContainer = LightSimmerColors.textPrimary,
    secondary = LightSimmerColors.surfaceInverse,
    onSecondary = LightSimmerColors.textInverse,
    secondaryContainer = LightSimmerColors.surfaceWarmStrong,
    onSecondaryContainer = LightSimmerColors.textPrimary,
    background = LightSimmerColors.surfacePage,
    onBackground = LightSimmerColors.textPrimary,
    surface = LightSimmerColors.surfaceCard,
    onSurface = LightSimmerColors.textPrimary,
    surfaceVariant = LightSimmerColors.surfaceSubtle,
    onSurfaceVariant = LightSimmerColors.textSecondary,
    outline = LightSimmerColors.borderDefault,
    outlineVariant = LightSimmerColors.borderSubtle,
    error = LightSimmerColors.accentPressed,
    onError = LightSimmerColors.textInverse,
    inverseSurface = LightSimmerColors.surfaceInverse,
    inverseOnSurface = LightSimmerColors.textInverse,
    inversePrimary = LightSimmerColors.accentPressed,
    scrim = LightSimmerColors.surfaceInverse,
)

private val SimmerDarkColorScheme = darkColorScheme(
    primary = DarkSimmerColors.accent,
    onPrimary = DarkSimmerColors.accentFg,
    primaryContainer = DarkSimmerColors.surfaceWarm,
    onPrimaryContainer = DarkSimmerColors.textPrimary,
    secondary = DarkSimmerColors.surfaceInverse,
    onSecondary = DarkSimmerColors.textInverse,
    secondaryContainer = DarkSimmerColors.surfaceWarmStrong,
    onSecondaryContainer = DarkSimmerColors.textPrimary,
    background = DarkSimmerColors.surfacePage,
    onBackground = DarkSimmerColors.textPrimary,
    surface = DarkSimmerColors.surfaceCard,
    onSurface = DarkSimmerColors.textPrimary,
    surfaceVariant = DarkSimmerColors.surfaceSubtle,
    onSurfaceVariant = DarkSimmerColors.textSecondary,
    outline = DarkSimmerColors.borderDefault,
    outlineVariant = DarkSimmerColors.borderSubtle,
    error = DarkSimmerColors.accentPressed,
    onError = DarkSimmerColors.textInverse,
    inverseSurface = DarkSimmerColors.surfaceInverse,
    inverseOnSurface = DarkSimmerColors.textInverse,
    inversePrimary = DarkSimmerColors.accentPressed,
    scrim = DarkSimmerColors.surfacePage,
)

// ---------------------------------------------------------------------------
// SimmerTheme
// ---------------------------------------------------------------------------

/**
 * The top-level Simmer design system theme.
 *
 * Provides [LocalSimmerColors] for semantic token access and a [MaterialTheme]
 * with Simmer-mapped color scheme and typography. Dynamic color is intentionally
 * not supported — the Simmer palette is fixed.
 */
@Composable
fun SimmerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val simmerColors = if (darkTheme) DarkSimmerColors else LightSimmerColors
    val materialColorScheme = if (darkTheme) SimmerDarkColorScheme else SimmerLightColorScheme

    CompositionLocalProvider(LocalSimmerColors provides simmerColors) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = Typography,
            content = content,
        )
    }
}
