package com.simmer.app.ui.recipes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simmer.app.R
import com.simmer.app.domain.FILTER_TAGS
import com.simmer.app.domain.PlannedEntry
import com.simmer.app.domain.RecipesOverview
import com.simmer.app.domain.model.Recipe
import com.simmer.app.domain.selectionSummary
import com.simmer.app.ui.components.Badge
import com.simmer.app.ui.components.BadgeVariant
import com.simmer.app.ui.components.FilterChip
import com.simmer.app.ui.components.GradientHeroCard
import com.simmer.app.ui.components.OptionCard
import com.simmer.app.ui.components.PrimaryButton
import com.simmer.app.ui.components.SecondaryButton
import com.simmer.app.ui.components.SimmerTextField
import com.simmer.app.ui.components.UnderlineTabs
import com.simmer.app.ui.components.recipeIconRes
import com.simmer.app.ui.flow.formatMinutes
import com.simmer.app.ui.theme.LocalSimmerColors
import com.simmer.app.ui.theme.SimmerTextStyle_BodyMdMedium
import com.simmer.app.ui.theme.SimmerTextStyle_BodySm
import com.simmer.app.ui.theme.SimmerTextStyle_BodySmMedium
import com.simmer.app.ui.theme.SimmerTextStyle_Caption
import com.simmer.app.ui.theme.SimmerTextStyle_ScreenTitle

