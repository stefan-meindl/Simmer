package com.simmer.app.ui.shopping

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.simmer.app.ui.components.MenuAction
import com.simmer.app.ui.components.OverflowMenu
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simmer.app.R
import com.simmer.app.domain.GracePeriod
import com.simmer.app.domain.GracePhase
import com.simmer.app.domain.ShoppingItem
import com.simmer.app.ui.components.CheckRow
import com.simmer.app.ui.components.PrimaryButton
import com.simmer.app.ui.components.RowCard
import com.simmer.app.ui.components.RowDivider
import com.simmer.app.ui.components.SimmerTextField
import com.simmer.app.ui.theme.LocalSimmerColors
import com.simmer.app.ui.theme.SimmerTextStyle_BodyMd
import com.simmer.app.ui.theme.SimmerTextStyle_BodySm
import com.simmer.app.ui.theme.SimmerTextStyle_MicroUppercase
import com.simmer.app.ui.theme.SimmerTextStyle_ScreenTitle

/** The Shopping list tab: aisle groups, manual items, and a collapsed "In your basket" section. */
@Composable
fun ShoppingListScreen(
    modifier: Modifier = Modifier,
    viewModel: ShoppingListViewModel = hiltViewModel(),
) {
    val colors = LocalSimmerColors.current
    val listView by viewModel.listView.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val basketOpen by viewModel.basketOpen.collectAsStateWithLifecycle()
    val phases by viewModel.gracePhases.collectAsStateWithLifecycle()
    val canClearTicked by viewModel.canClearTicked.collectAsStateWithLifecycle()
    val canClearAll by viewModel.canClearAll.collectAsStateWithLifecycle()
    val cleared by viewModel.cleared.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    // "Cleared N items · Undo": the view model owns the 5 s timer and clears the state, so a rotation or
    // returning to the tab does not show it again.
    val clearedMessage = cleared?.let { pluralStringResource(R.plurals.shopping_cleared, it.items.size, it.items.size) }
    val undoLabel = stringResource(R.string.shopping_undo)
    LaunchedEffect(cleared) {
        if (cleared == null) {
            snackbar.currentSnackbarData?.dismiss()
            return@LaunchedEffect
        }
        val result = snackbar.showSnackbar(
            message = clearedMessage.orEmpty(),
            actionLabel = undoLabel,
            duration = SnackbarDuration.Indefinite,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoClear() else viewModel.dismissCleared()
    }

    // The system "remove animations" setting (animator duration scale 0): keep the strikethrough, skip fade and collapse.
    // Read on each recomposition, not observed live: a change made while the tab is open applies after the next tick or recomposition.
    val context = LocalContext.current
    val reducedMotion = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    val onChecked: (String, Boolean) -> Unit = { id, checked -> viewModel.setChecked(id, checked, reducedMotion) }

    // Leaving the tab or backgrounding the app releases every held row to the basket; a rotation does not.
    DisposableEffect(Unit) {
        onDispose { if (!context.findActivity().isChangingConfigurations) viewModel.leaveScreen() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (!context.findActivity().isChangingConfigurations) viewModel.leaveScreen()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfacePage)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        val view = listView ?: return@Box
        LazyColumn(
            contentPadding = PaddingValues(vertical = 12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "title") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.shopping_title),
                        style = SimmerTextStyle_ScreenTitle,
                        color = colors.textPrimary,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { heading() },
                    )
                    val actions = buildList {
                        if (canClearTicked) add(MenuAction(stringResource(R.string.shopping_clear_ticked), viewModel::clearTicked))
                        if (canClearAll) add(MenuAction(stringResource(R.string.shopping_clear_all), viewModel::clearAll))
                    }
                    if (actions.isNotEmpty()) {
                        OverflowMenu(contentDescription = stringResource(R.string.shopping_menu), actions = actions)
                    }
                }
            }
            item(key = "add") {
                AddItemRow(
                    draft = draft,
                    onDraftChange = viewModel::setDraft,
                    onAdd = viewModel::addDraft,
                )
            }
            if (view.aisles.isEmpty() && view.manual.isEmpty() && view.basket.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.shopping_empty),
                        style = SimmerTextStyle_BodyMd,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            view.aisles.forEach { group ->
                item(key = "aisle-${group.aisle}") {
                    ItemGroup(label = group.aisle, items = group.items, onChecked = onChecked, phases = phases, reducedMotion = reducedMotion)
                }
            }
            if (view.manual.isNotEmpty()) {
                item(key = "manual") {
                    ItemGroup(
                        label = stringResource(R.string.shopping_other_items),
                        items = view.manual,
                        onChecked = onChecked,
                        phases = phases,
                        reducedMotion = reducedMotion,
                    )
                }
            }
            if (view.basket.isNotEmpty()) {
                item(key = "basket") {
                    BasketHeader(count = view.basket.size, open = basketOpen, onToggle = viewModel::toggleBasket)
                }
                if (basketOpen) {
                    item(key = "basket-items") {
                        ItemGroup(label = null, items = view.basket, onChecked = onChecked)
                    }
                }
            }
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}


