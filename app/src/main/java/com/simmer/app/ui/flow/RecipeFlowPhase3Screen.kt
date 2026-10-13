package com.simmer.app.ui.flow

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simmer.app.R
import com.simmer.app.domain.BODY_TIP_MARK
import com.simmer.app.domain.BodyLink
import com.simmer.app.domain.CookingStep
import com.simmer.app.domain.CookingView
import com.simmer.app.domain.TimerState
import com.simmer.app.domain.TooltipPlacement
import com.simmer.app.domain.formatDuration
import com.simmer.app.domain.hasSelectableMethod
import com.simmer.app.domain.hasTipLinks
import com.simmer.app.domain.layoutBody
import com.simmer.app.domain.model.BodySegment
import com.simmer.app.domain.model.ResolvedIngredient
import com.simmer.app.domain.placeTooltip
import com.simmer.app.domain.selectionSummary
import com.simmer.app.ui.components.DarkButton
import com.simmer.app.ui.components.GradientHeroCard
import com.simmer.app.ui.components.LinkButton
import com.simmer.app.ui.components.LiveStepTimer
import com.simmer.app.ui.components.SecondaryButton
import com.simmer.app.ui.theme.LocalSimmerColors
import com.simmer.app.ui.theme.LocalSimmerDarkTheme
import com.simmer.app.ui.theme.SimmerTextStyle_BodyMd
import com.simmer.app.ui.theme.SimmerTextStyle_BodyMdMedium
import com.simmer.app.ui.theme.SimmerTextStyle_BodySmMedium
import com.simmer.app.ui.theme.SimmerTextStyle_Caption
import com.simmer.app.ui.theme.SimmerTextStyle_Heading4
import com.simmer.app.ui.theme.SimmerTextStyle_Heading5
import com.simmer.app.ui.theme.SimmerTextStyle_HeroTitle
import com.simmer.app.ui.theme.SimmerTextStyle_Micro
import com.simmer.app.ui.theme.SimmerTextStyle_MicroUppercase
import kotlinx.coroutines.launch

/** One row of the cooking list; the list index of a Step is what "Go to step" scrolls to. */
private sealed interface CookingItem {
    data object Success : CookingItem
    data object Progress : CookingItem
    data object Hint : CookingItem
    data class Section(val prep: Boolean) : CookingItem
    data class StepItem(val cookingStep: CookingStep) : CookingItem
}

private fun cookingItems(view: CookingView, showHint: Boolean): List<CookingItem> = buildList {
    if (view.allDone) add(CookingItem.Success)
    add(CookingItem.Progress)
    if (showHint) add(CookingItem.Hint)
    val (prep, cook) = view.steps.partition { it.step.phase == "prep" }
    if (prep.isNotEmpty()) {
        add(CookingItem.Section(prep = true))
        prep.forEach { add(CookingItem.StepItem(it)) }
    }
    if (cook.isNotEmpty()) {
        add(CookingItem.Section(prep = false))
        cook.forEach { add(CookingItem.StepItem(it)) }
    }
}

/**
 * Recipe flow, phase 3: cook through the steps. Shares [viewModel] with phases 1 and 2
 * (scoped to the [com.simmer.app.ui.nav.RecipeFlow] back stack entry).
 */
