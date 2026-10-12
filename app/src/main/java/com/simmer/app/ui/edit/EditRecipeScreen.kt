package com.simmer.app.ui.edit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simmer.app.R
import com.simmer.app.domain.ConditionWords
import com.simmer.app.domain.IngredientDraft
import com.simmer.app.domain.RECIPE_CATEGORIES
import com.simmer.app.domain.RECIPE_DIFFICULTIES
import com.simmer.app.domain.RecipeDraft
import com.simmer.app.domain.StepDraft
import com.simmer.app.domain.formatDuration
import com.simmer.app.domain.parseQty
import com.simmer.app.ui.components.OptionCard
import com.simmer.app.ui.components.PrimaryButton
import com.simmer.app.ui.components.RowDivider
import com.simmer.app.ui.components.SecondaryButton
import com.simmer.app.ui.components.SimmerTextArea
import com.simmer.app.ui.components.SimmerTextField
import com.simmer.app.ui.recipes.DISPLAY_LABELS
import com.simmer.app.ui.theme.LocalSimmerColors
import com.simmer.app.ui.theme.SimmerTextStyle_BodyMd
import com.simmer.app.ui.theme.SimmerTextStyle_BodyMdMedium
import com.simmer.app.ui.theme.SimmerTextStyle_BodySmMedium
import com.simmer.app.ui.theme.SimmerTextStyle_Caption
import com.simmer.app.ui.theme.SimmerTextStyle_Heading5
import com.simmer.app.ui.theme.SimmerTextStyle_Micro

private val FieldGap = 12.dp

/**
 * Edit recipe: details, ingredients and steps of a built-in recipe or User Recipe,
 * or of the Add recipe draft when the route has no recipe id.
 */
@Composable
fun EditRecipeScreen(
    onClose: () -> Unit,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditRecipeViewModel = hiltViewModel(),
) {
    val colors = LocalSimmerColors.current
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfacePage)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        when (val u = ui) {
            EditUi.Loading -> Unit
            EditUi.NotFound -> {
                TopBar(title = "", onClose = onClose)
                Text(
                    text = stringResource(R.string.edit_not_found),
                    style = SimmerTextStyle_BodyMd,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(16.dp),
                )
            }
            is EditUi.Content -> {
                TopBar(title = u.draft.title, onClose = onClose)
                Column(
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                ) {
                    DetailsSection(u.draft, viewModel)
                    IngredientsSection(u, viewModel)
                    StepsSection(u, viewModel)
                    if (u.draft.canRestore) {
                        IconLinkButton(R.drawable.ic_rotate_ccw, stringResource(R.string.edit_restore)) {
                            viewModel.restoreOriginal(onSaved)
                        }
                    }
                    if (u.draft.canDelete) {
                        IconLinkButton(R.drawable.ic_trash, stringResource(R.string.edit_delete)) {
                            viewModel.delete(onDeleted)
                        }
                    }
                }
                BottomBar(canSave = u.draft.canSave, onCancel = onClose, onSave = { viewModel.save(onSaved) })
            }
        }
    }
}

@Composable
private fun TopBar(title: String, onClose: () -> Unit) {
    val colors = LocalSimmerColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(BorderStroke(2.dp, colors.borderDefault), RoundedCornerShape(8.dp))
                .background(colors.surfaceCard)
                .clickable(role = Role.Button, onClick = onClose),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_x),
                contentDescription = stringResource(R.string.edit_close),
                tint = colors.textPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title.ifBlank { "–" },
                style = SimmerTextStyle_BodySmMedium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.edit_heading),
                style = SimmerTextStyle_Caption,
                color = colors.textTertiary,
            )
        }
    }
}


