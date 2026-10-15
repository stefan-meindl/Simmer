package com.simmer.app.ui.add

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
import com.simmer.app.ui.components.RowCard
import com.simmer.app.ui.components.SecondaryButton
import com.simmer.app.ui.theme.LocalSimmerColors
import com.simmer.app.ui.theme.SimmerTextStyle_BodySm
import com.simmer.app.ui.theme.SimmerTextStyle_Caption
import com.simmer.app.ui.theme.SimmerTextStyle_Heading5

/** The link as shown on the card: no scheme or "www". */
internal fun shortReelLink(url: String): String = url.removePrefix("https://").removePrefix("www.").trimEnd('/')

/**
 * The Reel import card above Add recipe's text field: the shared link, the caption fetch status
 * (fetching, found, not found: a status, not an error) and the Listen slot, a disabled placeholder until Listen exists.
 */
@Composable
internal fun ReelCardView(card: ReelCard, modifier: Modifier = Modifier) {
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
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Placeholder: Listening arrives with its own ticket.
                SecondaryButton(
                    text = stringResource(R.string.reel_listen),
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(text = stringResource(R.string.reel_listen_soon), style = SimmerTextStyle_Caption, color = colors.textTertiary)
            }
        }
    }
}
