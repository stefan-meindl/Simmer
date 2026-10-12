package com.simmer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.simmer.app.ui.theme.LocalSimmerColors
import com.simmer.app.ui.theme.SimmerTextStyle_BodyMd
import com.simmer.app.ui.theme.SimmerTextStyle_Caption

private val InputShape = RoundedCornerShape(8.dp)

/**
 * Draws the field outline behind the composable content.
 *
 * Focus ring technique (no layout shift):
 * - Outer container is always the same size.
 * - At rest: 2 dp border in border-strong color.
 * - Focused: 2 dp border-strong + additional 1 dp inset drawn via drawBehind,
 *   rendered as a 3 dp visual weight without changing the layout bounding box.
 */
private fun Modifier.simmerFieldBorder(
    focused: Boolean,
    isError: Boolean,
    borderColor: Color,
    accentColor: Color,
    errorColor: Color,
): Modifier = this.drawBehind {
    val cornerRadius = CornerRadius(8.dp.toPx())
    when {
        isError -> {
            // 2 dp error border
            drawRoundRect(
                color = errorColor,
                cornerRadius = cornerRadius,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
        focused -> {
            // 2 dp base border
            drawRoundRect(
                color = borderColor,
                cornerRadius = cornerRadius,
                style = Stroke(width = 2.dp.toPx()),
            )
            // 1 dp inset orange ring (total visual weight = 3 dp)
            val inset = 2.dp.toPx()
            drawRoundRect(
                color = accentColor,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = size.copy(
                    width = size.width - inset * 2,
                    height = size.height - inset * 2,
                ),
                cornerRadius = CornerRadius((8.dp.toPx() - inset).coerceAtLeast(0f)),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
        else -> {
            // 2 dp rest border
            drawRoundRect(
                color = borderColor,
                cornerRadius = cornerRadius,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// SimmerTextField
// ---------------------------------------------------------------------------

/**
 * Single-line styled text field matching Simmer design spec.
 *
 * - 2 dp border-strong outline at rest.
 * - 3 dp orange focus ring drawn as an inset overlay — no layout shift.
 * - 8 dp corner radius.
 *
 * @param trailingIcon Optional composable to show at the end of the field.
 */
@Composable
fun SimmerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    trailingIcon: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val colors = LocalSimmerColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                style = SimmerTextStyle_Caption,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = SimmerTextStyle_BodyMd.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accent),
            interactionSource = interactionSource,
            keyboardOptions = keyboardOptions,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 44.dp)
                        .background(
                            color = if (enabled) colors.surfacePage else colors.disabledBg,
                            shape = InputShape,
                        )
                        .simmerFieldBorder(
                            focused = focused,
                            isError = isError,
                            borderColor = colors.borderStrong,
                            accentColor = colors.accent,
                            errorColor = colors.accentPressed,
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            text = placeholder,
                            style = SimmerTextStyle_BodyMd,
                            color = colors.textMuted,
                        )
                    }
                    innerTextField()
                    if (trailingIcon != null) {
                        Box(modifier = Modifier.align(androidx.compose.ui.Alignment.CenterEnd)) {
                            trailingIcon()
                        }
                    }
                }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// SimmerTextArea
// ---------------------------------------------------------------------------

/**
 * Multi-line text area. Same visual style as [SimmerTextField] but allows
 * multiple lines and does not enforce single-line behaviour.
 */
@Composable
fun SimmerTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    minLines: Int = 3,
) {
    val colors = LocalSimmerColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                style = SimmerTextStyle_Caption,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            minLines = minLines,
            textStyle = SimmerTextStyle_BodyMd.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accent),
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = if (enabled) colors.surfacePage else colors.disabledBg,
                            shape = InputShape,
                        )
                        .simmerFieldBorder(
                            focused = focused,
                            isError = false,
                            borderColor = colors.borderStrong,
                            accentColor = colors.accent,
                            errorColor = colors.accentPressed,
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            text = placeholder,
                            style = SimmerTextStyle_BodyMd,
                            color = colors.textMuted,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}