@Composable
private fun BottomBar(canSave: Boolean, onCancel: () -> Unit, onSave: () -> Unit) {
    val colors = LocalSimmerColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfacePage)
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
    ) {
        RowDivider()
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            SecondaryButton(
                text = stringResource(R.string.edit_cancel),
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                text = stringResource(R.string.edit_save),
                onClick = onSave,
                enabled = canSave,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Details
// ---------------------------------------------------------------------------

@Composable
private fun SectionHeading(text: String, caption: String? = null) {
    val colors = LocalSimmerColors.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = text,
            style = SimmerTextStyle_Heading5,
            color = colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        if (caption != null) {
            Text(text = caption, style = SimmerTextStyle_Caption, color = colors.textTertiary)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsSection(draft: RecipeDraft, vm: EditRecipeViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeading(stringResource(R.string.edit_details))
        SimmerTextField(
            value = draft.title,
            onValueChange = { v -> vm.edit { it.copy(title = v) } },
            label = stringResource(R.string.edit_title),
        )
        SimmerTextArea(
            value = draft.summary,
            onValueChange = { v -> vm.edit { it.copy(summary = v) } },
            label = stringResource(R.string.edit_summary),
            minLines = 3,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(FieldGap)) {
            SimmerTextField(
                value = draft.servings,
                onValueChange = { v -> vm.edit { it.copy(servings = v.filter(Char::isDigit)) } },
                label = stringResource(R.string.edit_servings),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            SimmerTextField(
                value = draft.time,
                onValueChange = { v -> vm.edit { it.copy(time = v.filter(Char::isDigit)) } },
                label = stringResource(R.string.edit_time),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(stringResource(R.string.edit_difficulty))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RECIPE_DIFFICULTIES.forEach { value ->
                    ChoiceButton(
                        label = displayLabel(value),
                        selected = draft.difficulty == value,
                        onClick = { vm.edit { it.copy(difficulty = value) } },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(stringResource(R.string.edit_category))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // A category outside the list (e.g. from a User Recipe) stays selectable.
                (RECIPE_CATEGORIES + listOfNotNull(draft.category.takeIf { it.isNotBlank() && it !in RECIPE_CATEGORIES }))
                    .forEach { value ->
                        ChoiceButton(
                            label = displayLabel(value),
                            selected = draft.category == value,
                            onClick = { vm.edit { it.copy(category = value) } },
                        )
                    }
            }
        }
    }
}

@Composable
private fun displayLabel(raw: String): String = DISPLAY_LABELS[raw]?.let { stringResource(it) } ?: raw

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = SimmerTextStyle_BodySmMedium,
        color = LocalSimmerColors.current.textPrimary,
    )
}

/** One radio-style choice: 2 dp outline, 3 dp orange when selected. */
@Composable
private fun ChoiceButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalSimmerColors.current
    OptionCard(
        selected = selected,
        onClick = onClick,
        modifier = modifier.semantics {
            role = Role.RadioButton
            this.selected = selected
        },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .defaultMinSize(minHeight = 44.dp)
                .padding(horizontal = 14.dp),
        ) {
            Text(
                text = label,
                style = SimmerTextStyle_BodySmMedium,
                color = if (selected) colors.textPrimary else colors.textSecondary,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Accordion pieces
// ---------------------------------------------------------------------------

/** Bordered card of accordion rows with a divider between them. */
@Composable
private fun AccordionCard(content: @Composable () -> Unit) {
    val colors = LocalSimmerColors.current
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceCard, shape)
            .border(BorderStroke(2.dp, colors.borderSubtle), shape)
            .clip(shape),
    ) {
        content()
    }
}

@Composable
private fun AccordionHeader(open: Boolean, onToggle: () -> Unit, content: @Composable (Modifier) -> Unit) {
    val colors = LocalSimmerColors.current
    val state = stringResource(if (open) R.string.edit_collapse else R.string.edit_expand)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(onClickLabel = state, role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = state }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        content(Modifier.weight(1f))
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier
                .size(18.dp)
                .rotate(if (open) 90f else 0f),
        )
    }
}

@Composable
private fun IconLinkButton(icon: Int, text: String, onClick: () -> Unit) {
    val colors = LocalSimmerColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .defaultMinSize(minHeight = 44.dp)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.textLink, modifier = Modifier.size(16.dp))
        Text(text = text, style = SimmerTextStyle_BodySmMedium, color = colors.textLink)
    }
}

@Composable
private fun InfoLine(text: String) {
    val colors = LocalSimmerColors.current
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(
            painterResource(R.drawable.ic_info),
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(14.dp),
        )
        Text(text = text, style = SimmerTextStyle_Caption, color = colors.textSecondary)
    }
}

// ---------------------------------------------------------------------------
// Ingredients
// ---------------------------------------------------------------------------

