package com.simmer.app.ui.flow

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simmer.app.R
import com.simmer.app.domain.MAX_SERVINGS
import com.simmer.app.domain.MIN_SERVINGS
import com.simmer.app.domain.Phase1State
import com.simmer.app.domain.addInAmount
import com.simmer.app.domain.isYourPick
import com.simmer.app.ui.components.Badge
import com.simmer.app.ui.components.BadgeVariant
import com.simmer.app.ui.components.CheckRow
import com.simmer.app.ui.components.GradientHeroCard
import com.simmer.app.ui.components.LinkButton
import com.simmer.app.ui.components.OptionCard
import com.simmer.app.ui.components.PrimaryButton
import com.simmer.app.ui.components.SecondaryButton
import com.simmer.app.ui.theme.LocalSimmerColors
import com.simmer.app.ui.theme.SimmerTextStyle_BodyMd
import com.simmer.app.ui.theme.SimmerTextStyle_BodySm
import com.simmer.app.ui.theme.SimmerTextStyle_BodySmMedium
import com.simmer.app.ui.theme.SimmerTextStyle_Caption
import com.simmer.app.ui.theme.SimmerTextStyle_Heading5
import com.simmer.app.ui.theme.SimmerTextStyle_HeroTitle
import com.simmer.app.ui.theme.SimmerTextStyle_Micro
import com.simmer.app.ui.theme.SimmerTextStyle_ServingsNumber
import kotlinx.coroutines.delay

private const val TOAST_MILLIS = 6_000L

/** Recipe flow, phase 1: servings, variations and the scaled ingredient list. */
@Composable
fun RecipeFlowPhase1Screen(
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onViewList: () -> Unit,
    onPreparation: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecipeFlowViewModel = hiltViewModel(),
) {
    val colors = LocalSimmerColors.current
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfacePage)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        when (val u = ui) {
            Phase1Ui.Loading -> Unit
            Phase1Ui.NotFound -> Column {
                FlowTopBar("", 1, onBack, onEdit = {})
                Text(
                    text = stringResource(R.string.flow_recipe_not_found),
                    style = SimmerTextStyle_BodyMd,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(16.dp),
                )
            }
            is Phase1Ui.Content -> Phase1Content(
                ui = u,
                viewModel = viewModel,
                onBack = onBack,
                onEdit = onEdit,
                onViewList = onViewList,
                onPreparation = onPreparation,
            )
        }
    }
}

@Composable
private fun Phase1Content(
    ui: Phase1Ui.Content,
    viewModel: RecipeFlowViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onViewList: () -> Unit,
    onPreparation: () -> Unit,
) {
    val colors = LocalSimmerColors.current
    val s = ui.state
    val recipe = s.recipe

    LaunchedEffect(ui.toast) {
        if (ui.toast != null) {
            delay(TOAST_MILLIS)
            viewModel.dismissToast()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        FlowTopBar(title = recipe.title, phase = 1, onBack = onBack, onEdit = onEdit)
        // Phase 1 is the first segment, so no earlier segment can be tapped.
        FlowProgress(current = 1, onSegmentClick = { onBack() })

        Box(modifier = Modifier.weight(1f)) {
            Column(
                verticalArrangement = Arrangement.spacedBy(28.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            ) {
                Hero(s)
                ServingsStepper(
                    servings = s.servings,
                    onStep = viewModel::stepServings,
                )
                if (recipe.variations.isNotEmpty()) {
                    Variations(ui, viewModel)
                }
                IngredientList(ui)
            }

            ui.toast?.let { toast ->
                AddedToastCard(
                    items = toast.items,
                    servings = toast.servings,
                    onViewList = onViewList,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        // Sticky bottom bar
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfacePage)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (s.isOnList) {
                SecondaryButton(
                    text = stringResource(R.string.flow_on_your_list),
                    onClick = onViewList,
                    icon = { Icon(painterResource(R.drawable.ic_check), null, Modifier.size(20.dp)) },
                    modifier = Modifier.weight(1f),
                )
            } else {
                SecondaryButton(
                    text = stringResource(R.string.flow_add_to_list),
                    onClick = viewModel::addToList,
                    modifier = Modifier.weight(1f),
                )
            }
            PrimaryButton(
                text = stringResource(R.string.flow_preparation_next),
                onClick = onPreparation,
                icon = { Icon(painterResource(R.drawable.ic_arrow_right), null, Modifier.size(20.dp)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Hero
// ---------------------------------------------------------------------------

@Composable
private fun Hero(s: Phase1State) {
    val colors = LocalSimmerColors.current
    val r = s.recipe
    GradientHeroCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(20.dp),
        ) {
            Badge(label = r.category, variant = BadgeVariant.Cream)
            Text(text = r.title, style = SimmerTextStyle_HeroTitle, color = colors.textPrimary)
            if (r.summary.isNotBlank()) {
                Text(text = r.summary, style = SimmerTextStyle_BodyMd, color = colors.textHero)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MetaItem(R.drawable.ic_clock, formatMinutes(r.time))
                MetaItem(R.drawable.ic_flame, r.difficulty)
            }
        }
    }
}

@Composable
private fun MetaItem(icon: Int, text: String) {
    val colors = LocalSimmerColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.textHero, modifier = Modifier.size(16.dp))
        Text(text = text, style = SimmerTextStyle_BodySm, color = colors.textHero)
    }
}

@Composable
private fun formatMinutes(min: Int): String = when {
    min < 60 -> stringResource(R.string.flow_time_min, min)
    min % 60 == 0 -> stringResource(R.string.flow_time_h, min / 60)
    else -> stringResource(R.string.flow_time_h_min, min / 60, min % 60)
}

// ---------------------------------------------------------------------------
// Servings stepper
// ---------------------------------------------------------------------------

@Composable
private fun ServingsStepper(servings: Int, onStep: (Int) -> Unit) {
    val colors = LocalSimmerColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.flow_servings),
                style = SimmerTextStyle_Heading5,
                color = colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.flow_servings_hint),
                style = SimmerTextStyle_Caption,
                color = colors.textTertiary,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            StepperButton(
                icon = R.drawable.ic_minus,
                description = stringResource(R.string.flow_fewer_servings),
                enabled = servings > MIN_SERVINGS,
                onClick = { onStep(-1) },
            )
            Text(
                text = servings.toString(),
                style = SimmerTextStyle_ServingsNumber,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 52.dp),
            )
            StepperButton(
                icon = R.drawable.ic_plus,
                description = stringResource(R.string.flow_more_servings),
                enabled = servings < MAX_SERVINGS,
                onClick = { onStep(+1) },
            )
        }
    }
}

