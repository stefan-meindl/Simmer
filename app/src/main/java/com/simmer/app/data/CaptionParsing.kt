package com.simmer.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The caption in an Instagram oEmbed response (its `title`); null for an error body, no title or a blank one. */
fun captionFromOembed(body: String): String? {
    val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
    val title = root["title"] as? JsonPrimitive ?: return null
    return title.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }
}

private const val CAPTION_START = """<div class="Caption">"""
private const val CAPTION_END_MARKERS = """<div class="CaptionComments">"""
private val USERNAME_LINK = Regex("""<a class="CaptionUsername".*?</a>""", RegexOption.DOT_MATCHES_ALL)
private val LINE_BREAK = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
private val TAG = Regex("""<[^>]*>""")
private val ENTITY = Regex("""&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);""")
private val BLANK_RUNS = Regex("""\n{3,}""")

/**
 * The caption in the HTML of an Instagram `/embed/captioned/` page (the `Caption` block without the
 * username and the comment count), as plain text; null when the page has none.
 */
fun captionFromEmbedHtml(html: String): String? {
    val start = html.indexOf(CAPTION_START).takeIf { it >= 0 } ?: return null
    val body = html.substring(start + CAPTION_START.length)
    val end = body.indexOf(CAPTION_END_MARKERS).takeIf { it >= 0 } ?: body.indexOf("""<div class="Footer"""").takeIf { it >= 0 } ?: body.length
    val text = body.substring(0, end)
        .replace(USERNAME_LINK, "")
        .replace(LINE_BREAK, "\n")
        .replace(TAG, "")
        .let(::decodeEntities)
        .lines().joinToString("\n") { it.trimEnd() }
        .replace(BLANK_RUNS, "\n\n")
        .trim()
    return text.takeIf { it.isNotEmpty() }
}

private val NAMED_ENTITIES = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ")

private fun decodeEntities(text: String): String = ENTITY.replace(text) { match ->
    val name = match.groupValues[1]
    when {
        name.startsWith("#x") -> name.drop(2).toIntOrNull(16)?.let(::codePointString)
        name.startsWith("#") -> name.drop(1).toIntOrNull()?.let(::codePointString)
        else -> NAMED_ENTITIES[name]
    } ?: match.value
}

private fun codePointString(code: Int): String? = if (Character.isValidCodePoint(code)) String(Character.toChars(code)) else null
