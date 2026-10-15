package com.simmer.app.domain

/** What another app shared to Simmer: an Instagram link, or any other text. */
sealed interface SharedContent {
    /** A Reel or post link, cleaned to `https://www.instagram.com/<reel|p>/<id>/` (no tracking parameters). */
    data class Reel(val url: String) : SharedContent

    /** Anything else; it goes into the Add recipe text field as if pasted. */
    data class Text(val text: String) : SharedContent
}

private val INSTAGRAM_LINK = Regex(
    """https?://(?:www\.)?instagram\.com/(?:[A-Za-z0-9._]+/)?(reels?|p)/([A-Za-z0-9_-]+)""",
    RegexOption.IGNORE_CASE,
)

/** The label line a Reel's caption sits under in the Add recipe text field. */
const val CAPTION_LABEL = "Caption"

/** [text] with the Reel's [caption] added under the [CAPTION_LABEL] line, after anything already there. */
fun withCaption(text: String, caption: String): String {
    val block = "$CAPTION_LABEL\n$caption"
    val before = text.trimEnd()
    return if (before.isEmpty()) block else "$before\n\n$block"
}

/** The shared [text] as a Reel link or plain text; null when nothing usable was shared. */
fun readSharedText(text: String?): SharedContent? {
    val trimmed = text?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    val match = INSTAGRAM_LINK.find(trimmed) ?: return SharedContent.Text(trimmed)
    val kind = if (match.groupValues[1].equals("p", ignoreCase = true)) "p" else "reel"
    return SharedContent.Reel("https://www.instagram.com/$kind/${match.groupValues[2]}/")
}
