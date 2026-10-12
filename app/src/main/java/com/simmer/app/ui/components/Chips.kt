package com.simmer.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.simmer.app.ui.theme.LocalSimmerColors
import com.simmer.app.ui.theme.SimmerTextStyle_BodySmMedium

/**
 * Filter chip for category / tag selection.
 *
 * - Full pill radius (CircleShape).
 * - Inactive: 2 dp border-default outline, transparent background.
 * - Active: surface-inverse fill with a checkmark glyph prefix, text-inverse text.
 * - Touch target >= 44 dp tall.
 */
@Composable
fun FilterChip(
    label: String,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSimmerColors.current

    val containerColor = if (selected) colors.surfaceInverse else colors.surfacePage
    val contentColor = if (selected) colors.textInverse else colors.textPrimary
    val borderColor = if (selected) colors.surfaceInverse else colors.borderDefault

    Surface(
        onClick = onToggle,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(width = 2.dp, color = borderColor),
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(CircleShape),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            if (selected) {
                // Simple check drawn with Canvas instead of an icon dependency
                CheckIcon(color = colors.textInverse)
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = SimmerTextStyle_BodySmMedium,
                color = contentColor,
            )
        }
    }
}

/** Lightweight inline check glyph drawn with Canvas. No icon library required. */
@Composable
private fun CheckIcon(
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.Canvas(
        modifier = modifier.size(14.dp),
    ) {
        val w = size.width
        val h = size.height
        // Draw a simple tick: short arm up-left, long arm to top-right
        val strokePx = (1.5).dp.toPx()
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.1f, h * 0.55f)
            lineTo(w * 0.4f, h * 0.80f)
            lineTo(w * 0.9f, h * 0.20f)
        }
        drawPath(
            path = path,
            color = color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = strokePx,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )
    }
}
