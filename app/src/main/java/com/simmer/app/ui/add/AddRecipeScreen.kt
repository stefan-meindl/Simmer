package com.simmer.app.ui.add

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import android.content.ClipData
import android.content.Context
import androidx.compose.ui.platform.Clipboard
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.ClipEntry
import com.simmer.app.domain.AddStep
import com.simmer.app.ui.components.readerStateText
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import com.simmer.app.domain.PipelineStage
import com.simmer.app.domain.StageMark
import com.simmer.app.domain.stageMarks
import com.simmer.app.pipeline.ImportNotifications
import com.simmer.app.ui.flow.rememberNotificationPermissionRequest
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.simmer.app.domain.autofillReply
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
import androidx.compose.ui.platform.LocalConfiguration
import com.simmer.app.domain.ReaderAction
import com.simmer.app.domain.ReaderModel
import com.simmer.app.domain.formatGigabytes
import com.simmer.app.domain.ReviewChoice
import com.simmer.app.domain.ReviewIngredient
import com.simmer.app.domain.formatDuration
import com.simmer.app.domain.model.Recipe
import com.simmer.app.domain.model.ResolvedIngredient
import com.simmer.app.domain.model.ResolvedStep
import com.simmer.app.ui.components.EditAction
import com.simmer.app.ui.components.EditIconAction
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
 * Add recipe: paste a recipe's text, convert it (Working stage, or the error stage), review the
 * result and save it. The built-in example converts at once; other text runs the Pipeline once the
 * Reader model is downloaded, and shows the "Download recipe reader" placeholder before.
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

    // Back from the start stage, the chatbot path or the paste stage: step back, then leave from the start stage.
    val onStepBack = { viewModel.back(onLeave = onBack) }
    BackHandler(enabled = ui is AddRecipeUi.Chatbot || ui is AddRecipeUi.Paste, onBack = onStepBack)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfacePage)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        when (val u = ui) {
            AddRecipeUi.Loading -> Unit
            is AddRecipeUi.Start -> StartStage(u, viewModel, onBack)
            is AddRecipeUi.Chatbot -> ChatbotStage(u, viewModel, onStepBack)
            is AddRecipeUi.Paste -> PasteStage(u, viewModel, onStepBack, onChangeKitchen)
            is AddRecipeUi.Working -> WorkingStage(u, viewModel, onBack)
            AddRecipeUi.Error -> ErrorStage(viewModel, onBack)
            is AddRecipeUi.Review -> ReviewStage(u, viewModel, onBack, onEditDetails, onSaved)
        }
    }
}

// ---------------------------------------------------------------------------
// Shared chrome
// ---------------------------------------------------------------------------

@Composable
private fun TopRow(caption: String, onBack: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
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
            text = caption,
            style = SimmerTextStyle_Caption,
            color = colors.textTertiary,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
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
// Start stage and chatbot path
// ---------------------------------------------------------------------------

@Composable
private fun ColumnScope.StartStage(ui: AddRecipeUi.Start, viewModel: AddRecipeViewModel, onBack: () -> Unit) {
    val colors = LocalSimmerColors.current
    TopRow(stringResource(R.string.add_caption_new), onBack)
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.add_choose_title),
            style = SimmerTextStyle_ScreenTitle,
            color = colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        StartCard(
            iconRes = R.drawable.ic_cpu,
            title = stringResource(R.string.add_choose_phone),
            body = stringResource(R.string.add_choose_phone_body),
            detail = readerStateText(ui.reader),
            onClick = { viewModel.choose(AddStep.Phone) },
        )
        StartCard(
            iconRes = R.drawable.ic_sparkles,
            title = stringResource(R.string.add_choose_chatbot),
            body = stringResource(R.string.add_choose_chatbot_body),
            detail = null,
            onClick = { viewModel.choose(AddStep.Chatbot) },
        )
    }
}

