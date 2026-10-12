package com.simmer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.simmer.app.ui.theme.DarkSimmerColors
import com.simmer.app.ui.theme.LocalSimmerColors

private val CardShape = RoundedCornerShape(12.dp)
private val HeroCardShape = RoundedCornerShape(16.dp)

/**
 * Draws a card-style outline via [drawBehind] — never affects layout size.
 *
 * - Inactive: 2 dp [defaultBorderColor].
 * - Selected: single 3 dp [accentColor] stroke (full orange, no double ring).
 */
private fun Modifier.optionCardBorder(
    selected: Boolean,
    defaultBorderColor: Color,
    accentColor: Color,
): Modifier = this.drawBehind {
    val cornerRadius = CornerRadius(12.dp.toPx())
    val (color, width) = if (selected) accentColor to 3.dp.toPx() else defaultBorderColor to 2.dp.toPx()
    drawRoundRect(color = color, cornerRadius = cornerRadius, style = Stroke(width = width))
}

// ---------------------------------------------------------------------------
// OptionCard
// ---------------------------------------------------------------------------

/**
 * Radio-style option card.
 *
 * - 12 dp radius.
 * - Inactive: 2 dp border-default outline.
 * - Selected: 3 dp accent (orange) outline.
 *
 * @param content The card body composable.
 */
@Composable
fun OptionCard(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = LocalSimmerColors.current

    Box(
        modifier = modifier
            .clip(CardShape)
            .clickable(onClick = onClick)
            .background(color = colors.surfaceCard, shape = CardShape)
            .optionCardBorder(
                selected = selected,
                defaultBorderColor = colors.borderDefault,
                accentColor = colors.accent,
            ),
    ) {
        content()
    }
}

// ---------------------------------------------------------------------------
// GradientHeroCard
// ---------------------------------------------------------------------------

/** Light gradient stops: 135°, #F9E2CF → #F4CCAE at 60% → #EBB48F */
private val LightGradientColors = listOf(
    Color(0xFFF9E2CF),
    Color(0xFFF4CCAE),
    Color(0xFFEBB48F),
)
private val LightGradientStops = floatArrayOf(0f, 0.6f, 1f)

/** Dark gradient stops: 135°, #1B0E04 → #2D1606 at 60% → #57270A */
private val DarkGradientColors = listOf(
    Color(0xFF1B0E04),
    Color(0xFF2D1606),
    Color(0xFF57270A),
)
private val DarkGradientStops = floatArrayOf(0f, 0.6f, 1f)

/**
 * Hero card with a warm 135-degree gradient background.
 *
 * Light mode: #F9E2CF → #F4CCAE → #EBB48F
 * Dark mode (OLED): #1B0E04 → #2D1606 → #57270A
 *
 * @param content The card body composable.
 */
@Composable
fun GradientHeroCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = LocalSimmerColors.current
    val isDark = colors === DarkSimmerColors
    val gradientColors = if (isDark) DarkGradientColors else LightGradientColors
    val gradientStops = if (isDark) DarkGradientStops else LightGradientStops

    Box(
        modifier = modifier
            .clip(HeroCardShape)
            .background(
                brush = Brush.linearGradient(
                    colorStops = Array(gradientColors.size) { i ->
                        gradientStops[i] to gradientColors[i]
                    },
                    // 135° = top-left to bottom-right bias
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                ),
                shape = HeroCardShape,
            ),
    ) {
        content()
    }
}