@Composable
private fun IngredientsSection(ui: EditUi.Content, vm: EditRecipeViewModel) {
    val draft = ui.draft
    Column(verticalArrangement = Arrangement.spacedBy(FieldGap)) {
        SectionHeading(
            text = stringResource(R.string.edit_ingredients),
            caption = stringResource(
                R.string.edit_ingredients_count,
                draft.ingredients.size,
                draft.servings.ifBlank { draft.base.base.toString() },
            ),
        )
        if (draft.ingredients.isNotEmpty()) {
            AccordionCard {
                draft.ingredients.forEachIndexed { i, ing ->
                    if (i > 0) RowDivider()
                    key(ing.key) {
                        IngredientRow(
                            ing = ing,
                            draft = draft,
                            open = ui.openIngredient == ing.key,
                            vm = vm,
                        )
                    }
                }
            }
        }
        SecondaryButton(
            text = stringResource(R.string.edit_add_ingredient),
            onClick = vm::addIngredient,
            icon = { Icon(painterResource(R.drawable.ic_plus), contentDescription = null, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun IngredientRow(ing: IngredientDraft, draft: RecipeDraft, open: Boolean, vm: EditRecipeViewModel) {
    val colors = LocalSimmerColors.current
    val change = { patch: (IngredientDraft) -> IngredientDraft -> vm.edit { it.updateIngredient(ing.key, patch) } }
    val meta = listOf(ing.note, ing.aisle).filter { it.isNotBlank() }.joinToString(" · ")
    AccordionHeader(open = open, onToggle = { vm.toggleIngredient(ing.key) }) { mod ->
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = mod) {
            Text(
                text = ing.name.ifBlank { stringResource(R.string.edit_new_ingredient) },
                style = SimmerTextStyle_BodyMd,
                color = colors.textPrimary,
            )
            if (meta.isNotEmpty()) {
                Text(text = meta, style = SimmerTextStyle_Caption, color = colors.textTertiary)
            }
        }
        Text(
            text = listOf(ing.qty, ing.unit).filter { it.isNotBlank() }.joinToString(" "),
            style = SimmerTextStyle_BodySmMedium,
            color = colors.textSecondary,
        )
    }
    if (open) {
        Column(
            verticalArrangement = Arrangement.spacedBy(FieldGap),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        ) {
            SimmerTextField(
                value = ing.name,
                onValueChange = { v -> change { it.copy(name = v) } },
                label = stringResource(R.string.edit_name),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(FieldGap)) {
                SimmerTextField(
                    value = ing.qty,
                    onValueChange = { v -> change { it.copy(qty = v) } },
                    label = stringResource(R.string.edit_amount),
                    // Text keyboard, so 1/2, 1 1/2 and the fraction glyphs can be typed.
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.weight(1f),
                )
                SimmerTextField(
                    value = ing.unit,
                    onValueChange = { v -> change { it.copy(unit = v) } },
                    label = stringResource(R.string.edit_unit),
                    modifier = Modifier.weight(1f),
                )
            }
            SimmerTextField(
                value = ing.note,
                onValueChange = { v -> change { it.copy(note = v) } },
                label = stringResource(R.string.edit_note),
            )
            SimmerTextField(
                value = ing.aisle,
                onValueChange = { v -> change { it.copy(aisle = v) } },
                label = stringResource(R.string.edit_aisle),
            )
            ing.ifCondition?.takeIf { it.isNotBlank() }?.let { cond ->
                InfoLine(stringResource(R.string.edit_only_when, draft.conditionLabel(cond, conditionWords())))
            }
            IconLinkButton(R.drawable.ic_trash, stringResource(R.string.edit_remove_ingredient)) {
                vm.edit { it.removeIngredient(ing.key) }
            }
        }
    }
}

@Composable
private fun conditionWords() = ConditionWords(
    not = stringResource(R.string.edit_cond_not, "%s"),
    added = stringResource(R.string.edit_cond_added, "%s"),
    notAdded = stringResource(R.string.edit_cond_not_added, "%s"),
)

// ---------------------------------------------------------------------------
// Steps
// ---------------------------------------------------------------------------

@Composable
private fun StepsSection(ui: EditUi.Content, vm: EditRecipeViewModel) {
    listOf("prep", "cook").forEach { phase ->
        val steps = ui.draft.stepsIn(phase)
        Column(verticalArrangement = Arrangement.spacedBy(FieldGap)) {
            SectionHeading(
                text = stringResource(if (phase == "prep") R.string.flow_phase_preparation else R.string.flow_phase_cooking),
                caption = stringResource(R.string.edit_steps_hint),
            )
            if (steps.isNotEmpty()) {
                AccordionCard {
                    steps.forEachIndexed { i, step ->
                        if (i > 0) RowDivider()
                        key(step.key) {
                            StepRow(step = step, number = i + 1, draft = ui.draft, open = ui.openStep == step.key, vm = vm)
                        }
                    }
                }
            }
            SecondaryButton(
                text = stringResource(if (phase == "prep") R.string.edit_add_prep else R.string.edit_add_cook),
                onClick = { vm.addStep(phase) },
                icon = { Icon(painterResource(R.drawable.ic_plus), contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** "5 min", "45 s", or a dash while the timer text is not a number. */
private fun timerText(minutes: String): String {
    val m = parseQty(minutes)
    return if (m > 0) formatDuration(Math.round(m * 60).toInt()) else "–"
}

@Composable
private fun StepRow(step: StepDraft, number: Int, draft: RecipeDraft, open: Boolean, vm: EditRecipeViewModel) {
    val colors = LocalSimmerColors.current
    val change = { patch: (StepDraft) -> StepDraft -> vm.edit { it.updateStep(step.key, patch) } }
    AccordionHeader(open = open, onToggle = { vm.toggleStep(step.key) }) { mod ->
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = mod) {
            Text(
                text = stringResource(if (step.phase == "prep") R.string.edit_prep_n else R.string.edit_step_n, number),
                style = SimmerTextStyle_Micro,
                color = colors.textTertiary,
            )
            Text(
                text = step.title.ifBlank { stringResource(R.string.edit_new_step) },
                style = SimmerTextStyle_BodyMdMedium,
                color = colors.textPrimary,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(painterResource(R.drawable.ic_timer), contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
            Text(text = timerText(step.minutes), style = SimmerTextStyle_Caption, color = colors.textSecondary)
        }
    }
    if (open) {
        val linked = draft.linkedNames(step.key)
        Column(
            verticalArrangement = Arrangement.spacedBy(FieldGap),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        ) {
            SimmerTextField(
                value = step.title,
                onValueChange = { v -> change { it.copy(title = v) } },
                label = stringResource(R.string.edit_step_title),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SimmerTextArea(
                    value = step.text,
                    onValueChange = { v -> change { it.copy(text = v) } },
                    label = stringResource(R.string.edit_instructions),
                    placeholder = stringResource(R.string.edit_instructions_placeholder),
                    minLines = 4,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        painterResource(R.drawable.ic_utensils),
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(14.dp),
                    )
                    Text(
                        text = if (linked.isEmpty()) stringResource(R.string.edit_no_links)
                        else stringResource(R.string.edit_linked, linked.joinToString(", ")),
                        style = SimmerTextStyle_Caption,
                        color = colors.textSecondary,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(FieldGap), verticalAlignment = Alignment.Bottom) {
                SimmerTextField(
                    value = step.minutes,
                    onValueChange = { v -> change { it.copy(minutes = v) } },
                    label = stringResource(R.string.edit_timer),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1.4f)) {
                    FieldLabel(stringResource(R.string.edit_phase))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("prep" to R.string.flow_phase_preparation, "cook" to R.string.flow_phase_cooking).forEach { (value, label) ->
                            ChoiceButton(
                                label = stringResource(label),
                                selected = step.phase == value,
                                onClick = { vm.edit { it.setPhase(step.key, value) } },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            step.ifCondition?.takeIf { it.isNotBlank() }?.let { cond ->
                InfoLine(stringResource(R.string.edit_only_when, draft.conditionLabel(cond, conditionWords())))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(
                    text = stringResource(R.string.edit_move_up),
                    onClick = { vm.edit { it.moveStep(step.key, -1) } },
                    enabled = draft.canMove(step.key, -1),
                )
                SecondaryButton(
                    text = stringResource(R.string.edit_move_down),
                    onClick = { vm.edit { it.moveStep(step.key, 1) } },
                    enabled = draft.canMove(step.key, 1),
                )
            }
            IconLinkButton(R.drawable.ic_trash, stringResource(R.string.edit_remove_step)) {
                vm.edit { it.removeStep(step.key) }
            }
        }
    }
}
