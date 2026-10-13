package com.simmer.app.ui.kitchen

import android.content.res.Configuration
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simmer.app.R
import com.simmer.app.domain.ApplianceRow
import com.simmer.app.domain.addOffer
import com.simmer.app.domain.kitchenRows
import com.simmer.app.domain.matchingAppliances
import com.simmer.app.ui.components.CheckRow
import com.simmer.app.ui.components.PrimaryButton
import com.simmer.app.ui.components.RowCard
import com.simmer.app.ui.components.RowDivider
import com.simmer.app.ui.components.SimmerTextField
import com.simmer.app.ui.settings.APPLIANCE_LABELS
import com.simmer.app.ui.theme.LocalSimmerColors
import com.simmer.app.ui.theme.SimmerTextStyle_BodyMd
import com.simmer.app.ui.theme.SimmerTextStyle_BodyMdMedium
import com.simmer.app.ui.theme.SimmerTextStyle_BodySmMedium
import com.simmer.app.ui.theme.SimmerTextStyle_Caption
import com.simmer.app.ui.theme.SimmerTextStyle_ScreenTitle
import java.util.Locale

/**
 * Your kitchen: tick the appliances you own. Recipe creation limits generated Methods to them;
 * cooking an existing Recipe is never filtered.
 *
 * @param onDone Back and Done both return to Settings.
 */
@Composable
fun YourKitchenScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: YourKitchenViewModel = hiltViewModel(),
) {
    val colors = LocalSimmerColors.current
    val choice by viewModel.choice.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val english = remember(context, configuration) {
        context.createConfigurationContext(Configuration(configuration).apply { setLocale(Locale.ENGLISH) })
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfacePage)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        BackRow(onDone)
        val current = choice
        if (current == null) {
            Box(Modifier.weight(1f))
        } else {
            val rows = kitchenRows(current.custom) { key ->
                val res = APPLIANCE_LABELS.getValue(key)
                ApplianceRow(key, label = stringResource(res), english = english.getString(res))
            }
            val shown = matchingAppliances(rows, query)
            val offer = addOffer(rows, query)

            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.kitchen_title),
                        style = SimmerTextStyle_ScreenTitle,
                        color = colors.textPrimary,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(R.string.kitchen_intro),
                        style = SimmerTextStyle_BodyMd,
                        color = colors.textSecondary,
                    )
                }

                val searchLabel = stringResource(R.string.kitchen_search_label)
                SimmerTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    placeholder = stringResource(R.string.kitchen_search_placeholder),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.semantics { contentDescription = searchLabel },
                )

                Text(
                    text = pluralStringResource(R.plurals.kitchen_selected_count, current.selected.size, current.selected.size),
                    style = SimmerTextStyle_BodySmMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )

                if (offer != null || shown.isNotEmpty()) {
                    RowCard {
                        if (offer != null) {
                            AddRow(offer, onAdd = { viewModel.addCustom(offer) })
                            if (shown.isNotEmpty()) RowDivider()
                        }
                        shown.forEachIndexed { index, row ->
                            if (index > 0) RowDivider()
                            CheckRow(
                                label = row.label,
                                checked = row.key in current.selected,
                                onCheckedChange = { viewModel.toggle(row.key) },
                                subtitle = if (row.isCustom) stringResource(R.string.kitchen_added_by_you) else null,
                            )
                        }
                    }
                } else {
                    Text(
                        text = stringResource(R.string.kitchen_no_match),
                        style = SimmerTextStyle_BodyMd,
                        color = colors.textSecondary,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfacePage)
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
        ) {
            RowDivider()
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                PrimaryButton(
                    text = stringResource(R.string.kitchen_done),
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun BackRow(onBack: () -> Unit) {
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
        Text(
            text = stringResource(R.string.kitchen_back_to_settings),
            style = SimmerTextStyle_Caption,
            color = colors.textTertiary,
        )
    }
}

/** "Add 'query'" with its helper text: creates the appliance, selects it and keeps it in the list. */
@Composable
private fun AddRow(name: String, onAdd: () -> Unit) {
    val colors = LocalSimmerColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(role = Role.Button, onClick = onAdd)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_plus),
            contentDescription = null,
            tint = colors.textLink,
            modifier = Modifier.size(24.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.kitchen_add, name),
                style = SimmerTextStyle_BodyMdMedium,
                color = colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.kitchen_add_helper),
                style = SimmerTextStyle_Caption,
                color = colors.textTertiary,
            )
        }
    }
}
