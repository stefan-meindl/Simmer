package com.simmer.app.ui.add

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simmer.app.R
import com.simmer.app.domain.KitchenLine
import com.simmer.app.domain.ReaderAction
import com.simmer.app.domain.ReviewChoice
import com.simmer.app.domain.ReviewIngredient
import com.simmer.app.domain.formatDuration
import com.simmer.app.domain.model.Recipe
import com.simmer.app.domain.model.ResolvedIngredient
import com.simmer.app.domain.model.ResolvedStep
import com.simmer.app.ui.components.GradientHeroCard
import com.simmer.app.ui.components.LinkButton
import com.simmer.app.ui.components.PrimaryButton
import com.simmer.app.ui.components.RowCard
import com.simmer.app.ui.components.RowDivider
import com.simmer.app.ui.components.SecondaryButton
import com.simmer.app.ui.components.SimmerTextArea
import com.simmer.app.ui.components.UnderlineTabs
import com.simmer.app.ui.components.recipeIconRes
import com.simmer.app.ui.flow.StepBody
import com.simmer.app.ui.flow.formatMinutes
import com.simmer.app.ui.recipes.displayLabel
import com.simmer.app.ui.settings.applianceLabel
import com.simmer.app.ui.theme.LocalSimmerColors
import com.simmer.app.ui.theme.SimmerTextStyle_BodyMd
import com.simmer.app.ui.theme.SimmerTextStyle_BodyMdMedium
import com.simmer.app.ui.theme.SimmerTextStyle_BodySm
import com.simmer.app.ui.theme.SimmerTextStyle_BodySmMedium
import com.simmer.app.ui.theme.SimmerTextStyle_Caption
import com.simmer.app.ui.theme.SimmerTextStyle_Heading5
import com.simmer.app.ui.theme.SimmerTextStyle_HeroTitle
import com.simmer.app.ui.theme.SimmerTextStyle_Micro
import com.simmer.app.ui.theme.SimmerTextStyle_MicroUppercase
import com.simmer.app.ui.theme.SimmerTextStyle_ScreenTitle
import kotlinx.coroutines.launch

/**
 * Add recipe: paste a recipe's text, convert it, review the result and save it.
 * Without the on-device recipe reader only the built-in example converts; other text shows the
 * "Download recipe reader" placeholder.
 */
@Composable
fun AddRecipeScreen(
    onBack: () -> Unit,
    onChangeKitchen: () -> Unit,
    onEditDetails: () -> Unit,
    onSaved: (recipeId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddRecipeViewModel = hiltViewModel(),
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
            AddRecipeUi.Loading -> Unit
            is AddRecipeUi.Paste -> PasteStage(u, viewModel, onBack, onChangeKitchen)
            is AddRecipeUi.Review -> ReviewStage(u, viewModel, onBack, onEditDetails, onSaved)
        }
    }
}

// ---------------------------------------------------------------------------
// Shared chrome
// ---------------------------------------------------------------------------

@Composable
private fun TopRow(caption: String, onBack: () -> Unit) {
    val colors = LocalSimmerColors.current
    val shape = RoundedCornerShape(8.dp)
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
                .clip(shape)
                .border(BorderStroke(2.dp, colors.borderDefault), shape)
                .background(colors.surfaceCard)
                .clickable(role = Role.Button, onClick = onBack),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_left),
                contentDescription = stringResource(R.string.flow_back),
                tint = colors.textPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(text = caption, style = SimmerTextStyle_Caption, color = colors.textTertiary)
    }
}

/** Sticky bar above the navigation bar and the keyboard. */
@Composable
private fun BottomBar(content: @Composable () -> Unit) {
    val colors = LocalSimmerColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfacePage)
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
    ) {
        RowDivider()
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) { content() }
    }
}

@Composable
private fun icon(res: Int, size: Int = 20, tint: androidx.compose.ui.graphics.Color? = null) {
    Icon(painterResource(res), contentDescription = null, tint = tint ?: androidx.compose.ui.graphics.Color.Unspecified, modifier = Modifier.size(size.dp))
}

