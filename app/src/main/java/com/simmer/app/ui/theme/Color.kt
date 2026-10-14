package com.simmer.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Base palette
// ---------------------------------------------------------------------------

// Neutrals
val Neutral0 = Color(0xFF000000)
val Neutral5 = Color(0xFF0A0A0A)
val Neutral10 = Color(0xFF1F1F1F)
val Neutral15 = Color(0xFF262626)
val Neutral20 = Color(0xFF232323)
val Neutral25 = Color(0xFF2E2E2E)
val Neutral30 = Color(0xFF3D3D3D)
val Neutral38 = Color(0xFF474747)
val Neutral42 = Color(0xFF4A4A4A)
val Neutral58 = Color(0xFF5E5E5E)
val Neutral65 = Color(0xFF6A6A6A)
val Neutral78 = Color(0xFF7D7D7D)
val Neutral80 = Color(0xFF8A8A8A)
val Neutral88 = Color(0xFFA8A8A8)
val Neutral93 = Color(0xFFC2C2C2)
val Neutral95 = Color(0xFFC7C7C7)
val Neutral97 = Color(0xFFE5E5E5)
val Neutral98 = Color(0xFFEDEDED)
val Neutral99 = Color(0xFF9E9E9E)
val Neutral100 = Color(0xFFFFFFFF)
val NeutralFA = Color(0xFFFAFAFA)
val NeutralE3 = Color(0xFFE3E3E3)

// Destructive (the one exception to "orange is the only accent", see CODING_STANDARDS.md)
/** Background revealed while swiping a card away to delete it; white icon on top, readable in light and dark. */
val SwipeDeleteRed = Color(0xFFC62828)

// Orange accent
val Orange400 = Color(0xFFFF6B1A)
val Orange500 = Color(0xFFE8590C)
val OrangeDark = Color(0xFFC2410C)
val OrangeLight = Color(0xFFFF7A33)

// Warm
val WarmFBF = Color(0xFFFBF5EF)
val WarmF6 = Color(0xFFF6E6DC)
val WarmE5D = Color(0xFFE5D0C4)
val WarmDark11 = Color(0xFF110B07)
val WarmDark26 = Color(0xFF26170C)
val WarmDark3D = Color(0xFF3D2716)

// ---------------------------------------------------------------------------
// SimmerColors — semantic tokens (CompositionLocal)
// ---------------------------------------------------------------------------

@Immutable
data class SimmerColors(
    val surfacePage: Color,
    val surfaceSubtle: Color,
    val surfaceCard: Color,
    val surfaceWarm: Color,
    val surfaceWarmStrong: Color,
    val surfaceInverse: Color,
    val textPrimary: Color,
    val textHero: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textMuted: Color,
    val textLink: Color,
    val textAccent: Color,
    val textInverse: Color,
    val borderSubtle: Color,
    val borderDefault: Color,
    val borderStrong: Color,
    val borderWarm: Color,
    val accent: Color,
    val accentPressed: Color,
    val accentFg: Color,
    val disabledBg: Color,
    val disabledFg: Color,
)

val LightSimmerColors = SimmerColors(
    surfacePage = Color(0xFFFFFFFF),
    surfaceSubtle = Color(0xFFFAFAFA),
    surfaceCard = Color(0xFFFFFFFF),
    surfaceWarm = Color(0xFFFBF5EF),
    surfaceWarmStrong = Color(0xFFF6E6DC),
    surfaceInverse = Color(0xFF1F1F1F),
    textPrimary = Color(0xFF1F1F1F),
    textHero = Color(0xFF3D3D3D),
    textSecondary = Color(0xFF4A4A4A),
    textTertiary = Color(0xFF6A6A6A),
    textMuted = Color(0xFF8A8A8A),
    textLink = Color(0xFFC2410C),
    textAccent = Color(0xFFC2410C),
    textInverse = Color(0xFFFFFFFF),
    borderSubtle = Color(0xFFEDEDED),
    borderDefault = Color(0xFFE5E5E5),
    borderStrong = Color(0xFFC7C7C7),
    borderWarm = Color(0xFFE5D0C4),
    accent = Color(0xFFFF6B1A),
    accentPressed = Color(0xFFE8590C),
    accentFg = Color(0xFF000000),
    disabledBg = Color(0xFFE5E5E5),
    disabledFg = Color(0xFFA8A8A8),
)

val DarkSimmerColors = SimmerColors(
    surfacePage = Color(0xFF000000),
    surfaceSubtle = Color(0xFF0A0A0A),
    surfaceCard = Color(0xFF000000),
    surfaceWarm = Color(0xFF110B07),
    surfaceWarmStrong = Color(0xFF26170C),
    surfaceInverse = Color(0xFFFAFAFA),
    textPrimary = Color(0xFFFAFAFA),
    textHero = Color(0xFFE3E3E3),
    textSecondary = Color(0xFFC2C2C2),
    textTertiary = Color(0xFF9E9E9E),
    textMuted = Color(0xFF7D7D7D),
    textLink = Color(0xFFFF7A33),
    textAccent = Color(0xFFFF7A33),
    textInverse = Color(0xFF000000),
    borderSubtle = Color(0xFF232323),
    borderDefault = Color(0xFF2E2E2E),
    borderStrong = Color(0xFF474747),
    borderWarm = Color(0xFF3D2716),
    accent = Color(0xFFFF6B1A),
    accentPressed = Color(0xFFE8590C),
    accentFg = Color(0xFF000000),
    disabledBg = Color(0xFF2E2E2E),
    disabledFg = Color(0xFF5E5E5E),
)

// CompositionLocal provider — default to light
/** True while [SimmerTheme] is in its dark (OLED) variant; for choices colour tokens can't express, like dropping shadows. */
val LocalSimmerDarkTheme = staticCompositionLocalOf { false }

val LocalSimmerColors =staticCompositionLocalOf { LightSimmerColors }
