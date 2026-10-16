package com.simmer.app.ui.add

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.simmer.app.R
import com.simmer.app.data.CaptionStatus
import com.simmer.app.data.ReelCard
import com.simmer.app.domain.DownloadFailure
import com.simmer.app.domain.ListenFailure
import com.simmer.app.domain.ListenState
import com.simmer.app.domain.listenClock
import com.simmer.app.ui.components.PrimaryButton
import com.simmer.app.ui.components.RowCard
import com.simmer.app.ui.components.SecondaryButton
import com.simmer.app.ui.theme.LocalSimmerColors
import com.simmer.app.ui.theme.SimmerTextStyle_BodySm
import com.simmer.app.ui.theme.SimmerTextStyle_Caption
import com.simmer.app.ui.theme.SimmerTextStyle_Heading5

/** The link as shown on the card: no scheme or "www". */
internal fun shortReelLink(url: String): String = url.removePrefix("https://").removePrefix("www.").trimEnd('/')

/** What the Reel card's Listen section can ask for; the [AddRecipeViewModel] answers them. */
internal class ListenCardActions(
    val onListen: () -> Unit,
    val onConfirmDownload: () -> Unit,
    val onStop: () -> Unit,
    val onCancel: () -> Unit,
    val onDismiss: () -> Unit,
    /** The silence message's Paste: closes the message and pastes the clipboard into the screen's text field. */
    val onPaste: () -> Unit,
)

/**
 * The Reel import card above Add recipe's text field: the shared link, the caption fetch status
 * (fetching, found, not found: a status, not an error) and the Listen section for the Reel's sound.
 */
@Composable
internal fun ReelCardView(card: ReelCard, listen: ListenState, actions: ListenCardActions, modifier: Modifier = Modifier) {
    val colors = LocalSimmerColors.current
    val status = when (card.status) {
        CaptionStatus.Loading -> stringResource(R.string.reel_fetching)
        CaptionStatus.Found -> pluralStringResource(R.plurals.reel_caption_found, card.captionLines, card.captionLines)
        CaptionStatus.NotFound -> stringResource(R.string.reel_not_found)
    }
    val link = shortReelLink(card.url)
    RowCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Icon(
                    painter = painterResource(R.drawable.ic_play),
                    contentDescription = null,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(24.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.reel_title), style = SimmerTextStyle_Heading5, color = colors.textPrimary)
                    Text(text = link, style = SimmerTextStyle_Caption, color = colors.textTertiary)
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .defaultMinSize(minHeight = 24.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                if (card.status == CaptionStatus.Loading) {
                    CircularProgressIndicator(strokeWidth = 2.dp, color = colors.accent, modifier = Modifier.size(16.dp))
                }
                Text(text = status, style = SimmerTextStyle_BodySm, color = colors.textSecondary)
            }
            // AudioPlaybackCapture needs Android 10; on older phones the card has no Listen section.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ListenSection(listen, actions)
        }
    }
}

/** The Listen button and what follows it: model download, capturing with Stop, transcribing, and the end messages. */
@Composable
private fun ListenSection(listen: ListenState, actions: ListenCardActions) {
    val colors = LocalSimmerColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (listen) {
            ListenState.Idle, is ListenState.Done -> {
                SecondaryButton(stringResource(R.string.reel_listen), onClick = actions.onListen, modifier = Modifier.fillMaxWidth())
                Text(text = stringResource(R.string.reel_listen_hint), style = SimmerTextStyle_Caption, color = colors.textTertiary)
            }
            is ListenState.ConfirmDownload, ListenState.AwaitingPermission, ListenState.AwaitingConsent ->
                SecondaryButton(stringResource(R.string.reel_listen), onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth())
            is ListenState.Downloading -> BusyWithCancel(
                text = stringResource(
                    if (listen.waiting) R.string.listen_downloading_waiting else R.string.listen_downloading,
                    listen.percent,
                ),
                actions = actions,
            )
            ListenState.Transcribing -> BusyWithCancel(text = stringResource(R.string.listen_transcribing), actions = actions)
            is ListenState.Capturing -> {
                // Not a live region: it would be read out every second.
                Text(
                    text = stringResource(R.string.listen_capturing, listenClock(listen.elapsedSeconds)),
                    style = SimmerTextStyle_BodySm,
                    color = colors.textPrimary,
                )
                PrimaryButton(stringResource(R.string.listen_stop), onClick = actions.onStop, modifier = Modifier.fillMaxWidth())
                Text(text = stringResource(R.string.listen_capturing_hint), style = SimmerTextStyle_Caption, color = colors.textTertiary)
            }
            ListenState.Silent -> {
                ListenStatus(text = stringResource(R.string.listen_silent), busy = false)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    PrimaryButton(stringResource(R.string.listen_retry), onClick = actions.onListen, modifier = Modifier.weight(1f))
                    SecondaryButton(stringResource(R.string.listen_paste), onClick = actions.onPaste, modifier = Modifier.weight(1f))
                }
            }
            // The rest ended without a transcript: a message, then Retry or Dismiss.
            else -> {
                ListenStatus(text = stringResource(listen.endMessage()), busy = false)
                EndActions(retry = if (listen is ListenState.DownloadFailed) actions.onConfirmDownload else actions.onListen, actions = actions)
            }
        }
    }
}

/** The message of a run that ended without a transcript (not silence, which has its own Paste action). */
@StringRes
private fun ListenState.endMessage(): Int = when (this) {
    is ListenState.DownloadFailed ->
        if (reason == DownloadFailure.NotEnoughStorage) R.string.listen_download_no_storage else R.string.listen_download_failed
    ListenState.PermissionDenied -> R.string.listen_permission_denied
    ListenState.Cancelled -> R.string.listen_cancelled
    ListenState.Interrupted -> R.string.listen_interrupted
    is ListenState.Failed ->
        if (reason == ListenFailure.CaptureFailed) R.string.listen_failed_capture else R.string.listen_failed_transcribe
    else -> error("$this has no end message")
}

@Composable
private fun BusyWithCancel(text: String, actions: ListenCardActions) {
    ListenStatus(text = text, busy = true)
    SecondaryButton(stringResource(R.string.listen_cancel), onClick = actions.onCancel, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun EndActions(retry: () -> Unit, actions: ListenCardActions) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        PrimaryButton(stringResource(R.string.listen_retry), onClick = retry, modifier = Modifier.weight(1f))
        SecondaryButton(stringResource(R.string.listen_dismiss), onClick = actions.onDismiss, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ListenStatus(text: String, busy: Boolean) {
    val colors = LocalSimmerColors.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .defaultMinSize(minHeight = 24.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        if (busy) CircularProgressIndicator(strokeWidth = 2.dp, color = colors.accent, modifier = Modifier.size(16.dp))
        Text(text = text, style = SimmerTextStyle_BodySm, color = colors.textSecondary)
    }
}