// ---------------------------------------------------------------------------
// Paste stage
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.PasteStage(
    ui: AddRecipeUi.Paste,
    viewModel: AddRecipeViewModel,
    onBack: () -> Unit,
    onChangeKitchen: () -> Unit,
) {
    val colors = LocalSimmerColors.current
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val textLabel = stringResource(R.string.add_recipe_text)

    TopRow(stringResource(R.string.add_caption_paste), onBack)
    Column(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.add_title),
                style = SimmerTextStyle_ScreenTitle,
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(text = stringResource(R.string.add_intro), style = SimmerTextStyle_BodyMd, color = colors.textSecondary)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 44.dp),
            ) {
                Text(text = textLabel, style = SimmerTextStyle_BodySmMedium, color = colors.textPrimary)
                if (ui.text.isNotEmpty()) {
                    LinkButton(text = stringResource(R.string.add_clear), onClick = viewModel::clear)
                } else {
                    SecondaryButton(
                        text = stringResource(R.string.add_paste),
                        onClick = {
                            scope.launch {
                                val pasted = clipboard.getClipEntry()?.clipData
                                    ?.takeIf { it.itemCount > 0 }
                                    ?.getItemAt(0)?.coerceToText(context)?.toString()
                                if (pasted.isNullOrEmpty()) focus.requestFocus() else viewModel.setText(pasted)
                            }
                        },
                        icon = { icon(R.drawable.ic_copy, 16) },
                    )
                }
            }
            SimmerTextArea(
                value = ui.text,
                onValueChange = viewModel::setText,
                placeholder = stringResource(R.string.add_placeholder),
                minLines = 13,
                fieldModifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .semantics { contentDescription = textLabel },
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.add_helper),
                    style = SimmerTextStyle_Caption,
                    color = colors.textTertiary,
                    modifier = Modifier.weight(1f),
                )
                LinkButton(text = stringResource(R.string.add_try_example), onClick = viewModel::useExample)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.add_next_title).uppercase(),
                style = SimmerTextStyle_MicroUppercase,
                color = colors.textTertiary,
                modifier = Modifier.semantics { heading() },
            )
            listOf(
                R.drawable.ic_list_checks to R.string.add_next_1,
                R.drawable.ic_utensils to R.string.add_next_2,
                R.drawable.ic_timer to R.string.add_next_3,
            ).forEach { (iconRes, text) ->
                NextRow(iconRes) { Text(stringResource(text), style = SimmerTextStyle_BodySm, color = colors.textSecondary) }
            }
            NextRow(R.drawable.ic_chef_hat) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = kitchenText(ui.kitchen),
                        style = SimmerTextStyle_BodySm,
                        color = colors.textSecondary,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                    LinkButton(text = stringResource(R.string.add_change), onClick = onChangeKitchen)
                }
            }
        }
    }

    BottomBar {
        when (ui.action) {
            ReaderAction.Convert -> PrimaryButton(
                text = stringResource(R.string.add_convert),
                onClick = viewModel::convert,
                enabled = ui.canConvert,
                icon = { icon(R.drawable.ic_sparkles) },
                modifier = Modifier.fillMaxWidth(),
            )
            ReaderAction.Download -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    icon(R.drawable.ic_cpu, 14, colors.textSecondary)
                    Text(
                        text = stringResource(R.string.add_reader_note),
                        style = SimmerTextStyle_Caption,
                        color = colors.textSecondary,
                    )
                }
                // Inactive placeholder: the on-device recipe reader and its download come later.
                PrimaryButton(
                    text = stringResource(R.string.add_download),
                    onClick = {},
                    enabled = false,
                    icon = { icon(R.drawable.ic_download) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun NextRow(iconRes: Int, content: @Composable () -> Unit) {
    val colors = LocalSimmerColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        icon(iconRes, 18, colors.textPrimary)
        Box(modifier = Modifier.weight(1f)) { content() }
    }
}

/** "Prep 2 and Prep 3": the labels joined with the language's "and". */
@Composable
private fun joinWithAnd(labels: List<String>): String = labels.reduce { a, b -> stringResource(R.string.add_and, a, b) }

@Composable
private fun kitchenText(line: KitchenLine): String = when (line) {
    KitchenLine.AnyAppliance -> stringResource(R.string.add_next_kitchen_any)
    is KitchenLine.OnlyYours -> stringResource(
        R.string.add_next_kitchen_only,
        if (line.appliances.isEmpty()) stringResource(R.string.add_next_kitchen_none)
        else line.appliances.map { applianceLabel(it) }.joinToString(", "),
    )
}

// ---------------------------------------------------------------------------
// Review stage
// ---------------------------------------------------------------------------

@Composable
private fun ColumnScope.ReviewStage(
    ui: AddRecipeUi.Review,
    viewModel: AddRecipeViewModel,
    onBack: () -> Unit,
    onEditDetails: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val review = ui.review
    val recipe = review.recipe

    TopRow(stringResource(R.string.add_caption_review), onBack)
    Column(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Hero(review.recipe)
            LinkButton(text = stringResource(R.string.add_edit_details), onClick = onEditDetails)
        }
        if (recipe.notes.isNotEmpty()) CheckNote(recipe.notes)
        if (review.choices.isNotEmpty()) ChoicesFound(review.choices)

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val colors = LocalSimmerColors.current
            UnderlineTabs(
                tabs = listOf(stringResource(R.string.add_tab_ingredients), stringResource(R.string.add_tab_steps)),
                selectedIndex = ui.tab.ordinal,
                onTabSelected = { viewModel.selectTab(ReviewTab.entries[it]) },
            )
            Text(
                text = stringResource(
                    R.string.add_counts,
                    pluralStringResource(R.plurals.add_ingredients_count, review.ingredientCount, review.ingredientCount),
                    pluralStringResource(
                        R.plurals.add_steps_count, review.cookSteps.size, review.prepSteps.size, review.cookSteps.size,
                    ),
                ),
                style = SimmerTextStyle_Caption,
                color = colors.textTertiary,
            )
            when (ui.tab) {
                ReviewTab.Ingredients -> IngredientsTab(review.ingredients)
                ReviewTab.Steps -> StepsTab(ui)
            }
        }
    }

    BottomBar {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            SecondaryButton(
                text = stringResource(R.string.add_edit_text),
                onClick = viewModel::editText,
                icon = { icon(R.drawable.ic_arrow_left) },
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                text = stringResource(R.string.add_save),
                onClick = { viewModel.save(onSaved) },
                icon = { icon(R.drawable.ic_check) },
                modifier = Modifier.weight(1.4f),
            )
        }
    }
}