@Composable
fun RecipeFlowPhase3Screen(
    viewModel: RecipeFlowViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onBackToRecipes: () -> Unit,
    onPhaseClick: (phase: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSimmerColors.current
    val ui by viewModel.phase3.collectAsStateWithLifecycle()
    val timerStates by viewModel.timers.collectAsStateWithLifecycle()
    val askNotificationPermission = rememberNotificationPermissionRequest()
    val timers = TimerControls(
        states = timerStates,
        onToggle = { stepId, title, secs ->
            viewModel.toggleTimer(stepId, title, secs)
            askNotificationPermission()
        },
        onReset = viewModel::resetTimer,
        onTick = viewModel::tickTimers,
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfacePage)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        when (val content = ui) {
            Phase3Ui.Loading -> Unit
            is Phase3Ui.Content -> Column(modifier = Modifier.fillMaxSize()) {
                FlowTopBar(title = content.recipe.title, phase = 3, skipsMethods = !hasSelectableMethod(content.recipe), onBack = onBack, onEdit = onEdit)
                FlowProgress(current = 3, skipsMethods = !hasSelectableMethod(content.recipe), onSegmentClick = onPhaseClick)
                CookingList(
                    content = content,
                    onMarkDone = viewModel::markStepDone,
                    onUndo = viewModel::undoStep,
                    onBackToRecipes = onBackToRecipes,
                    timers = timers,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CookingList(
    content: Phase3Ui.Content,
    onMarkDone: (String) -> Unit,
    onUndo: (String) -> Unit,
    onBackToRecipes: () -> Unit,
    timers: TimerControls,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSimmerColors.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val items = cookingItems(content.view, showHint = hasTipLinks(content.view.steps.map { it.step }, content.ingredients))
    val summary = selectionSummary(
        content.recipe, content.selection,
        pluralStringResource(R.plurals.flow_servings_count, content.servings, content.servings),
    )

    fun jumpTo(stepId: String) {
        val index = items.indexOfFirst { it is CookingItem.StepItem && it.cookingStep.step.id == stepId }
        if (index >= 0) scope.launch { listState.animateScrollToItem(index) }
    }

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
    ) {
        itemsIndexed(
            items,
            key = { _, item ->
                when (item) {
                    CookingItem.Success -> "success"
                    CookingItem.Progress -> "progress"
                    CookingItem.Hint -> "hint"
                    is CookingItem.Section -> if (item.prep) "section-prep" else "section-cook"
                    is CookingItem.StepItem -> "step-${item.cookingStep.step.id}"
                }
            },
        ) { _, item ->
            when (item) {
                CookingItem.Success -> SuccessPanel(content.recipe.title, summary, onBackToRecipes)
                CookingItem.Progress -> ProgressBlock(content.view, summary)
                CookingItem.Hint -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_lightbulb), null, Modifier.size(16.dp), tint = colors.textTertiary)
                    Text(stringResource(R.string.flow_tap_hint), style = SimmerTextStyle_Caption, color = colors.textTertiary)
                }
                is CookingItem.Section -> Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(
                        stringResource(if (item.prep) R.string.flow_phase_preparation else R.string.flow_phase_cooking),
                        style = SimmerTextStyle_Heading5,
                        color = colors.textPrimary,
                    )
                    Text(
                        stringResource(if (item.prep) R.string.flow_prep_caption else R.string.flow_cook_caption),
                        style = SimmerTextStyle_Caption,
                        color = colors.textTertiary,
                    )
                }
                is CookingItem.StepItem -> StepRow(
                    cookingStep = item.cookingStep,
                    ingredients = content.ingredients,
                    onMarkDone = { onMarkDone(item.cookingStep.step.id) },
                    onUndo = { onUndo(item.cookingStep.step.id) },
                    onJump = ::jumpTo,
                    timers = timers,
                )
            }
        }
    }
}

/** The timers of this recipe by step id, and what tapping them does. */
@androidx.compose.runtime.Immutable
private class TimerControls(
    val states: Map<String, TimerState>,
    val onToggle: (stepId: String, title: String, durationSecs: Int) -> Unit,
    val onReset: (stepId: String) -> Unit,
    val onTick: () -> Unit,
)

@Composable
private fun SuccessPanel(title: String, summary: String, onBackToRecipes: () -> Unit) {
    val colors = LocalSimmerColors.current
    GradientHeroCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Icon(painterResource(R.drawable.ic_circle_check), null, Modifier.size(32.dp), tint = colors.textHero)
            Text(
                stringResource(R.string.flow_enjoy),
                style = SimmerTextStyle_HeroTitle,
                color = colors.textHero,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Text("$title · $summary", style = SimmerTextStyle_BodyMd, color = colors.textHero)
            DarkButton(
                text = stringResource(R.string.flow_back_to_recipes),
                onClick = onBackToRecipes,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ProgressBlock(view: CookingView, summary: String) {
    val colors = LocalSimmerColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(R.string.flow_cook_progress, view.doneCount, view.total),
                style = SimmerTextStyle_BodySmMedium,
                color = colors.textPrimary,
            )
            Text(
                summary,
                style = SimmerTextStyle_Caption,
                color = colors.textTertiary,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(colors.borderSubtle, CircleShape),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(view.fraction)
                    .background(colors.accent, CircleShape),
            )
        }
    }
}

@Composable
private fun StepRow(
    cookingStep: CookingStep,
    ingredients: Map<String, ResolvedIngredient>,
    onMarkDone: () -> Unit,
    onUndo: () -> Unit,
    onJump: (String) -> Unit,
    timers: TimerControls,
) {
    if (cookingStep.done) DoneStepRow(cookingStep, onUndo) else OpenStepCard(cookingStep, ingredients, onMarkDone, onJump, timers)
}

