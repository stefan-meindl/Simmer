package com.simmer.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.simmer.app.ui.theme.LocalSimmerColors
import com.simmer.app.ui.theme.SimmerTextStyle_Caption

/**
 * Visual variant for [Badge].
 */
enum class BadgeVariant {
    /** Orange fill (#FF6B1A), black text. */
    Primary,
    /** Surface-warm fill, text-primary text. Soft neutral look. */
    Cream,
    /** Surface-inverse fill, text-inverse text. High-contrast dark look. */
    Dark,
}

/**
 * Pill-shaped badge/tag label.
 *
 * - Full pill shape (CircleShape).
 * - Three variants: Primary (orange), Cream (warm), Dark (inverse).
 * - Uses caption text style.
 *
 * @param label The badge text.
 * @param variant Visual style variant; defaults to [BadgeVariant.Primary].
 */
@Composable
fun Badge(
    label: String,
    modifier: Modifier = Modifier,
    variant: BadgeVariant = BadgeVariant.Primary,
) {
    val colors = LocalSimmerColors.current

    val (containerColor, textColor) = when (variant) {
        BadgeVariant.Primary -> colors.accent to colors.accentFg
        BadgeVariant.Cream -> colors.surfaceWarm to colors.textPrimary
        BadgeVariant.Dark -> colors.surfaceInverse to colors.textInverse
    }

    Surface(
        shape = CircleShape,
        color = containerColor,
        contentColor = textColor,
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = SimmerTextStyle_Caption,
            color = textColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