@Composable
private fun Hero(recipe: Recipe) {
    val colors = LocalSimmerColors.current
    GradientHeroCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(20.dp)) {
            Text(
                text = displayLabel(recipe.category).uppercase(),
                style = SimmerTextStyle_MicroUppercase,
                color = colors.textPrimary,
            )
            Text(text = recipe.title, style = SimmerTextStyle_HeroTitle, color = colors.textPrimary)
            if (recipe.summary.isNotBlank()) {
                Text(text = recipe.summary, style = SimmerTextStyle_BodyMd, color = colors.textHero)
            }
            FlowRowMeta(
                R.drawable.ic_clock to formatMinutes(recipe.time),
                R.drawable.ic_flame to displayLabel(recipe.difficulty),
                R.drawable.ic_users to pluralStringResource(R.plurals.flow_servings_count, recipe.base, recipe.base),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowMeta(vararg items: Pair<Int, String>) {
    val colors = LocalSimmerColors.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { (res, text) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                icon(res, 16, colors.textHero)
                Text(text = text, style = SimmerTextStyle_BodySm, color = colors.textHero)
            }
        }
    }
}

/** Cream note, orange outline: what the reader estimated, converted or could not read. */
@Composable
private fun CheckNote(notes: List<String>) {
    val colors = LocalSimmerColors.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceWarm, shape)
            .border(BorderStroke(2.dp, colors.accent), shape)
            .padding(16.dp),
    ) {
        icon(R.drawable.ic_circle_alert, 20, colors.textAccent)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.add_check_first),
                style = SimmerTextStyle_BodySmMedium,
                color = colors.textPrimary,
            )
            notes.forEach { note ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("•", style = SimmerTextStyle_Caption, color = colors.textSecondary)
                    Text(note, style = SimmerTextStyle_Caption, color = colors.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun ChoicesFound(choices: List<ReviewChoice>) {
    val colors = LocalSimmerColors.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.add_choices_found),
                style = SimmerTextStyle_Heading5,
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(text = stringResource(R.string.add_choices_hint), style = SimmerTextStyle_Caption, color = colors.textTertiary)
        }
        RowCard {
            choices.forEachIndexed { i, choice ->
                if (i > 0) RowDivider()
                val (iconRes, label, detail) = when (choice) {
                    is ReviewChoice.Method -> Triple(recipeIconRes(choice.icon), choice.label, choice.options.joinToString(" · "))
                    is ReviewChoice.AddIn -> Triple(
                        R.drawable.ic_plus,
                        choice.label,
                        listOf(stringResource(R.string.add_add_in), choice.description).filter { it.isNotBlank() }.joinToString(" · "),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 64.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    val tile = RoundedCornerShape(8.dp)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .background(colors.surfaceWarm, tile)
                            .border(BorderStroke(2.dp, colors.borderWarm), tile),
                    ) {
                        icon(iconRes, 20, colors.textPrimary)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                        Text(label, style = SimmerTextStyle_BodyMdMedium, color = colors.textPrimary)
                        Text(detail, style = SimmerTextStyle_Caption, color = colors.textTertiary)
                    }
                }
            }
        }
    }
}

