package com.simmer.app.ui.flow

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simmer.app.R
import com.simmer.app.domain.CookingStep
import com.simmer.app.domain.CookingView
import com.simmer.app.domain.TimerState
import com.simmer.app.domain.formatDuration
import com.simmer.app.domain.model.BodySegment
import com.simmer.app.domain.model.ResolvedIngredient
import com.simmer.app.domain.selectionSummary
import com.simmer.app.domain.hasSelectableMethod
import com.simmer.app.ui.components.DarkButton
import com.simmer.app.ui.components.GradientHeroCard
import com.simmer.app.ui.components.LinkButton
import com.simmer.app.ui.components.SecondaryButton
import com.simmer.app.ui.components.LiveStepTimer
import com.simmer.app.ui.theme.LocalSimmerColors
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

private fun cookingItems(view: CookingView): List<CookingItem> = buildList {
    if (view.allDone) add(CookingItem.Success)
    add(CookingItem.Progress)
    add(CookingItem.Hint)
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
    val items = cookingItems(content.view)
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
// Step body with tappable ingredient references
// ---------------------------------------------------------------------------

private data class LinkRange(val id: String, val start: Int, val end: Int, val shown: String)

private val UnderlineThickness = 2.dp
private val UnderlineOffset = 3.dp
private val MinTouchTarget = 44.dp
private val LinkTargetPadding = 12.dp

/** The part of a Link on one text line, in text coordinates. */
private data class LinkLine(val index: Int, val left: Float, val right: Float)

private fun linkLines(layout: TextLayoutResult, range: LinkRange): List<LinkLine> {
    val first = layout.getLineForOffset(range.start)
    val last = layout.getLineForOffset(range.end - 1)
    return (first..last).map { line ->
        LinkLine(
            index = line,
            left = if (line == first) layout.getHorizontalPosition(range.start, true) else layout.getLineLeft(line),
            right = if (line == last) layout.getHorizontalPosition(range.end, true) else layout.getLineRight(line),
        )
    }
}

/**
 * Step text where each ingredient Link is underlined (2dp, offset below the baseline).
 * Tapping one opens a popover with the ingredient's amount, note and tips; tapping outside closes it.
 */
@Composable
internal fun StepBody(segs: List<BodySegment>, ingredients: Map<String, ResolvedIngredient>) {
    val colors = LocalSimmerColors.current
    val density = LocalDensity.current
    val (text, ranges) = remember(segs) { bodyText(segs) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var openIngredient by remember { mutableStateOf<ResolvedIngredient?>(null) }
    var anchor by remember { mutableStateOf(Offset.Zero) }
    val underlineColor = colors.textSecondary

    fun openRange(r: LinkRange, at: Offset) {
        ingredients[r.id]?.let {
            anchor = at
            openIngredient = it
        }
    }

    // The vertical padding gives short links room to reach a 44dp touch target.
    val padPx = with(density) { LinkTargetPadding.roundToPx() }
    val minTargetPx = with(density) { MinTouchTarget.roundToPx() }
    Box {
        Box(Modifier.padding(vertical = LinkTargetPadding)) {
            Text(
                text = text,
                style = SimmerTextStyle_BodyMd,
                color = colors.textSecondary,
                onTextLayout = { layout = it },
                modifier = Modifier.drawBehind {
                    val l = layout ?: return@drawBehind
                    val thickness = with(density) { UnderlineThickness.toPx() }
                    val gap = with(density) { UnderlineOffset.toPx() }
                    ranges.forEach { r ->
                        linkLines(l, r).forEach { line ->
                            val y = l.getLineBaseline(line.index) + gap
                            drawRect(underlineColor, Offset(line.left, y), Size(line.right - line.left, thickness))
                        }
                    }
                },
            )
        }
        // One button per link (per line it wraps over), sized to at least 44dp.
        layout?.let { l ->
            ranges.filter { it.id in ingredients }.forEach { r ->
                linkLines(l, r).forEachIndexed { n, line ->
                    val width = (line.right - line.left).toInt()
                    val height = (l.getLineBottom(line.index) - l.getLineTop(line.index)).toInt()
                    val w = maxOf(width, minTargetPx)
                    val h = maxOf(height, minTargetPx)
                    val x = (line.left - (w - width) / 2f).toInt().coerceAtLeast(0)
                    val y = (padPx + l.getLineTop(line.index) - (h - height) / 2f).toInt().coerceAtLeast(0)
                    Box(
                        Modifier
                            .offset { IntOffset(x, y) }
                            .size(with(density) { w.toDp() }, with(density) { h.toDp() })
                            .then(
                                if (n == 0) {
                                    Modifier.semantics { contentDescription = r.shown }
                                } else {
                                    Modifier.clearAndSetSemantics {}
                                },
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button,
                            ) { openRange(r, Offset(line.left, padPx + l.getLineBottom(line.index))) },
                    )
                }
            }
        }
        openIngredient?.let { ingredient ->
            val gap = with(density) { 12.dp.roundToPx() }
            val margin = with(density) { 16.dp.roundToPx() }
            Popup(
                popupPositionProvider = remember(anchor) {
                    object : PopupPositionProvider {
                        override fun calculatePosition(
                            anchorBounds: IntRect,
                            windowSize: IntSize,
                            layoutDirection: LayoutDirection,
                            popupContentSize: IntSize,
                        ): IntOffset {
                            val x = (anchorBounds.left + anchor.x.toInt())
                                .coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin))
                            val below = anchorBounds.top + anchor.y.toInt() + gap
                            val y = if (below + popupContentSize.height > windowSize.height) {
                                (anchorBounds.top + anchor.y.toInt() - gap - popupContentSize.height).coerceAtLeast(0)
                            } else below
                            return IntOffset(x, y)
                        }
                    }
                },
                onDismissRequest = { openIngredient = null },
                properties = PopupProperties(focusable = true),
            ) {
                IngredientPopover(ingredient)
            }
        }
    }
}

private fun bodyText(segs: List<BodySegment>): Pair<AnnotatedString, List<LinkRange>> {
    val ranges = mutableListOf<LinkRange>()
    val text = buildAnnotatedString {
        segs.forEach { seg ->
            when (seg) {
                is BodySegment.TextSegment -> append(seg.text)
                is BodySegment.IngredientLinkSegment -> {
                    val start = length
                    append(seg.text)
                    ranges += LinkRange(seg.id, start, length, seg.text)
                }
            }
        }
    }
    return text to ranges
}

@Composable
private fun IngredientPopover(ingredient: ResolvedIngredient) {
    val colors = LocalSimmerColors.current
    val shape = RoundedCornerShape(12.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .widthIn(min = 180.dp, max = 280.dp)
            .background(colors.surfaceCard, shape)
            .border(BorderStroke(2.dp, colors.borderStrong), shape)
            .padding(14.dp),
    ) {
        Text(ingredient.name, style = SimmerTextStyle_BodyMdMedium, color = colors.textPrimary)
        Text(ingredient.amount, style = SimmerTextStyle_BodySmMedium, color = colors.textAccent)
        if (ingredient.note.isNotBlank()) {
            Text(ingredient.note, style = SimmerTextStyle_Caption, color = colors.textTertiary)
        }
        ingredient.tips.forEach { tip ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(painterResource(R.drawable.ic_lightbulb), null, Modifier.size(14.dp), tint = colors.textTertiary)
                Text(tip, style = SimmerTextStyle_Caption, color = colors.textSecondary)
            }
        }
    }
}
