package com.simmer.app.domain

/** What another app shared to Simmer: an Instagram link, or any other text. */
sealed interface SharedContent {
    /** A Reel or post link, cleaned to `https://www.instagram.com/<reel|p>/<id>/` (no tracking parameters). */
    data class Reel(val url: String) : SharedContent

    /** Anything else; it goes into the Add recipe text field as if pasted. */
    data class Text(val text: String) : SharedContent
}

private val INSTAGRAM_LINK = Regex(
    """https?://(?:www\.)?instagram\.com/(?:[A-Za-z0-9._]+/)?(reels?|p)/([A-Za-z0-9_-]+)\S*""",
    RegexOption.IGNORE_CASE,
)

/** The longest phrase Instagram may put before the link ("Check this out") for the text to still count as just a link. */
private const val MAX_LEADING_PHRASE = 40

/** [text] with the Reel's [caption] added under a [label] line, after anything already there. */
fun withCaption(text: String, caption: String, label: String): String {
    val block = "$label\n$caption"
    val before = text.trimEnd()
    if (before.endsWith(block)) return text
    return if (before.isEmpty()) block else "$before\n\n$block"
}

/**
 * The shared [text] as a Reel link or plain text; null when nothing usable was shared. Only text that is
 * essentially just the link (optionally after a short one-line phrase) is a Reel; recipe notes that
 * contain a link stay text.
 */
fun readSharedText(text: String?): SharedContent? {
    val trimmed = text?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    val match = INSTAGRAM_LINK.find(trimmed) ?: return SharedContent.Text(trimmed)
    val leading = trimmed.substring(0, match.range.first).trim()
    val justTheLink = match.range.last == trimmed.lastIndex && leading.length <= MAX_LEADING_PHRASE && '\n' !in leading
    if (!justTheLink) return SharedContent.Text(trimmed)
    val kind = if (match.groupValues[1].equals("p", ignoreCase = true)) "p" else "reel"
    return SharedContent.Reel("https://www.instagram.com/$kind/${match.groupValues[2]}/")
}
