package com.simmer.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simmer.app.R
import com.simmer.app.domain.TimerState
import com.simmer.app.domain.TimerStatus
import com.simmer.app.domain.formatClock
import com.simmer.app.domain.formatDuration
import com.simmer.app.ui.theme.LocalSimmerColors
import com.simmer.app.ui.theme.SimmerTextStyle_Button
import kotlinx.coroutines.delay

private val TimerShape = RoundedCornerShape(8.dp)

/**
 * Step timer, stateless. One button that cycles idle, running, paused, done.
 *
 * - Idle: orange fill, timer icon, [idleText] ("5 min").
 * - Running: orange fill, pause icon, [clockText] and a thin progress bar inside.
 * - Paused: outline, play icon, [clockText] and the progress bar.
 * - Done: inverse fill, check icon, "Done".
 * - Paused and done show a 44dp reset button to the left; the timer button itself stays put.
 *
 * @param progress 0..1 share of the duration that has elapsed.
 * @param onToggle Start, pause, resume, or (when done) reset.
 */
@Composable
fun StepTimer(
    status: TimerStatus,
    idleText: String,
    clockText: String,
    progress: Float,
    onToggle: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        if (status == TimerStatus.Paused || status == TimerStatus.Done) ResetButton(onReset)
        TimerButton(status, idleText, clockText, progress, onToggle)
    }
}

/** A [StepTimer] that counts down against the wall clock; [state] is null while idle. */
@Composable
fun LiveStepTimer(
    state: TimerState?,
    durationSecs: Int,
    onToggle: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val timer = state ?: TimerState.idle(durationSecs)
    val now by produceState(System.currentTimeMillis(), timer.status, timer.endAtMs) {
        value = System.currentTimeMillis()
        while (timer.status == TimerStatus.Running) {
            delay(250)
            value = System.currentTimeMillis()
        }
    }
    StepTimer(
        status = timer.status,
        idleText = remember(durationSecs) { formatDuration(durationSecs) },
        clockText = formatClock(timer.remainingMsAt(now)),
        progress = timer.progressAt(now),
        onToggle = onToggle,
        onReset = onReset,
        modifier = modifier,
    )
}

@Composable
private fun ResetButton(onReset: () -> Unit) {
    val colors = LocalSimmerColors.current
    Surface(
        onClick = onReset,
        shape = TimerShape,
        color = Color.Transparent,
        contentColor = colors.textSecondary,
        border = BorderStroke(2.dp, colors.borderDefault),
        modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painterResource(R.drawable.ic_rotate_ccw),
                contentDescription = stringResource(R.string.timer_reset_description),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun TimerButton(
    status: TimerStatus,
    idleText: String,
    clockText: String,
    progress: Float,
    onToggle: () -> Unit,
) {
    val colors = LocalSimmerColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val filled = status == TimerStatus.Idle || status == TimerStatus.Running
    val done = status == TimerStatus.Done

    val fill = when {
        done -> colors.surfaceInverse
        filled -> if (pressed) colors.accentPressed else colors.accent
        else -> Color.Transparent
    }
    val border = if (done) colors.surfaceInverse else if (pressed) colors.accentPressed else colors.accent
    val content = when {
        done -> colors.textInverse
        filled -> colors.accentFg
        else -> colors.textAccent
    }
    val icon = when (status) {
        TimerStatus.Idle -> R.drawable.ic_timer
        TimerStatus.Running -> R.drawable.ic_pause
        TimerStatus.Paused -> R.drawable.ic_play
        TimerStatus.Done -> R.drawable.ic_check
    }
    val text = when (status) {
        TimerStatus.Idle -> idleText
        TimerStatus.Done -> stringResource(R.string.timer_done)
        else -> clockText
    }
    val description = when (status) {
        TimerStatus.Idle -> stringResource(R.string.timer_start_description, idleText)
        TimerStatus.Running -> stringResource(R.string.timer_pause_description, clockText)
        TimerStatus.Paused -> stringResource(R.string.timer_resume_description, clockText)
        TimerStatus.Done -> stringResource(R.string.timer_done_description)
    }

    Surface(
        onClick = onToggle,
        shape = TimerShape,
        color = fill,
        contentColor = content,
        border = BorderStroke(2.dp, border),
        interactionSource = interaction,
        modifier = Modifier
            .defaultMinSize(minWidth = 104.dp, minHeight = 44.dp)
            .semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 14.dp, end = 16.dp),
            ) {
                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = text,
                    style = SimmerTextStyle_Button.copy(fontSize = 15.sp, fontFeatureSettings = "tnum"),
                    modifier = if (done) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier,
                )
            }
            if (status == TimerStatus.Running || status == TimerStatus.Paused) {
                // matchParentSize: the bar must not widen the button.
                Box(
                    Modifier
                        .matchParentSize()
                        .padding(start = 12.dp, end = 12.dp, bottom = 3.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    ProgressBar(
                        progress = progress,
                        track = if (status == TimerStatus.Running) Color.Black.copy(alpha = 0.22f) else colors.borderSubtle,
                        fill = if (status == TimerStatus.Running) colors.accentFg else colors.accent,
                    )
                }
            }
        }
    }
}

/** 3dp rounded bar; [progress] of it is filled. */
@Composable
private fun ProgressBar(progress: Float, track: Color, fill: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(CircleShape)
            .background(track),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(3.dp)
                .background(fill),
        )
    }
}
