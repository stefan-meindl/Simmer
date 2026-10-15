package com.simmer.app.data

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** How the caption fetch for the shared Reel went. */
enum class CaptionStatus { Loading, Found, NotFound }

/** What the Reel card shows: the link, the fetch [status] and, once found, the caption's [captionLines]. */
data class ReelCard(val url: String, val status: CaptionStatus, val captionLines: Int)

private const val KEY_URL = "reel_url"
private const val KEY_STATUS = "reel_status"
private const val KEY_LINES = "reel_lines"

/**
 * The Reel import of Add recipe: the shared link, the caption fetch and the card's state, kept in [state] so it
 * survives rotation and process death. A found caption is handed to [onCaption] (Add recipe puts it in the text
 * field); the user, not this class, decides whether it is enough.
 */
class ReelImport(
    private val state: SavedStateHandle,
    private val fetcher: CaptionFetcher,
    private val scope: CoroutineScope,
    private val onCaption: suspend (String) -> Unit,
) {
    private var fetch: Job? = null

    val card: Flow<ReelCard?> = combine(
        state.getStateFlow<String?>(KEY_URL, null),
        state.getStateFlow(KEY_STATUS, CaptionStatus.Loading),
        state.getStateFlow(KEY_LINES, 0),
    ) { url, status, lines ->
        url?.let { ReelCard(it, status, lines) }
    }

    /** The Reel link to keep as the new recipe's Source URL; null when the text did not come from a Reel. */
    val sourceUrl: String? get() = state.get<String>(KEY_URL)

    /** A Reel link was shared: show the card loading and fetch its caption. Replaces any earlier Reel. */
    fun start(url: String) {
        fetch(url)
    }

    /** Plain text was shared: no Reel card. */
    fun clear() {
        fetch?.cancel()
        reset(null)
    }

    /** After process death: a fetch that was still loading is asked again; a finished card stays as it was. */
    fun resume() {
        val url = state.get<String>(KEY_URL) ?: return
        if (fetch?.isActive != true && state.get<CaptionStatus>(KEY_STATUS) == CaptionStatus.Loading) fetch(url)
    }

    private fun reset(url: String?) {
        state[KEY_URL] = url
        state[KEY_STATUS] = CaptionStatus.Loading
        state[KEY_LINES] = 0
    }

    private fun fetch(url: String) {
        fetch?.cancel()
        reset(url)
        fetch = scope.launch {
            val caption = fetcher.fetch(url)
            if (caption == null) {
                state[KEY_STATUS] = CaptionStatus.NotFound
            } else {
                // Placed first, so a restored "caption added" always has its caption in the field.
                onCaption(caption)
                state[KEY_LINES] = caption.lines().size
                state[KEY_STATUS] = CaptionStatus.Found
            }
        }
    }
}