@Composable
private fun StepperButton(icon: Int, description: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalSimmerColors.current
    val shape = RoundedCornerShape(8.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(colors.surfaceCard, shape)
            .border(BorderStroke(2.dp, colors.borderStrong), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = colors.textPrimary,
            modifier = Modifier.size(20.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Variations
// ---------------------------------------------------------------------------

@Composable
private fun Variations(ui: Phase1Ui.Content, viewModel: RecipeFlowViewModel) {
    val colors = LocalSimmerColors.current
    val s = ui.state
    val r = s.recipe
    val swaps = r.variations.filter { it.kind == "swap" }
    val addIns = r.variations.filter { it.kind == "add" }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column {
            Text(
                text = stringResource(R.string.flow_your_way),
                style = SimmerTextStyle_Heading5,
                color = colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.flow_your_way_hint),
                style = SimmerTextStyle_Caption,
                color = colors.textTertiary,
            )
        }

        swaps.forEach { v ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = v.label, style = SimmerTextStyle_BodySmMedium, color = colors.textSecondary)
                v.options.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { o ->
                            val selected = s.selection.choice[v.id] == o.value
                            OptionCard(
                                selected = selected,
                                onClick = { viewModel.selectSwap(v.id, o.value) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 48.dp)
                                        .padding(horizontal = 12.dp),
                                ) {
                                    Text(
                                        text = o.label,
                                        style = SimmerTextStyle_BodySmMedium,
                                        color = if (selected) colors.textPrimary else colors.textSecondary,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                        if (row.size == 1) Box(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        if (addIns.isNotEmpty()) {
            val shape = RoundedCornerShape(12.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .border(BorderStroke(2.dp, colors.borderSubtle), shape),
            ) {
                addIns.forEachIndexed { i, v ->
                    if (i > 0) RowDivider()
                    CheckRow(
                        label = v.label,
                        subtitle = v.d.ifBlank { null },
                        trailing = addInAmount(r, v, s.servings, ui.units, ui.spices),
                        checked = s.selection.add[v.id] == true,
                        onCheckedChange = { viewModel.setAddIn(v.id, it) },
                        modifier = Modifier.background(colors.surfaceCard),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Ingredient list
// ---------------------------------------------------------------------------

@Composable
private fun IngredientList(ui: Phase1Ui.Content) {
    val colors = LocalSimmerColors.current
    val s = ui.state
    val resolved = s.resolved(ui.units, ui.spices).ingredients
    val sourceById = s.recipe.ingredients.associateBy { it.id }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.flow_ingredients),
                style = SimmerTextStyle_Heading5,
                color = colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.flow_ingredients_caption, resolved.size, s.servings),
                style = SimmerTextStyle_Caption,
                color = colors.textTertiary,
            )
        }
        val shape = RoundedCornerShape(12.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(BorderStroke(2.dp, colors.borderSubtle), shape),
        ) {
            resolved.forEachIndexed { i, ing ->
                if (i > 0) RowDivider()
                val pick = sourceById[ing.id]?.let { isYourPick(s.recipe, it, s.selection) } == true
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 52.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(text = ing.name, style = SimmerTextStyle_BodyMd, color = colors.textPrimary)
                            if (pick) {
                                Text(
                                    text = stringResource(R.string.flow_your_pick),
                                    style = SimmerTextStyle_Micro,
                                    color = colors.textAccent,
                                )
                            }
                        }
                        if (ing.note.isNotBlank()) {
                            Text(text = ing.note, style = SimmerTextStyle_Caption, color = colors.textTertiary)
                        }
                    }
                    Text(text = ing.amount, style = SimmerTextStyle_BodySmMedium, color = colors.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun RowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(LocalSimmerColors.current.borderSubtle),
    )
}

// ---------------------------------------------------------------------------
// Toast
// ---------------------------------------------------------------------------

@Composable
private fun AddedToastCard(items: Int, servings: Int, onViewList: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalSimmerColors.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceWarm, shape)
            .border(BorderStroke(2.dp, colors.borderWarm), shape)
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.flow_toast_added, items, servings),
                style = SimmerTextStyle_BodySmMedium,
                color = colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.flow_toast_find),
                style = SimmerTextStyle_Caption,
                color = colors.textSecondary,
            )
        }
        LinkButton(text = stringResource(R.string.flow_view_list), onClick = onViewList)
    }
}
