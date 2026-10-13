package com.simmer.app.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import com.simmer.app.domain.DownloadFailure
import com.simmer.app.domain.READER_REQUIRED_FREE_BYTES
import com.simmer.app.domain.ReaderModel
import com.simmer.app.domain.ReaderStatus
import com.simmer.app.domain.formatGigabytes
import androidx.compose.ui.platform.LocalConfiguration
import com.simmer.app.ui.components.PrimaryButton
import com.simmer.app.ui.flow.rememberNotificationPermissionRequest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simmer.app.R
import com.simmer.app.domain.KitchenMode
import com.simmer.app.domain.SettingsPreview
import com.simmer.app.ui.components.OptionCard
import com.simmer.app.ui.components.SecondaryButton
import com.simmer.app.ui.theme.LocalSimmerColors
import com.simmer.app.ui.theme.SimmerTextStyle_BodyMdMedium
import com.simmer.app.ui.theme.SimmerTextStyle_BodySm
import com.simmer.app.ui.theme.SimmerTextStyle_BodySmMedium
import com.simmer.app.ui.theme.SimmerTextStyle_Caption
import com.simmer.app.ui.theme.SimmerTextStyle_MicroUppercase
import com.simmer.app.ui.theme.SimmerTextStyle_ScreenTitle

/** The Settings tab: language, units, spices, kitchen, kitchen mode, recipe reader and a live preview. */
@Composable
fun SettingsScreen(
    onEditKitchen: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val colors = LocalSimmerColors.current
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfacePage)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        val state = ui ?: return@Box
        Column(
            verticalArrangement = Arrangement.spacedBy(28.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = SimmerTextStyle_ScreenTitle,
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            OptionGroup(
                title = R.string.settings_language,
                hint = R.string.settings_language_hint,
                options = listOf(
                    Choice("en", R.string.settings_language_en, R.string.settings_language_en_desc),
                    Choice("de", R.string.settings_language_de, R.string.settings_language_de_desc),
                ),
                selected = state.lang,
                onSelect = viewModel::setLang,
            )
            OptionGroup(
                title = R.string.settings_units,
                hint = R.string.settings_units_hint,
                options = listOf(
                    Choice("metric", R.string.settings_units_metric, R.string.settings_units_metric_desc),
                    Choice("imperial", R.string.settings_units_imperial, R.string.settings_units_imperial_desc),
                ),
                selected = state.units,
                onSelect = viewModel::setUnits,
            )
            OptionGroup(
                title = R.string.settings_spices,
                hint = R.string.settings_spices_hint,
                options = listOf(
                    Choice("spoons", R.string.settings_spices_spoons, R.string.settings_spices_spoons_desc),
                    Choice("grams", R.string.settings_spices_grams, R.string.settings_spices_grams_desc),
                ),
                selected = state.spices,
                onSelect = viewModel::setSpices,
            )
            KitchenSection(
                appliances = state.appliances,
                mode = state.kitchenMode,
                onEditKitchen = onEditKitchen,
                onModeSelect = viewModel::setKitchenMode,
            )
            ReaderSection(
                status = state.reader,
                wifiOnly = state.readerWifiOnly,
                onWifiOnly = viewModel::setReaderWifiOnly,
                onDownload = viewModel::downloadReader,
                onCancel = viewModel::cancelReaderDownload,
                onRemove = viewModel::removeReader,
            )
            state.preview?.let { PreviewPanel(it) }
            DebugReaderSection()
        }
    }
}

private data class Choice(val key: String, @StringRes val label: Int, @StringRes val description: Int)

@Composable
private fun SectionHeader(@StringRes title: Int, @StringRes hint: Int) {
    val colors = LocalSimmerColors.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringResource(title),
            style = SimmerTextStyle_BodyMdMedium,
            color = colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Text(text = stringResource(hint), style = SimmerTextStyle_Caption, color = colors.textTertiary)
    }
}

/** Two option cards side by side (radio group). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionGroup(
    @StringRes title: Int,
    @StringRes hint: Int,
    options: List<Choice>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title, hint)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
        ) {
            options.forEach { choice ->
                val on = choice.key == selected
                RadioCard(
                    selected = on,
                    onSelect = { onSelect(choice.key) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 76.dp)
                            .padding(14.dp),
                    ) {
                        CardTitleRow(stringResource(choice.label), on)
                        Text(
                            text = stringResource(choice.description),
                            style = SimmerTextStyle_Caption,
                            color = LocalSimmerColors.current.textTertiary,
                        )
                    }
                }
            }
        }
    }
}

/** An [OptionCard] exposed to accessibility as a radio button. */
@Composable
private fun RadioCard(
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    OptionCard(
        selected = selected,
        onClick = onSelect,
        modifier = modifier.semantics(mergeDescendants = true) {
            role = Role.RadioButton
            this.selected = selected
        },
        content = content,
    )
}

@Composable
private fun CardTitleRow(label: String, selected: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 22.dp),
    ) {
        Text(
            text = label,
            style = SimmerTextStyle_BodyMdMedium,
            color = LocalSimmerColors.current.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (selected) SelectedDot()
    }
}

