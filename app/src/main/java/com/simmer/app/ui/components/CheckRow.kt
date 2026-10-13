package com.simmer.app.ui.components

import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.simmer.app.R
import com.simmer.app.ui.theme.LocalSimmerColors
import com.simmer.app.ui.theme.SimmerTextStyle_BodyMd
import com.simmer.app.ui.theme.SimmerTextStyle_BodySmMedium
import com.simmer.app.ui.theme.SimmerTextStyle_Caption

/** `duration-fast`. */
private const val CHECK_ANIM_MS = 150

private val CheckBoxShape = RoundedCornerShape(6.dp)
private val RowCardShape = RoundedCornerShape(12.dp)

/**
 * Check row for lists (shopping list, ingredient add-ins), after the design's ShoppingListItem.
 *
 * - Row height >= 56 dp, padding 12/16 dp, 16 dp gap after the box.
 * - Leading 24 dp rounded box (radius-sm, 2 dp border-strong; accent border and fill when checked)
 *   with a 16 dp check icon.
 * - Label (body-md; struck through and text-tertiary when checked), optional note below (caption, tertiary).
 * - Optional trailing amount (body-sm-medium, tertiary, tabular numerals).
 * - The whole row is one checkbox for accessibility.
 *
 * @param label Primary label text.
 * @param checked Whether the box is checked.
 * @param onCheckedChange Callback when the user toggles the row.
 * @param subtitle Optional note below the label.
 * @param animate Animate the box fill and label colour over `duration-fast` when [checked] changes.
 * @param trailing Optional short text (e.g. an amount) at the end of the row.
 */
@Composable
fun CheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: String? = null,
    animate: Boolean = false,
) {
    val colors = LocalSimmerColors.current
    val spec = if (animate) tween<Color>(CHECK_ANIM_MS) else snap()
    val fill by animateColorAsState(if (checked) colors.accent else Color.Transparent, spec, label = "fill")
    val outline by animateColorAsState(if (checked) colors.accent else colors.borderStrong, spec, label = "outline")
    val checkAlpha by animateFloatAsState(if (checked) 1f else 0f, if (animate) tween(CHECK_ANIM_MS) else snap(), label = "check")
    val labelColor by animateColorAsState(if (checked) colors.textTertiary else colors.textPrimary, spec, label = "label")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(24.dp)
                .clip(CheckBoxShape)
                .background(fill)
                .border(BorderStroke(2.dp, outline), CheckBoxShape),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_check_bold),
                contentDescription = null,
                tint = colors.accentFg,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { alpha = checkAlpha },
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = SimmerTextStyle_BodyMd,
                color = labelColor,
                textDecoration = if (checked) TextDecoration.LineThrough else null,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = SimmerTextStyle_Caption,
                    color = colors.textTertiary,
                )
            }
        }
        if (trailing != null) {
            Text(
                text = trailing,
                style = SimmerTextStyle_BodySmMedium.copy(fontFeatureSettings = "tnum"),
                color = colors.textTertiary,
            )
        }
    }
}

/** Bordered, rounded, clipped card (radius-card, 2 dp border-subtle, surface-card) that holds a group of rows. */
@Composable
fun RowCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalSimmerColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RowCardShape)
            .background(colors.surfaceCard)
            .border(BorderStroke(2.dp, colors.borderSubtle), RowCardShape),
        content = content,
    )
}

/** Divider between rows inside a [RowCard] (border-width, border-subtle). */
@Composable
fun RowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(LocalSimmerColors.current.borderSubtle),
    )
}