/** The Recipes tab: browse, search and filter recipes, resume Planned ones, open the cooking flow. */
@Composable
fun RecipesScreen(
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
    onAddRecipe: () -> Unit,
    onOpenRecipe: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecipesViewModel = hiltViewModel(),
) {
    val colors = LocalSimmerColors.current
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val content = ui as? RecipesUi.Content

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfacePage)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") {
                Header(isDarkTheme, onThemeToggle, onAddRecipe)
            }
            item(key = "search") {
                SimmerTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    label = stringResource(R.string.recipes_search_label),
                    placeholder = stringResource(R.string.recipes_search_placeholder),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                )
            }
            if (content != null) {
                val overview = content.overview
                item(key = "tabs") {
                    val labels = listOf(stringResource(R.string.recipes_all)) +
                        overview.categories.map { displayLabel(it) }
                    UnderlineTabs(
                        tabs = labels,
                        selectedIndex = overview.selectedTab,
                        onTabSelected = { viewModel.selectCategory(overview.categoryForTab(it)) },
                        scrollable = true,
                    )
                }
                item(key = "chips") {
                    TagChips(overview.tags, viewModel::toggleTag)
                }
                if (overview.planned.isNotEmpty()) {
                    item(key = "planned-title") { SectionTitle(stringResource(R.string.recipes_planned)) }
                    items(overview.planned, key = { "planned-" + it.recipe.id }) { entry ->
                        PlannedCard(entry, onContinue = { onOpenRecipe(entry.recipe.id) })
                    }
                }
                item(key = "count") {
                    CountRow(overview, onClear = viewModel::clearFilters)
                }
                if (overview.recipes.isEmpty()) {
                    item(key = "empty") { EmptyState(onClear = viewModel::clearFilters) }
                } else {
                    items(overview.recipes, key = { it.id }) { recipe ->
                        RecipeCard(recipe, onClick = { onOpenRecipe(recipe.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(isDarkTheme: Boolean, onThemeToggle: () -> Unit, onAddRecipe: () -> Unit) {
    val colors = LocalSimmerColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.recipes_title),
            style = SimmerTextStyle_ScreenTitle,
            color = colors.textPrimary,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        IconButton(onClick = onAddRecipe) {
            Icon(
                painter = painterResource(R.drawable.ic_plus),
                contentDescription = stringResource(R.string.recipes_add),
                tint = colors.textPrimary,
            )
        }
        IconButton(onClick = onThemeToggle) {
            Icon(
                painter = painterResource(if (isDarkTheme) R.drawable.ic_sun else R.drawable.ic_moon),
                contentDescription = stringResource(
                    if (isDarkTheme) R.string.recipes_theme_to_light else R.string.recipes_theme_to_dark,
                ),
                tint = colors.textPrimary,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagChips(selected: Set<String>, onToggle: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        FILTER_TAGS.forEach { tag ->
            FilterChip(label = displayLabel(tag), selected = tag in selected, onToggle = { onToggle(tag) })
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = SimmerTextStyle_BodyMdMedium,
        color = LocalSimmerColors.current.textPrimary,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun PlannedCard(entry: PlannedEntry, onContinue: () -> Unit) {
    val colors = LocalSimmerColors.current
    val servings = pluralStringResource(R.plurals.flow_servings_count, entry.choice.servings, entry.choice.servings)
    val summary = selectionSummary(entry.recipe, entry.choice.selection, servings)
    val continueLabel = stringResource(R.string.recipes_continue_description, entry.recipe.title)
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceWarm, shape)
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(entry.recipe.title, style = SimmerTextStyle_BodyMdMedium, color = colors.textPrimary)
            Text(summary, style = SimmerTextStyle_BodySm, color = colors.textSecondary)
        }
        PrimaryButton(
            text = stringResource(R.string.recipes_continue),
            onClick = onContinue,
            modifier = Modifier.semantics { contentDescription = continueLabel },
        )
    }
}

@Composable
private fun CountRow(overview: RecipesOverview, onClear: () -> Unit) {
    val colors = LocalSimmerColors.current
    val count = overview.recipes.size
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = pluralStringResource(R.plurals.recipes_count, count, count),
            style = SimmerTextStyle_BodySm,
            color = colors.textSecondary,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (overview.filtersActive) {
            TextButton(onClick = onClear, modifier = Modifier.defaultMinSize(minHeight = 44.dp)) {
                Text(
                    text = stringResource(R.string.recipes_clear_filters),
                    style = SimmerTextStyle_BodySmMedium,
                    color = colors.textLink,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(onClear: () -> Unit) {
    val colors = LocalSimmerColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color = colors.borderDefault,
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f))),
                )
            }
            .padding(24.dp),
    ) {
        Text(
            text = stringResource(R.string.recipes_empty),
            style = SimmerTextStyle_BodyMdMedium,
            color = colors.textSecondary,
        )
        SecondaryButton(text = stringResource(R.string.recipes_clear_filters), onClick = onClear)
    }
}

@Composable
private fun RecipeCard(recipe: Recipe, onClick: () -> Unit) {
    val colors = LocalSimmerColors.current
    OptionCard(selected = false, onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(12.dp),
        ) {
            GradientHeroCard(modifier = Modifier.size(72.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        painter = painterResource(recipeIconRes(recipe.icon)),
                        contentDescription = null,
                        tint = colors.textPrimary,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                Text(recipe.title, style = SimmerTextStyle_BodyMdMedium, color = colors.textPrimary)
                CardBadges(recipe)
                Text(
                    text = stringResource(
                        R.string.recipes_meta,
                        formatMinutes(recipe.time),
                        displayLabel(recipe.difficulty),
                    ),
                    style = SimmerTextStyle_BodySm,
                    color = colors.textSecondary,
                )
                if (recipe.variations.isNotEmpty()) {
                    val n = recipe.variations.size
                    Text(
                        text = pluralStringResource(R.plurals.recipes_variations, n, n),
                        style = SimmerTextStyle_BodySmMedium,
                        color = colors.textAccent,
                    )
                }
                if (recipe.tags.isNotEmpty()) {
                    Text(
                        text = recipe.tags.map { displayLabel(it) }.joinToString(" · "),
                        style = SimmerTextStyle_Caption,
                        color = colors.textTertiary,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardBadges(recipe: Recipe) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Badge(label = displayLabel(recipe.category), variant = BadgeVariant.Cream)
        if (recipe.mine) Badge(label = stringResource(R.string.recipes_mine), variant = BadgeVariant.Primary)
        if (recipe.edited) Badge(label = stringResource(R.string.recipes_edited), variant = BadgeVariant.Dark)
    }
}

/** Stored (English) category, tag and difficulty values that have a translation. */
internal val DISPLAY_LABELS: Map<String, Int> = mapOf(
    "Mains" to R.string.category_mains,
    "Pasta" to R.string.category_pasta,
    "Breakfast" to R.string.category_breakfast,
    "Sides" to R.string.category_sides,
    "Vegetarian" to R.string.tag_vegetarian,
    "Quick" to R.string.tag_quick,
    "Spicy" to R.string.tag_spicy,
    "One-pot" to R.string.tag_one_pot,
    "Make-ahead" to R.string.tag_make_ahead,
    "Easy" to R.string.difficulty_easy,
    "Medium" to R.string.difficulty_medium,
    "Hard" to R.string.difficulty_hard,
)

/** Localised category, tag or difficulty; values without a translation (User Recipes) show as written. */
@Composable
private fun displayLabel(raw: String): String = DISPLAY_LABELS[raw]?.let { stringResource(it) } ?: raw