@Composable
private fun DoneStepRow(cookingStep: CookingStep, onUndo: () -> Unit) {
    val colors = LocalSimmerColors.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .background(colors.surfaceSubtle, shape)
            .border(BorderStroke(2.dp, colors.borderSubtle), shape)
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(24.dp)
                .background(colors.accent, CircleShape),
        ) {
            Icon(painterResource(R.drawable.ic_check), null, Modifier.size(14.dp), tint = colors.accentFg)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) {},
        ) {
            Text(cookingStep.step.label, style = SimmerTextStyle_Micro, color = colors.textTertiary)
            Text(
                cookingStep.step.title,
                style = SimmerTextStyle_BodySmMedium,
                color = colors.textTertiary,
                textDecoration = TextDecoration.LineThrough,
            )
        }
        LinkButton(text = stringResource(R.string.flow_undo), onClick = onUndo)
    }
}

@Composable
private fun OpenStepCard(
    cookingStep: CookingStep,
    ingredients: Map<String, ResolvedIngredient>,
    onMarkDone: () -> Unit,
    onJump: (String) -> Unit,
    timers: TimerControls,
) {
    val colors = LocalSimmerColors.current
    val shape = RoundedCornerShape(12.dp)
    val step = cookingStep.step
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceCard, shape)
            .border(
                BorderStroke(if (cookingStep.active) 3.dp else 2.dp, if (cookingStep.active) colors.accent else colors.borderSubtle),
                shape,
            )
            .padding(if (cookingStep.active) 17.dp else 18.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                Text(
                    step.label,
                    style = SimmerTextStyle_MicroUppercase,
                    color = if (cookingStep.active) colors.textAccent else colors.textTertiary,
                )
                Text(step.title, style = SimmerTextStyle_Heading4, color = colors.textPrimary)
            }
            if (step.durationSecs > 0) {
                LiveStepTimer(
                    state = timers.states[step.id],
                    durationSecs = step.durationSecs,
                    onToggle = { timers.onToggle(step.id, step.title, step.durationSecs) },
                    onReset = { timers.onReset(step.id) },
                    onTick = timers.onTick,
                )
            }
        }
        cookingStep.pendingNeeds.forEach { need ->
            NeedsWarning(
                label = need.label,
                title = need.title,
                onJump = { onJump(need.id) },
            )
        }
        cookingStep.doneNeeds.forEach { need ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(painterResource(R.drawable.ic_circle_check), null, Modifier.size(14.dp), tint = colors.textTertiary)
                Text(
                    stringResource(R.string.flow_need_done, need.label, need.title),
                    style = SimmerTextStyle_Caption,
                    color = colors.textTertiary,
                )
            }
        }
        StepBody(step.segs, ingredients)
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(colors.borderSubtle),
        )
        val check: @Composable () -> Unit = { Icon(painterResource(R.drawable.ic_check), null, Modifier.size(20.dp)) }
        if (cookingStep.active) {
            DarkButton(
                text = stringResource(R.string.flow_mark_done),
                onClick = onMarkDone,
                icon = check,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            SecondaryButton(
                text = stringResource(R.string.flow_mark_done),
                onClick = onMarkDone,
                icon = check,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun NeedsWarning(label: String, title: String, onJump: () -> Unit) {
    val colors = LocalSimmerColors.current
    val shape = RoundedCornerShape(8.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceWarm, shape)
            .border(BorderStroke(2.dp, colors.accent), shape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(painterResource(R.drawable.ic_circle_alert), null, Modifier.size(18.dp), tint = colors.textAccent)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.flow_needs_first, label),
                style = SimmerTextStyle_BodySmMedium,
                color = colors.textPrimary,
            )
            Text(title, style = SimmerTextStyle_Caption, color = colors.textSecondary)
        }
        LinkButton(text = stringResource(R.string.flow_go_to_step), onClick = onJump)
    }
}

// ---------------------------------------------------------------------------
// Step body: Links with their amount inline; Links with tips open the Tips tooltip
// ---------------------------------------------------------------------------

private val UnderlineThickness = 2.dp
private val UnderlineOffset = 3.dp
private val MinTouchTarget = 44.dp
private val LinkTargetPadding = 12.dp
private val TooltipMaxWidth = 300.dp
private val TooltipMargin = 12.dp
private val TooltipGap = 10.dp
private val TooltipArrow = 14.dp
private val TooltipArrowInset = 20.dp
private val TooltipShadowPad = 24.dp
private val TooltipCorner = 12.dp
private val CloseButtonSize = 44.dp
private const val TIP_ICON = "tip"

/** The part of a Link on one text line, in text coordinates. */
private data class LinkLine(val index: Int, val left: Float, val right: Float, val top: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
}