@Composable
private fun SelectedDot() {
    val colors = LocalSimmerColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(22.dp)
            .background(colors.accent, CircleShape),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_check),
            contentDescription = null,
            tint = colors.accentFg,
            modifier = Modifier.size(14.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KitchenSection(
    appliances: List<String>,
    mode: KitchenMode,
    onEditKitchen: () -> Unit,
    onModeSelect: (KitchenMode) -> Unit,
) {
    val colors = LocalSimmerColors.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(R.string.settings_kitchen, R.string.settings_kitchen_hint)
        if (appliances.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_kitchen_empty),
                style = SimmerTextStyle_BodySm,
                color = colors.textSecondary,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                appliances.forEach { key -> AppliancePill(applianceLabel(key)) }
            }
        }
        SecondaryButton(
            text = stringResource(R.string.settings_kitchen_edit),
            onClick = onEditKitchen,
            modifier = Modifier.fillMaxWidth(),
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            Text(
                text = stringResource(R.string.settings_kitchen_mode),
                style = SimmerTextStyle_BodySmMedium,
                color = colors.textPrimary,
            )
            KitchenMode.entries.forEach { option ->
                val on = option == mode
                val (label, description) = modeStrings(option)
                RadioCard(selected = on, onSelect = { onModeSelect(option) }, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 64.dp)
                            .padding(14.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(label),
                                style = SimmerTextStyle_BodyMdMedium,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = stringResource(description),
                                style = SimmerTextStyle_Caption,
                                color = colors.textTertiary,
                            )
                        }
                        if (on) SelectedDot()
                    }
                }
            }
        }
    }
}

/** Label and description string resources of a kitchen mode. */
private fun modeStrings(mode: KitchenMode): Pair<Int, Int> = when (mode) {
    KitchenMode.Strict -> R.string.settings_mode_strict to R.string.settings_mode_strict_desc
    KitchenMode.Medium -> R.string.settings_mode_medium to R.string.settings_mode_medium_desc
    KitchenMode.Free -> R.string.settings_mode_free to R.string.settings_mode_free_desc
}

