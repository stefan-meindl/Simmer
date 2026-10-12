package com.simmer.app.ui.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.simmer.app.R
import com.simmer.app.domain.flowPhaseCount
import com.simmer.app.domain.flowPhaseNumber
import com.simmer.app.domain.ProgressSegment
import com.simmer.app.domain.SegmentState
import com.simmer.app.domain.progressSegments
import com.simmer.app.ui.components.EditAction
import com.simmer.app.ui.theme.LocalSimmerColors
import com.simmer.app.ui.theme.SimmerTextStyle_BodyMdMedium
import com.simmer.app.ui.theme.SimmerTextStyle_Caption
import com.simmer.app.ui.theme.SimmerTextStyle_Micro

@Composable
private fun phaseLabel(phase: Int): String = stringResource(
    when (phase) {
        1 -> R.string.flow_phase_servings
        2 -> R.string.flow_phase_preparation
        else -> R.string.flow_phase_cooking
    },
)

/**
 * Top bar shared by the recipe-flow phases: back button, recipe title,
 * "Step n of m · <phase>" (m is 2 when Phase 2 is skipped) caption and an Edit button.
 */
@Composable
fun FlowTopBar(
    title: String,
    phase: Int,
    skipsMethods: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSimmerColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clickable(role = Role.Button, onClick = onBack),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_left),
                contentDescription = stringResource(R.string.flow_back),
                tint = colors.textPrimary,
                modifier = Modifier.size(24.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = SimmerTextStyle_BodyMdMedium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.flow_step_caption, flowPhaseNumber(phase, skipsMethods), phaseLabel(phase), flowPhaseCount(skipsMethods)),
                style = SimmerTextStyle_Caption,
                color = colors.textTertiary,
            )
        }
        EditAction(text = stringResource(R.string.flow_edit), onClick = onEdit)
    }
}

/**
 * Progress indicator. Done segments (orange, secondary text) navigate
 * back via [onSegmentClick]; the current one is orange with accent text; future
 * ones are grey and not tappable.
 */
@Composable
fun FlowProgress(
    current: Int,
    skipsMethods: Boolean,
    onSegmentClick: (phase: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        progressSegments(current, skipsMethods).forEach { seg ->
            Segment(seg, onClick = { onSegmentClick(seg.phase) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun Segment(seg: ProgressSegment, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalSimmerColors.current
    val barColor = if (seg.state == SegmentState.Todo) colors.borderDefault else colors.accent
    val textColor = when (seg.state) {
        SegmentState.Done -> colors.textSecondary
        SegmentState.Current -> colors.textAccent
        SegmentState.Todo -> colors.textMuted
    }
    // The 8dp top padding sits inside the 44dp touch target, so the bar and label
    // keep their look while the whole cell is tappable.
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .then(if (seg.tappable) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(top = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(barColor, RoundedCornerShape(2.dp)),
        )
        Text(
            text = phaseLabel(seg.phase),
            style = SimmerTextStyle_Micro,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