private fun linkLines(layout: TextLayoutResult, start: Int, end: Int): List<LinkLine> {
    val first = layout.getLineForOffset(start)
    val last = layout.getLineForOffset(end - 1)
    return (first..last).map { line ->
        LinkLine(
            index = line,
            left = if (line == first) layout.getHorizontalPosition(start, true) else layout.getLineLeft(line),
            right = if (line == last) layout.getHorizontalPosition(end, true) else layout.getLineRight(line),
            top = layout.getLineTop(line),
            bottom = layout.getLineBottom(line),
        )
    }
}

/**
 * Step text where each ingredient Link shows its amount "(3 cloves)" and is underlined in orange
 * (2dp, offset below the baseline). A Link whose ingredient has tips also shows a lightbulb and is
 * a button that opens the Tips tooltip; tapping outside or the close button dismisses it.
 */
@Composable
internal fun StepBody(segs: List<BodySegment>, ingredients: Map<String, ResolvedIngredient>) {
    val colors = LocalSimmerColors.current
    val density = LocalDensity.current
    val body = remember(segs, ingredients) { layoutBody(segs, ingredients) }
    val annotated = remember(body) {
        buildAnnotatedString {
            body.text.forEach { c -> if (c == BODY_TIP_MARK) appendInlineContent(TIP_ICON) else append(c) }
        }
    }
    val inline = remember(colors) {
        mapOf(
            TIP_ICON to InlineTextContent(Placeholder(14.sp, 14.sp, PlaceholderVerticalAlign.Center)) {
                Icon(painterResource(R.drawable.ic_lightbulb), null, Modifier.fillMaxSize(), tint = colors.textAccent)
            },
        )
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var openLink by remember { mutableStateOf<BodyLink?>(null) }
    var tipsAnchor by remember { mutableStateOf(IntRect.Zero) }

    // The vertical padding gives short links room to reach a 44dp touch target.
    val padPx = with(density) { LinkTargetPadding.roundToPx() }
    val minTargetPx = with(density) { MinTouchTarget.roundToPx() }
    Box {
        Box(Modifier.padding(vertical = LinkTargetPadding)) {
            Text(
                text = annotated,
                inlineContent = inline,
                style = SimmerTextStyle_BodyMd,
                color = colors.textSecondary,
                onTextLayout = { layout = it },
                modifier = Modifier.drawBehind {
                    val l = layout ?: return@drawBehind
                    val thickness = UnderlineThickness.toPx()
                    val gap = UnderlineOffset.toPx()
                    // Like the design's Ingredient, the open highlight covers what is underlined.
                    openLink?.let { link ->
                        linkLines(l, link.start, link.underlineEnd).forEach { line ->
                            drawRoundRect(
                                colors.surfaceWarmStrong,
                                Offset(line.left, line.top),
                                Size(line.width, line.height),
                                CornerRadius(4.dp.toPx()),
                            )
                        }
                    }
                    body.links.forEach { link ->
                        linkLines(l, link.start, link.underlineEnd).forEach { line ->
                            drawRect(colors.accent, Offset(line.left, l.getLineBaseline(line.index) + gap), Size(line.width, thickness))
                        }
                    }
                },
            )
        }
        // One button per tip-bearing link (per line it wraps over), sized to at least 44dp.
        layout?.let { l ->
            body.links.filter { it.tappable }.forEach { link ->
                val description = stringResource(
                    R.string.flow_link_tips_description,
                    body.text.substring(link.start, link.underlineEnd),
                    ingredients.getValue(link.id).name,
                )
                val wordLines = linkLines(l, link.start, link.underlineEnd)
                linkLines(l, link.start, link.end).forEachIndexed { n, line ->
                    val w = maxOf(line.width.toInt(), minTargetPx)
                    val h = maxOf(line.height.toInt(), minTargetPx)
                    val x = (line.left - (w - line.width) / 2f).toInt().coerceAtLeast(0)
                    val y = (padPx + line.top - (h - line.height) / 2f).toInt().coerceAtLeast(0)
                    Box(
                        Modifier
                            .offset { IntOffset(x, y) }
                            .size(with(density) { w.toDp() }, with(density) { h.toDp() })
                            .then(
                                if (n == 0) {
                                    Modifier.semantics { contentDescription = description }
                                } else {
                                    Modifier.clearAndSetSemantics {}
                                },
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button,
                            ) {
                                // The arrow points at the underlined word on the tapped line (the lightbulb may have wrapped).
                                val target = wordLines.firstOrNull { it.index == line.index } ?: wordLines.last()
                                tipsAnchor = IntRect(
                                    target.left.toInt(),
                                    padPx + target.top.toInt(),
                                    target.right.toInt(),
                                    padPx + target.bottom.toInt(),
                                )
                                openLink = link
                            },
                    )
                }
            }
        }
        openLink?.let { link ->
            TipsTooltip(
                tips = ingredients.getValue(link.id).tips,
                anchor = tipsAnchor,
                onDismiss = { openLink = null },
            )
        }
    }
}

/** The Tips tooltip: arrow at the tapped word, "Tips" title with a close button, then the tips. */
@Composable
private fun TipsTooltip(tips: List<String>, anchor: IntRect, onDismiss: () -> Unit) {
    val colors = LocalSimmerColors.current
    val isDark = LocalSimmerDarkTheme.current
    val density = LocalDensity.current
    val shape = RoundedCornerShape(TooltipCorner)
    val shadowPad = with(density) { TooltipShadowPad.roundToPx() }
    val margin = with(density) { TooltipMargin.roundToPx() }
    val gap = with(density) { TooltipGap.roundToPx() }
    val arrowInset = with(density) { TooltipArrowInset.roundToPx() }
    // Null until the popup has been measured and placed; the tooltip stays invisible until then so the
    // arrow never shows on the wrong side for a frame.
    var placement by remember { mutableStateOf<TooltipPlacement?>(null) }
    val title = stringResource(R.string.flow_tips_title)
    Popup(
        popupPositionProvider = remember(anchor) {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize,
                ): IntOffset {
                    val p = placeTooltip(
                        anchorBounds.left + anchor.left,
                        anchorBounds.top + anchor.top,
                        anchorBounds.left + anchor.right,
                        anchorBounds.top + anchor.bottom,
                        popupContentSize.width - 2 * shadowPad,
                        popupContentSize.height - 2 * shadowPad,
                        windowSize.width,
                        windowSize.height,
                        margin,
                        gap,
                        arrowInset,
                    )
                    placement = p
                    return IntOffset(p.x - shadowPad, p.y - shadowPad)
                }
            }
        },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        // The outer padding leaves room for the shadow, which the popup window would otherwise clip.
        Box(Modifier.padding(TooltipShadowPad).alpha(if (placement == null) 0f else 1f)) {
            Box(Modifier.widthIn(max = TooltipMaxWidth).semantics { paneTitle = title }) {
                Box(
                    Modifier
                        .matchParentSize()
                        // shadow-4 is light theme only; dark relies on the outline.
                        .then(
                            if (isDark) {
                                Modifier
                            } else {
                                Modifier.shadow(
                                    16.dp, shape, clip = false,
                                    ambientColor = Color.Black.copy(alpha = 0.12f),
                                    spotColor = Color.Black.copy(alpha = 0.12f),
                                )
                            },
                        )
                        .background(colors.surfaceCard, shape)
                        .border(BorderStroke(2.dp, colors.borderStrong), shape),
                )
                placement?.let { TooltipArrowMark(it) }
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, end = 4.dp),
                    ) {
                        Text(
                            title,
                            style = SimmerTextStyle_Heading5,
                            color = colors.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(CloseButtonSize)
                                .clickable(role = Role.Button, onClick = onDismiss),
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_x),
                                stringResource(R.string.flow_tips_close),
                                Modifier.size(18.dp),
                                tint = colors.textTertiary,
                            )
                        }
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 16.dp),
                    ) {
                        tips.forEach { tip ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(
                                    painterResource(R.drawable.ic_lightbulb), null,
                                    Modifier.padding(top = 2.dp).size(14.dp), tint = colors.textTertiary,
                                )
                                Text(tip, style = SimmerTextStyle_Caption, color = colors.textSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The 14dp rotated square on the tooltip edge nearest the word: top when below it, bottom when above. */
@Composable
private fun BoxScope.TooltipArrowMark(placement: TooltipPlacement) {
    val colors = LocalSimmerColors.current
    val density = LocalDensity.current
    val half = with(density) { (TooltipArrow / 2).roundToPx() }
    val below = placement.below
    Box(
        Modifier
            .align(if (below) Alignment.TopStart else Alignment.BottomStart)
            .offset { IntOffset(placement.arrowX - half, if (below) -half else half) }
            .size(TooltipArrow)
            .rotate(45f)
            .background(colors.surfaceCard, RoundedCornerShape(topStart = 3.dp, bottomEnd = 3.dp))
            .drawBehind {
                // Outline the two edges that face away from the card: top and left when below the word,
                // bottom and right when above it.
                val w = 2.dp.toPx()
                val s = size.width
                val edge = if (below) w / 2 else s - w / 2
                drawLine(colors.borderStrong, Offset(0f, edge), Offset(s, edge), w)
                drawLine(colors.borderStrong, Offset(edge, 0f), Offset(edge, s), w)
            },
    )
}