@Composable
private fun StartCard(iconRes: Int, title: String, body: String, detail: String?, onClick: () -> Unit) {
    val colors = LocalSimmerColors.current
    RowCard(modifier = Modifier.clickable(role = Role.Button, onClick = onClick)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(16.dp),
        ) {
            icon(iconRes, 24, colors.textPrimary)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text(text = title, style = SimmerTextStyle_Heading5, color = colors.textPrimary)
                Text(text = body, style = SimmerTextStyle_BodySm, color = colors.textSecondary)
                if (detail != null) Text(text = detail, style = SimmerTextStyle_Caption, color = colors.textTertiary)
            }
            icon(R.drawable.ic_chevron_right, 20, colors.textTertiary)
        }
    }
}

/**
 * The chatbot path. Step 1: an optional recipe text and "Copy prompt", which puts the Reader prompt on
 * the clipboard. Step 2: paste the chatbot's reply, then "Add recipe" turns it into the draft for review.
 */
@Composable
private fun ColumnScope.ChatbotStage(ui: AddRecipeUi.Chatbot, viewModel: AddRecipeViewModel, onBack: () -> Unit) {
    val colors = LocalSimmerColors.current
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copyLabel = stringResource(R.string.add_copy_prompt)
    val copiedText = stringResource(R.string.add_prompt_copied)
    val fixCopyLabel = stringResource(R.string.add_copy_fix_prompt)
    val fixCopiedText = stringResource(R.string.add_fix_prompt_copied)
    val geminiLabel = stringResource(R.string.add_open_gemini)
    val autofilledText = stringResource(R.string.add_reply_autofilled)
    val geminiInstalled = remember { isGeminiInstalled(context) }
    // The last text this screen put on the clipboard or prefilled from it; never offered for autofill again
    // (the prompts carry a recipe example that would parse).
    var handledClip by rememberSaveable { mutableStateOf("") }
    // The one way this screen writes the clipboard, so a new writer cannot forget to record the text.
    suspend fun copyToClipboard(label: String, text: String) {
        handledClip = text
        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, text)))
    }

    // On return to the app, a clipboard reply that reads as a recipe prefills the reply field. Never submits.
    // Reading the clipboard needs window focus on Android 10+, which arrives just after ON_RESUME.
    var resumed by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(resumed, windowFocused) {
        if (!resumed || !windowFocused) return@LaunchedEffect
        val reply = autofillReply(clipboard.readText(context),handledClip, ui.kitchen.appliances.toSet(), ui.kitchen.mode)
        if (reply != null) {
            handledClip = reply
            viewModel.setReply(reply)
            Toast.makeText(context, autofilledText, Toast.LENGTH_SHORT).show()
        }
    }

    val scrollState = rememberScrollState()
    val errorText = stringResource(R.string.add_reply_error)
    LaunchedEffect(viewModel) {
        viewModel.replyFailed.collect {
            Toast.makeText(context, errorText, Toast.LENGTH_SHORT).show()
            // The error and Copy fix prompt sit below the reply field, often off screen; let them lay out first.
            withFrameNanos { }
            withFrameNanos { }
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    TopRow(stringResource(R.string.add_choose_chatbot), onBack)
    Column(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier
            .weight(1f)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.add_chatbot_title),
            style = SimmerTextStyle_ScreenTitle,
            color = colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.add_step_prompt),
            style = SimmerTextStyle_Heading5,
            color = colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        PasteField(
            label = stringResource(R.string.add_prompt_text),
            value = ui.promptText,
            placeholder = stringResource(R.string.add_prompt_text_placeholder),
            minLines = 6,
            onChange = viewModel::setPromptText,
            onClear = viewModel::clearPromptText,
        )
        Text(
            text = stringResource(R.string.add_prompt_text_note),
            style = SimmerTextStyle_BodySm,
            color = colors.textSecondary,
        )
        SecondaryButton(
            text = stringResource(R.string.add_copy_prompt),
            onClick = {
                val prompt = readerPromptFor(context, ui)
                scope.launch {
                    copyToClipboard(copyLabel, prompt)
                    Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
                }
            },
            icon = { icon(R.drawable.ic_copy, 16) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (geminiInstalled) {
            // The same prompt also goes on the clipboard, as a fallback if Gemini drops the shared text.
            SecondaryButton(
                text = geminiLabel,
                onClick = {
                    val prompt = readerPromptFor(context, ui)
                    scope.launch {
                        copyToClipboard(copyLabel, prompt)
                        openInGemini(context, prompt)
                    }
                },
                icon = { icon(R.drawable.ic_arrow_right, 16) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            text = stringResource(R.string.add_step_reply),
            style = SimmerTextStyle_Heading5,
            color = colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        PasteField(
            label = stringResource(R.string.add_reply),
            value = ui.reply,
            placeholder = stringResource(R.string.add_reply_placeholder),
            minLines = 13,
            onChange = viewModel::setReply,
            onClear = viewModel::clearReply,
        )
        if (ui.error != null) {
            Text(
                text = stringResource(R.string.add_reply_error),
                style = SimmerTextStyle_BodySm,
                color = colors.textPrimary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            SecondaryButton(
                text = stringResource(R.string.add_copy_fix_prompt),
                onClick = {
                    val prompt = fixPromptFor(context, ui.lang, ui.error)
                    scope.launch {
                        copyToClipboard(fixCopyLabel, prompt)
                        Toast.makeText(context, fixCopiedText, Toast.LENGTH_SHORT).show()
                    }
                },
                icon = { icon(R.drawable.ic_copy, 16) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    BottomBar {
        PrimaryButton(
            text = stringResource(R.string.add_reply_add),
            onClick = viewModel::addFromReply,
            enabled = ui.canAdd,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A labelled text area with Paste (empty) or Clear (filled) beside the label. */
@Composable
private fun PasteField(
    label: String,
    value: String,
    placeholder: String,
    minLines: Int,
    onChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    val colors = LocalSimmerColors.current
    val focus = remember { FocusRequester() }
    val paste = rememberPasteAction(onPasted = onChange, onEmpty = { focus.requestFocus() })
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 44.dp),
        ) {
            Text(text = label, style = SimmerTextStyle_BodySmMedium, color = colors.textPrimary)
            if (value.isNotEmpty()) {
                LinkButton(text = stringResource(R.string.add_clear), onClick = onClear)
            } else {
                SecondaryButton(
                    text = stringResource(R.string.add_paste),
                    onClick = paste,
                    icon = { icon(R.drawable.ic_copy, 16) },
                )
            }
        }
        SimmerTextArea(
            value = value,
            onValueChange = onChange,
            placeholder = placeholder,
            minLines = minLines,
            fieldModifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .semantics { contentDescription = label },
        )
    }
}

/** The clipboard's first item as text, or null when it holds none. */
private suspend fun Clipboard.readText(context: Context): String? =
    getClipEntry()?.clipData
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)?.coerceToText(context)?.toString()

/** A click handler that pastes the clipboard's text into [onPasted], or calls [onEmpty] when it holds none. */
@Composable
private fun rememberPasteAction(onPasted: (String) -> Unit, onEmpty: () -> Unit): () -> Unit {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            val pasted = clipboard.readText(context)
            if (pasted.isNullOrEmpty()) onEmpty() else onPasted(pasted)
        }
    }
}

// ---------------------------------------------------------------------------
// Paste stage
// ---------------------------------------------------------------------------

@Composable
private fun ColumnScope.PasteStage(
    ui: AddRecipeUi.Paste,
    viewModel: AddRecipeViewModel,
    onBack: () -> Unit,
    onChangeKitchen: () -> Unit,
) {
    val colors = LocalSimmerColors.current
    val focus = remember { FocusRequester() }
    val pasteText = rememberPasteAction(onPasted = viewModel::setText, onEmpty = { focus.requestFocus() })
    val textLabel = stringResource(R.string.add_recipe_text)
    val askNotificationPermission = rememberNotificationPermissionRequest()

    TopRow(stringResource(R.string.add_caption_new), onBack)
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
                        onClick = pasteText,
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.add_next_title).uppercase(),
                    style = SimmerTextStyle_MicroUppercase,
                    color = colors.textTertiary,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                EditIconAction(
                    contentDescription = stringResource(R.string.add_change_kitchen),
                    onClick = onChangeKitchen,
                )
            }
            listOf(
                R.drawable.ic_list_checks to R.string.add_next_1,
                R.drawable.ic_utensils to R.string.add_next_2,
                R.drawable.ic_timer to R.string.add_next_3,
            ).forEach { (iconRes, text) ->
                NextRow(iconRes) { Text(stringResource(text), style = SimmerTextStyle_BodySm, color = colors.textSecondary) }
            }
            NextRow(R.drawable.ic_chef_hat) {
                Text(
                    text = kitchenText(ui.kitchen),
                    style = SimmerTextStyle_BodySm,
                    color = colors.textSecondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    BottomBar {
        when (ui.action) {
            ReaderAction.Convert -> PrimaryButton(
                text = stringResource(R.string.add_convert),
                onClick = {
                    askNotificationPermission()
                    viewModel.convert()
                },
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
                    text = stringResource(R.string.add_download, formatGigabytes(ReaderModel.SIZE_BYTES, LocalConfiguration.current.locales[0])),
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
// Working and error stages
// ---------------------------------------------------------------------------

@Composable
private fun ColumnScope.WorkingStage(ui: AddRecipeUi.Working, viewModel: AddRecipeViewModel, onBack: () -> Unit) {
    val colors = LocalSimmerColors.current
    TopRow(stringResource(R.string.add_caption_new), onBack)
    Column(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.import_working_title),
                style = SimmerTextStyle_ScreenTitle,
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(text = stringResource(R.string.import_working_hint), style = SimmerTextStyle_BodyMd, color = colors.textSecondary)
        }
        Column(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            stageMarks(ui.stage).forEach { (stage, mark) -> StageRow(stage, mark) }
        }
    }
    BottomBar {
        SecondaryButton(
            text = stringResource(R.string.import_cancel),
            onClick = viewModel::cancel,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** One line of the Working checklist: orange check (done), orange ring (current), grey ring (pending). */
@Composable
private fun StageRow(stage: PipelineStage, mark: StageMark) {
    val colors = LocalSimmerColors.current
    val label = stringResource(ImportNotifications.stageLabel(stage))
    val description = stringResource(
        when (mark) {
            StageMark.Done -> R.string.import_stage_done
            StageMark.Current -> R.string.import_stage_current
            StageMark.Pending -> R.string.import_stage_pending
        },
        label,
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (mark == StageMark.Done) colors.accent else Color.Transparent)
                .border(
                    BorderStroke(if (mark == StageMark.Current) 3.dp else 2.dp, if (mark == StageMark.Pending) colors.borderStrong else colors.accent),
                    CircleShape,
                ),
        ) {
            if (mark == StageMark.Done) {
                Icon(painterResource(R.drawable.ic_check_bold), contentDescription = null, tint = colors.accentFg, modifier = Modifier.size(16.dp))
            }
        }
        Text(
            text = label,
            style = SimmerTextStyle_BodyMd,
            color = if (mark == StageMark.Pending) colors.textTertiary else colors.textPrimary,
        )
    }
}

@Composable
private fun ColumnScope.ErrorStage(viewModel: AddRecipeViewModel, onBack: () -> Unit) {
    val colors = LocalSimmerColors.current
    TopRow(stringResource(R.string.add_caption_new), onBack)
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.import_error_title),
            style = SimmerTextStyle_ScreenTitle,
            color = colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.import_error_body),
            style = SimmerTextStyle_BodyMd,
            color = colors.textSecondary,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    BottomBar {
        PrimaryButton(
            text = stringResource(R.string.import_error_back),
            onClick = viewModel::dismissError,
            modifier = Modifier.fillMaxWidth(),
        )
    }
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

    // Outside the scrolling column, so Edit details stays in view while the review scrolls.
    TopRow(stringResource(R.string.add_caption_review), onBack) {
        EditAction(text = stringResource(R.string.add_edit_details), onClick = onEditDetails)
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Hero(review.recipe)
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
