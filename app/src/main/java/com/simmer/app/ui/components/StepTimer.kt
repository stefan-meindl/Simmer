package com.simmer.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.simmer.app.ui.theme.LocalSimmerColors
import com.simmer.app.ui.theme.SimmerTextStyle_Button

private val TimerShape = RoundedCornerShape(8.dp)

/**
 * Step timer button — idle state only.
 *
 * Displays an orange-filled button with a timer icon glyph and a duration label.
 * Active / running states are out of scope for this ticket.
 *
 * - 8 dp radius (not a pill per Simmer spec).
 * - Min height 44 dp touch target.
 * - Label: button text style; icon on the left.
 *
 * @param durationText Human-readable duration (e.g. "5 min", "1:30").
 * @param onClick Called when the user taps the timer.
 */
@Composable
fun StepTimer(
    durationText: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalSimmerColors.current

    Surface(
        onClick = onClick,
        shape = TimerShape,
        color = colors.accent,
        contentColor = colors.accentFg,
        modifier = modifier.defaultMinSize(minHeight = 44.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            TimerIcon(color = colors.accentFg)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = durationText,
                style = SimmerTextStyle_Button,
                color = colors.accentFg,
            )
        }
    }
}

/** Simple timer/clock glyph drawn with Canvas. No icon library required. */
@Composable
private fun TimerIcon(
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.Canvas(
        modifier = modifier.size(18.dp),
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = size.minDimension / 2f - 1.dp.toPx()
        val strokePx = 1.5f.dp.toPx()

        // Outer circle
        drawCircle(
            color = color,
            radius = radius,
            style = Stroke(width = strokePx),
        )
        // Clock hands: 12 o'clock → ~2 o'clock position
        val handPath = Path().apply {
            // minute hand pointing up
            moveTo(cx, cy)
            lineTo(cx, cy - radius * 0.6f)
            // hour hand pointing to ~2 o'clock
            moveTo(cx, cy)
            lineTo(cx + radius * 0.35f, cy - radius * 0.35f)
        }
        drawPath(
            path = handPath,
            color = color,
            style = Stroke(
                width = strokePx,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            ),
        )
    }
}
