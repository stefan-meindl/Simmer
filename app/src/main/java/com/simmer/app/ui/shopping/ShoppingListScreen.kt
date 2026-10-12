package com.simmer.app.ui.shopping

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simmer.app.R
import com.simmer.app.domain.ShoppingItem
import com.simmer.app.ui.components.CheckRow
import com.simmer.app.ui.components.PrimaryButton
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
    val list by viewModel.list.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val basketOpen by viewModel.basketOpen.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfacePage)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        val view = list ?: return@Box
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "title") {
                Text(
                    text = stringResource(R.string.shopping_title),
                    style = SimmerTextStyle_ScreenTitle,
                    color = colors.textPrimary,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .semantics { heading() },
                )
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
                item(key = "aisle-${group.aisle}") { SectionLabel(group.aisle) }
                items(group.items, key = { it.id }) { ItemRow(it, viewModel::setChecked) }
            }
            if (view.manual.isNotEmpty()) {
                item(key = "manual") { SectionLabel(stringResource(R.string.shopping_other_items)) }
                items(view.manual, key = { it.id }) { ItemRow(it, viewModel::setChecked) }
            }
            if (view.basket.isNotEmpty()) {
                item(key = "basket") {
                    BasketHeader(count = view.basket.size, open = basketOpen, onToggle = viewModel::toggleBasket)
                }
                if (basketOpen) {
                    items(view.basket, key = { "basket-${it.id}" }) { ItemRow(it, viewModel::setChecked) }
                }
            }
        }
    }
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

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = SimmerTextStyle_MicroUppercase,
        color = LocalSimmerColors.current.textTertiary,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun ItemRow(item: ShoppingItem, onChecked: (String, Boolean) -> Unit) {
    CheckRow(
        label = item.name,
        checked = item.checked,
        onCheckedChange = { onChecked(item.id, it) },
        subtitle = item.note.ifBlank { null },
        trailing = item.amount.ifBlank { null },
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
            .padding(top = 16.dp)
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