@Composable
private fun AppliancePill(label: String) {
    val colors = LocalSimmerColors.current
    Text(
        text = label,
        style = SimmerTextStyle_BodySmMedium,
        color = colors.textPrimary,
        modifier = Modifier
            .defaultMinSize(minHeight = 36.dp)
            .background(colors.surfaceWarm, CircleShape)
            .border(BorderStroke(2.dp, colors.borderWarm), CircleShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** Catalogue appliances have a translation; custom ones ("c:Name") show as the user wrote them. */
@Composable
internal fun applianceLabel(key: String): String {
    if (key.startsWith("c:")) return key.removePrefix("c:")
    val res = APPLIANCE_LABELS[key] ?: return key
    return stringResource(res)
}

private val APPLIANCE_LABELS: Map<String, Int> = mapOf(
    "hob" to R.string.appliance_hob,
    "oven" to R.string.appliance_oven,
    "microwave" to R.string.appliance_microwave,
    "airfryer" to R.string.appliance_airfryer,
    "slowcooker" to R.string.appliance_slowcooker,
    "pressure" to R.string.appliance_pressure,
    "multicooker" to R.string.appliance_multicooker,
    "ricecooker" to R.string.appliance_ricecooker,
    "steamoven" to R.string.appliance_steamoven,
    "grill" to R.string.appliance_grill,
    "charcoal" to R.string.appliance_charcoal,
    "smoker" to R.string.appliance_smoker,
    "pizzaoven" to R.string.appliance_pizzaoven,
    "grillpan" to R.string.appliance_grillpan,
    "wok" to R.string.appliance_wok,
    "dutchoven" to R.string.appliance_dutchoven,
    "sousvide" to R.string.appliance_sousvide,
    "toaster" to R.string.appliance_toaster,
    "kettle" to R.string.appliance_kettle,
    "processor" to R.string.appliance_processor,
    "mixer" to R.string.appliance_mixer,
    "handmixer" to R.string.appliance_handmixer,
    "blender" to R.string.appliance_blender,
    "stickblender" to R.string.appliance_stickblender,
    "breadmaker" to R.string.appliance_breadmaker,
    "waffle" to R.string.appliance_waffle,
    "icecream" to R.string.appliance_icecream,
    "dehydrator" to R.string.appliance_dehydrator,
)

@Composable
private fun readerModelSize(): String =
    formatGigabytes(ReaderModel.SIZE_BYTES, LocalConfiguration.current.locales[0])

@Composable
private fun readerFreeSpaceNeeded(): String =
    formatGigabytes(READER_REQUIRED_FREE_BYTES, LocalConfiguration.current.locales[0])

/** The state line under "On this phone": one string per [ReaderStatus]. */
@Composable
private fun readerStateText(status: ReaderStatus): String = when (status) {
    ReaderStatus.NotDownloaded -> stringResource(R.string.settings_reader_state, readerModelSize())
    is ReaderStatus.Downloading -> stringResource(
        if (status.waiting) R.string.settings_reader_state_waiting else R.string.settings_reader_state_downloading,
        status.percent,
    )
    ReaderStatus.Ready -> stringResource(R.string.settings_reader_state_ready)
    ReaderStatus.NotEnoughStorage -> stringResource(R.string.settings_reader_state_no_space, readerFreeSpaceNeeded())
    ReaderStatus.NotSupported -> stringResource(R.string.settings_reader_state_unsupported)
    is ReaderStatus.Failed -> when (status.reason) {
        DownloadFailure.ChecksumMismatch -> stringResource(R.string.settings_reader_state_corrupt)
        DownloadFailure.NotEnoughStorage -> stringResource(R.string.settings_reader_state_no_space, readerFreeSpaceNeeded())
        DownloadFailure.ServerError -> stringResource(R.string.settings_reader_state_server)
        DownloadFailure.Unknown -> stringResource(R.string.settings_reader_state_failed)
    }
}

/** Status of the on-device model, its actions (Download, Cancel, Remove) and the Wi-Fi-only switch. */
@Composable
private fun ReaderSection(
    status: ReaderStatus,
    wifiOnly: Boolean,
    onWifiOnly: (Boolean) -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = LocalSimmerColors.current
    val shape = RoundedCornerShape(12.dp)
    val requestNotifications = rememberNotificationPermissionRequest()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(R.string.settings_reader, R.string.settings_reader_hint)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceCard, shape)
                .border(BorderStroke(2.dp, colors.borderDefault), shape)
                .padding(16.dp),
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
                    painter = painterResource(R.drawable.ic_cpu),
                    contentDescription = null,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_reader_where),
                    style = SimmerTextStyle_BodyMdMedium,
                    color = colors.textPrimary,
                )
                Text(
                    text = readerStateText(status),
                    style = SimmerTextStyle_Caption,
                    color = colors.textTertiary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                if (status is ReaderStatus.Downloading) {
                    LinearProgressIndicator(
                        progress = { status.percent / 100f },
                        color = colors.accent,
                        trackColor = colors.borderSubtle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .height(6.dp)
                            .clip(CircleShape),
                    )
                }
            }
        }
        when (status) {
            ReaderStatus.NotDownloaded, ReaderStatus.NotEnoughStorage, is ReaderStatus.Failed ->
                PrimaryButton(
                    text = stringResource(R.string.settings_reader_download),
                    onClick = {
                        requestNotifications()
                        onDownload()
                    },
                    enabled = status != ReaderStatus.NotEnoughStorage,
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Icon(painterResource(R.drawable.ic_download), null, Modifier.size(18.dp)) },
                )
            is ReaderStatus.Downloading ->
                SecondaryButton(
                    text = stringResource(R.string.settings_reader_cancel),
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Icon(painterResource(R.drawable.ic_x), null, Modifier.size(18.dp)) },
                )
            ReaderStatus.Ready ->
                SecondaryButton(
                    text = stringResource(R.string.settings_reader_remove),
                    onClick = onRemove,
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Icon(painterResource(R.drawable.ic_trash), null, Modifier.size(18.dp)) },
                )
            ReaderStatus.NotSupported -> Unit
        }
        if (status != ReaderStatus.NotSupported) WifiOnlyRow(wifiOnly, onWifiOnly)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_lock),
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = stringResource(R.string.settings_reader_private),
                style = SimmerTextStyle_Caption,
                color = colors.textSecondary,
            )
        }
    }
}

/** "Download on Wi-Fi only": the whole row toggles; a switch with a switch role for TalkBack. */
@Composable
private fun WifiOnlyRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LocalSimmerColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_reader_wifi_only),
                style = SimmerTextStyle_BodyMdMedium,
                color = colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.settings_reader_wifi_only_desc, readerModelSize()),
                style = SimmerTextStyle_Caption,
                color = colors.textTertiary,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.accentFg,
                checkedTrackColor = colors.accent,
                checkedBorderColor = colors.accent,
                uncheckedThumbColor = colors.textSecondary,
                uncheckedTrackColor = colors.surfaceCard,
                uncheckedBorderColor = colors.borderDefault,
            ),
        )
    }
}

@Composable
private fun PreviewPanel(preview: SettingsPreview) {
    val colors = LocalSimmerColors.current
    val shape = RoundedCornerShape(12.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceWarm, shape)
            .border(BorderStroke(2.dp, colors.borderWarm), shape)
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.settings_preview).uppercase(),
                style = SimmerTextStyle_MicroUppercase,
                color = colors.textSecondary,
            )
            Text(
                text = stringResource(R.string.settings_preview_caption),
                style = SimmerTextStyle_BodySmMedium,
                color = colors.textPrimary,
            )
        }
        Column {
            preview.ingredients.forEach { PreviewLine(it.name, it.amount) }
            preview.ovenTemperature?.let { PreviewLine(stringResource(R.string.settings_preview_oven), it) }
        }
    }
}

@Composable
private fun PreviewLine(name: String, amount: String) {
    val colors = LocalSimmerColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 40.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(
            text = name,
            style = SimmerTextStyle_BodySm,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(text = amount, style = SimmerTextStyle_BodySmMedium, color = colors.textPrimary)
    }
}
