package com.simmer.app.ui.flow

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simmer.app.R
import com.simmer.app.domain.hasSelectableMethod
import com.simmer.app.domain.StepPlan
import com.simmer.app.domain.methodDescription
import com.simmer.app.domain.model.Method
import com.simmer.app.domain.model.MethodOption
import com.simmer.app.ui.components.OptionCard
import com.simmer.app.ui.components.PrimaryButton
import com.simmer.app.ui.components.SecondaryButton
import com.simmer.app.ui.theme.LocalSimmerColors
import com.simmer.app.ui.theme.SimmerTextStyle_BodyMd
import com.simmer.app.ui.theme.SimmerTextStyle_BodyMdMedium
import com.simmer.app.ui.theme.SimmerTextStyle_BodySmMedium
import com.simmer.app.ui.theme.SimmerTextStyle_Caption
import com.simmer.app.ui.theme.SimmerTextStyle_Heading5
import com.simmer.app.ui.theme.SimmerTextStyle_MicroUppercase
import com.simmer.app.ui.theme.SimmerTextStyle_ScreenTitle

/**
 * Recipe flow, phase 2: choose how to cook. Shares [viewModel] with phase 1 (scoped to the
 * [com.simmer.app.ui.nav.RecipeFlow] back stack entry) so both edit the same selection.
 */
@Composable
fun RecipeFlowPhase2Screen(
    viewModel: RecipeFlowViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onStartCooking: () -> Unit,
    onNothingToPick: () -> Unit,
    onPhaseClick: (phase: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSimmerColors.current
    val ui by viewModel.phase2.collectAsStateWithLifecycle()

    // A restored screen, or an edit made from here, can leave no Method with a choice; this screen would be empty.
    val nothingToPick = (ui as? Phase2Ui.Content)?.let { !hasSelectableMethod(it.recipe) } == true
    LaunchedEffect(nothingToPick) { if (nothingToPick) onNothingToPick() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfacePage)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        when (val u = ui) {
            Phase2Ui.Loading -> Unit
            is Phase2Ui.Content -> Column(modifier = Modifier.fillMaxSize()) {
                FlowTopBar(title = u.recipe.title, phase = 2, onBack = onBack, onEdit = onEdit)
                FlowProgress(current = 2, onSegmentClick = onPhaseClick)

                Column(
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 24.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.flow_methods_title),
                            style = SimmerTextStyle_ScreenTitle,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = stringResource(
                                R.string.flow_methods_caption,
                                pluralStringResource(R.plurals.flow_servings_count, u.servings, u.servings),
                            ),
                            style = SimmerTextStyle_BodyMd,
                            color = colors.textSecondary,
                        )
                    }
                    u.methods.forEach { method ->
                        MethodGroup(
                            method = method,
                            picked = u.picks[method.id],
                            units = u.units,
                            onPick = { viewModel.selectMethod(method.id, it) },
                        )
                    }
                    PlanSummary(u.plan)
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfacePage)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    SecondaryButton(
                        text = stringResource(R.string.flow_back),
                        onClick = onBack,
                        icon = { Icon(painterResource(R.drawable.ic_arrow_left), null, Modifier.size(20.dp)) },
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryButton(
                        text = stringResource(R.string.flow_start_cooking),
                        onClick = onStartCooking,
                        icon = { Icon(painterResource(R.drawable.ic_arrow_right), null, Modifier.size(20.dp)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MethodGroup(method: Method, picked: String?, units: String, onPick: (String) -> Unit) {
    val colors = LocalSimmerColors.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = method.label, style = SimmerTextStyle_MicroUppercase, color = colors.textTertiary)
            Text(text = method.q, style = SimmerTextStyle_Heading5, color = colors.textPrimary)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.selectableGroup()) {
            method.options.forEach { option ->
                MethodOptionCard(
                    option = option,
                    selected = option.value == picked,
                    units = units,
                    onClick = { onPick(option.value) },
                )
            }
        }
    }
}

@Composable
private fun MethodOptionCard(option: MethodOption, selected: Boolean, units: String, onClick: () -> Unit) {
    val colors = LocalSimmerColors.current
    OptionCard(
        selected = selected,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                role = Role.RadioButton
                this.selected = selected
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 72.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            val tile = RoundedCornerShape(8.dp)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .background(colors.surfaceWarm, tile)
                    .border(BorderStroke(2.dp, colors.borderWarm), tile),
            ) {
                Icon(
                    painter = painterResource(methodIcon(option.icon)),
                    contentDescription = null,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                Text(text = option.label, style = SimmerTextStyle_BodyMdMedium, color = colors.textPrimary)
                Text(
                    text = methodDescription(option, units),
                    style = SimmerTextStyle_Caption,
                    color = colors.textTertiary,
                )
            }
            Text(text = option.time, style = SimmerTextStyle_BodySmMedium, color = colors.textSecondary)
        }
    }
}

private fun methodIcon(name: String): Int = when (name) {
    "flame" -> R.drawable.ic_flame
    "zap" -> R.drawable.ic_zap
    "timer" -> R.drawable.ic_timer
    "layers" -> R.drawable.ic_layers
    "clock" -> R.drawable.ic_clock
    else -> R.drawable.ic_utensils
}

@Composable
private fun PlanSummary(plan: StepPlan) {
    val colors = LocalSimmerColors.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceWarm, shape)
            .border(BorderStroke(2.dp, colors.borderWarm), shape)
            .padding(16.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_list_checks),
            contentDescription = null,
            tint = colors.textPrimary,
            modifier = Modifier.size(20.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(
                    R.string.flow_plan,
                    pluralStringResource(R.plurals.flow_prep_steps_count, plan.prep, plan.prep),
                    pluralStringResource(R.plurals.flow_cook_steps_count, plan.cook, plan.cook),
                ),
                style = SimmerTextStyle_BodySmMedium,
                color = colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.flow_prep_first),
                style = SimmerTextStyle_Caption,
                color = colors.textSecondary,
            )
        }
    }
}
