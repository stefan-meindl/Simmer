package com.simmer.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.simmer.app.ui.theme.LocalSimmerColors
import com.simmer.app.ui.theme.SimmerTextStyle_Button

private val ButtonShape = RoundedCornerShape(8.dp)
private val ButtonMinHeight = 44.dp
private val ButtonPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)

// ---------------------------------------------------------------------------
// PrimaryButton — orange fill, black label
// ---------------------------------------------------------------------------

/**
 * Filled orange button. Label on orange is always black per the Simmer spec.
 * Touch target is at least 44 dp tall.
 *
 * @param icon Optional leading composable (icon, etc.).
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
) {
    val colors = LocalSimmerColors.current
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accent,
            contentColor = colors.accentFg,
            disabledContainerColor = colors.disabledBg,
            disabledContentColor = colors.disabledFg,
        ),
        contentPadding = ButtonPadding,
        modifier = modifier.defaultMinSize(minHeight = ButtonMinHeight),
    ) {
        ButtonContent(text = text, icon = icon)
    }
}

// ---------------------------------------------------------------------------
// DarkButton — surface-inverse fill, text-inverse label
// ---------------------------------------------------------------------------

/**
 * Filled dark button that inverts in dark theme (OLED black → near-white surface).
 */
@Composable
fun DarkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
) {
    val colors = LocalSimmerColors.current
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.surfaceInverse,
            contentColor = colors.textInverse,
            disabledContainerColor = colors.disabledBg,
            disabledContentColor = colors.disabledFg,
        ),
        contentPadding = ButtonPadding,
        modifier = modifier.defaultMinSize(minHeight = ButtonMinHeight),
    ) {
        ButtonContent(text = text, icon = icon)
    }
}

// ---------------------------------------------------------------------------
// SecondaryButton — transparent, 2 dp border-strong outline
// ---------------------------------------------------------------------------

/**
 * Outlined button with a 2 dp border-strong outline.
 * Background is transparent at rest; presses show a subtle surface fill
 * via the ripple / pressed state.
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
) {
    val colors = LocalSimmerColors.current
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        border = BorderStroke(
            width = 2.dp,
            color = if (enabled) colors.borderStrong else colors.disabledBg,
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = colors.textPrimary,
            disabledContentColor = colors.disabledFg,
        ),
        contentPadding = ButtonPadding,
        modifier = modifier.defaultMinSize(minHeight = ButtonMinHeight),
    ) {
        ButtonContent(text = text, icon = icon)
    }
}

// ---------------------------------------------------------------------------
// CreamButton — surface-warm fill, border-warm outline
// ---------------------------------------------------------------------------

/**
 * Warm cream-toned button for secondary actions with a soft feel.
 */
@Composable
fun CreamButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
) {
    val colors = LocalSimmerColors.current
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        border = BorderStroke(
            width = 2.dp,
            color = if (enabled) colors.borderWarm else colors.disabledBg,
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = colors.surfaceWarm,
            contentColor = colors.textPrimary,
            disabledContainerColor = colors.disabledBg,
            disabledContentColor = colors.disabledFg,
        ),
        contentPadding = ButtonPadding,
        modifier = modifier.defaultMinSize(minHeight = ButtonMinHeight),
    ) {
        ButtonContent(text = text, icon = icon)
    }
}

// ---------------------------------------------------------------------------
// LinkButton — no background, no border, underlined text-link color
// ---------------------------------------------------------------------------

/**
 * Inline link-style button. No background or border; text is underlined and
 * uses the text-link token color.
 */
@Composable
fun LinkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalSimmerColors.current
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(
            contentColor = colors.textLink,
            disabledContentColor = colors.disabledFg,
        ),
        modifier = modifier.defaultMinSize(minHeight = ButtonMinHeight),
    ) {
        Text(
            text = text,
            style = SimmerTextStyle_Button.copy(
                textDecoration = TextDecoration.Underline,
                color = if (enabled) colors.textLink else colors.disabledFg,
            ),
        )
    }
}

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

@Composable
private fun ButtonContent(
    text: String,
    icon: @Composable (() -> Unit)?,
) {
    if (icon != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            icon()
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = text, style = SimmerTextStyle_Button)
        }
    } else {
        Text(text = text, style = SimmerTextStyle_Button)
    }
}
