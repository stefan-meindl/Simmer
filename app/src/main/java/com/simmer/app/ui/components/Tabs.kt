package com.simmer.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.simmer.app.ui.theme.LocalSimmerColors
import com.simmer.app.ui.theme.SimmerTextStyle_BodySmMedium

/**
 * Underline-style tab row.
 *
 * - Full-width 2 dp bottom rule in border-default color.
 * - A 3 dp round-ended orange indicator bar sits under the active tab.
 * - Inactive tabs show tertiary text color; active tab shows primary text color.
 * - Tab text uses body-sm-medium style.
 *
 * @param tabs List of tab label strings.
 * @param selectedIndex Index of the currently selected tab.
 * @param onTabSelected Callback when the user taps a tab.
 */
@Composable
fun UnderlineTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSimmerColors.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                // 2 dp full-width bottom rule
                val y = size.height - 1.dp.toPx()
                drawLine(
                    color = colors.borderDefault,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 2.dp.toPx(),
                )
            },
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { index, label ->
                val isSelected = index == selectedIndex
                val tabInteractionSource = remember { MutableInteractionSource() }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            onClick = { onTabSelected(index) },
                            indication = null,
                            interactionSource = tabInteractionSource,
                        )
                        .drawBehind {
                            if (isSelected) {
                                // 3 dp round-ended orange indicator at the very bottom
                                val indicatorHeight = 3.dp.toPx()
                                val top = size.height - indicatorHeight
                                drawRoundRect(
                                    color = colors.accent,
                                    topLeft = Offset(0f, top),
                                    size = size.copy(height = indicatorHeight),
                                    cornerRadius = CornerRadius(indicatorHeight / 2f),
                                )
                            }
                        },
                ) {
                    Text(
                        text = label,
                        style = SimmerTextStyle_BodySmMedium,
                        color = if (isSelected) colors.textPrimary else colors.textTertiary,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}