private fun Context.findActivity(): Activity {
    var c = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    error("No Activity in context")
}

@Composable
private fun AddItemRow(draft: String, onDraftChange: (String) -> Unit, onAdd: () -> Unit) {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        SimmerTextField(
            value = draft,
            onValueChange = onDraftChange,
            label = stringResource(R.string.shopping_add_label),
            placeholder = stringResource(R.string.shopping_add_placeholder),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAdd() }),
            modifier = Modifier.weight(1f),
        )
        PrimaryButton(
            text = stringResource(R.string.shopping_add_button),
            onClick = onAdd,
            enabled = draft.isNotBlank(),
        )
    }
}

/** One section: optional micro label above a bordered card with a divider between rows. */
@Composable
private fun ItemGroup(
    label: String?,
    items: List<ShoppingItem>,
    onChecked: (String, Boolean) -> Unit,
    phases: Map<String, GracePhase> = emptyMap(),
    reducedMotion: Boolean = false,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        if (label != null) {
            Text(
                text = label,
                style = SimmerTextStyle_MicroUppercase,
                color = LocalSimmerColors.current.textTertiary,
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .semantics { heading() },
            )
        }
        RowCard {
            items.forEachIndexed { i, item ->
                key(item.id) {
                    // A ticked row keeps its place; once its grace period ends it fades out and collapses (duration-base).
                    AnimatedVisibility(
                        visible = phases[item.id] != GracePhase.Fading,
                        exit = if (reducedMotion) {
                            ExitTransition.None
                        } else {
                            fadeOut(tween(GracePeriod.FADE_MS.toInt())) + shrinkVertically(tween(GracePeriod.FADE_MS.toInt()))
                        },
                    ) {
                        Column {
                            if (i > 0) RowDivider()
                            ItemRow(item, onChecked, animate = !reducedMotion)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemRow(item: ShoppingItem, onChecked: (String, Boolean) -> Unit, animate: Boolean) {
    CheckRow(
        label = item.name,
        checked = item.checked,
        onCheckedChange = { onChecked(item.id, it) },
        subtitle = item.note.ifBlank { null },
        trailing = item.amount.ifBlank { null },
        animate = animate,
        strikeThroughWhenChecked = true,
    )
}

@Composable
private fun BasketHeader(count: Int, open: Boolean, onToggle: () -> Unit) {
    val colors = LocalSimmerColors.current
    val stateText = stringResource(if (open) R.string.shopping_basket_collapse else R.string.shopping_basket_expand)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(onClickLabel = stateText, role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = stateText }
            .padding(horizontal = 16.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier
                .size(20.dp)
                .rotate(if (open) 90f else 0f),
        )
        Text(
            text = stringResource(R.string.shopping_basket),
            style = SimmerTextStyle_BodyMd,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = pluralStringResource(R.plurals.shopping_items_count, count, count),
            style = SimmerTextStyle_BodySm,
            color = colors.textSecondary,
        )
    }
}
