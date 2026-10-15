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
        state.getStateFlow(KEY_STATUS, CaptionStatus.Loading.name),
        state.getStateFlow(KEY_LINES, 0),
    ) { url, status, lines ->
        url?.let { ReelCard(it, CaptionStatus.entries.firstOrNull { s -> s.name == status } ?: CaptionStatus.NotFound, lines) }
    }

    /** The Reel link to keep as the new recipe's Source URL; null when the text did not come from a Reel. */
    val sourceUrl: String? get() = state.get<String>(KEY_URL)

    /** A Reel link was shared: show the card loading and fetch its caption. Replaces any earlier Reel. */
    fun start(url: String) {
        state[KEY_URL] = url
        fetch(url)
    }

    /** Plain text was shared: no Reel card. */
    fun clear() {
        fetch?.cancel()
        state[KEY_URL] = null
        state[KEY_STATUS] = CaptionStatus.Loading.name
        state[KEY_LINES] = 0
    }

    /** After process death: a fetch that was still loading is asked again; a finished card stays as it was. */
    fun resume() {
        val url = state.get<String>(KEY_URL) ?: return
        if (fetch?.isActive != true && state.get<String>(KEY_STATUS) == CaptionStatus.Loading.name) fetch(url)
    }

    private fun fetch(url: String) {
        fetch?.cancel()
        state[KEY_STATUS] = CaptionStatus.Loading.name
        state[KEY_LINES] = 0
        fetch = scope.launch {
            val caption = fetcher.fetch(url)
            if (caption == null) {
                state[KEY_STATUS] = CaptionStatus.NotFound.name
            } else {
                state[KEY_LINES] = caption.lines().size
                state[KEY_STATUS] = CaptionStatus.Found.name
                onCaption(caption)
            }
        }
    }
}