@Composable
private fun IngredientsTab(ingredients: List<ReviewIngredient>) {
    val colors = LocalSimmerColors.current
    RowCard {
        ingredients.forEachIndexed { i, ing ->
            if (i > 0) RowDivider()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(ing.name, style = SimmerTextStyle_BodyMd, color = colors.textPrimary)
                        if (ing.optional) {
                            Text(stringResource(R.string.add_optional), style = SimmerTextStyle_Micro, color = colors.textAccent)
                        }
                    }
                    Text(
                        text = listOf(ing.note, ing.aisle).filter { it.isNotBlank() }.joinToString(" · ")
                            .ifEmpty { stringResource(R.string.add_no_aisle) },
                        style = SimmerTextStyle_Caption,
                        color = colors.textTertiary,
                    )
                }
                Text(ing.amount, style = SimmerTextStyle_BodySmMedium, color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun StepsTab(ui: AddRecipeUi.Review) {
    val colors = LocalSimmerColors.current
    val review = ui.review
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (review.defaultMethodLabels.isNotEmpty()) {
            val joined = joinWithAnd(review.defaultMethodLabels)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                icon(R.drawable.ic_info, 16, colors.textTertiary)
                Text(
                    text = stringResource(R.string.add_default_note, joined),
                    style = SimmerTextStyle_Caption,
                    color = colors.textTertiary,
                )
            }
        }
        listOf(
            R.string.flow_phase_preparation to review.prepSteps,
            R.string.flow_phase_cooking to review.cookSteps,
        ).filter { it.second.isNotEmpty() }.forEach { (title, steps) ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(title),
                    style = SimmerTextStyle_Heading5,
                    color = colors.textPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                steps.forEach { StepCard(it, review.ingredientsById) }
            }
        }
    }
}

@Composable
private fun StepCard(step: ResolvedStep, ingredients: Map<String, ResolvedIngredient>) {
    val colors = LocalSimmerColors.current
    val shape = RoundedCornerShape(12.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceCard, shape)
            .border(BorderStroke(2.dp, colors.borderSubtle), shape)
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(step.label, style = SimmerTextStyle_MicroUppercase, color = colors.textTertiary)
            // Timers are optional: a step without one shows no duration.
            if (step.durationSecs > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    icon(R.drawable.ic_timer, 14, colors.textSecondary)
                    Text(formatDuration(step.durationSecs), style = SimmerTextStyle_Caption, color = colors.textSecondary)
                }
            }
        }
        Text(step.title, style = SimmerTextStyle_BodyMdMedium, color = colors.textPrimary)
        if (step.needs.isNotEmpty()) {
            val joined = joinWithAnd(step.needs.map { it.label })
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                icon(R.drawable.ic_circle_alert, 14, colors.textAccent)
                Text(stringResource(R.string.flow_needs_first, joined), style = SimmerTextStyle_Caption, color = colors.textPrimary)
            }
        }
        StepBody(step.segs, ingredients)
    }
}
