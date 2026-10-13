package com.simmer.app.domain

/** Most characters of pasted text the Pipeline reads (about 1,500 tokens, leaving room on the small model). */
const val MAX_RECIPE_CHARS = 6_000

/** What pass 1 reads, and whether the paste was cut at [MAX_RECIPE_CHARS]. */
data class TrimmedText(val text: String, val truncated: Boolean)

/** A preamble longer than this is blog story, not a recipe header. */
private const val PREAMBLE_KEEP_CHARS = 300

private val INGREDIENTS_HEADING = Regex("^\\W*(ingredients?|zutaten)\\b.*$", RegexOption.IGNORE_CASE)
private val COMMENTS_HEADING = Regex("^\\W*(\\d+\\s+)?(comments?|reviews?|kommentare?|bewertungen)\\b.*$", RegexOption.IGNORE_CASE)
private val HEADER_FACT = Regex("serves?|makes|yield|prep|cook|total|time|portion|ergibt|zeit|min\\b|stunde|hour", RegexOption.IGNORE_CASE)
private val NOISE_LINE = Regex("^\\W*(jump to recipe|print recipe|rezept drucken|zum rezept)\\W*$", RegexOption.IGNORE_CASE)

/**
 * Cleans pasted text for pass 1: repeated whitespace collapses, comments and everything after them
 * go, and a long blog preamble before the ingredients is dropped (the title and lines that read
 * like servings or times stay). Then the text is capped at [MAX_RECIPE_CHARS], cutting at a line break.
 */
fun trimRecipeText(raw: String): TrimmedText {
    var lines = raw.lines().map { it.replace(Regex("[ \\t]+"), " ").trim() }.filterNot { NOISE_LINE.matches(it) }

    val commentsAt = lines.indexOfFirst { COMMENTS_HEADING.matches(it) && lines.indexOf(it) > 0 }
    if (commentsAt > 0) lines = lines.take(commentsAt)

    val ingredientsAt = lines.indexOfFirst { INGREDIENTS_HEADING.matches(it) }
    if (ingredientsAt > 0) {
        val preamble = lines.take(ingredientsAt)
        if (preamble.joinToString("\n").length > PREAMBLE_KEEP_CHARS) {
            val title = preamble.firstOrNull { it.isNotEmpty() }
            val facts = preamble.drop(1).filter { it.isNotEmpty() && it.length <= 80 && HEADER_FACT.containsMatchIn(it) }
            lines = listOfNotNull(title) + facts + listOf("") + lines.drop(ingredientsAt)
        }
    }

    val cleaned = lines.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
    if (cleaned.length <= MAX_RECIPE_CHARS) return TrimmedText(cleaned, truncated = false)
    val head = cleaned.take(MAX_RECIPE_CHARS)
    val lineBreak = head.lastIndexOf('\n')
    val cut = if (lineBreak > MAX_RECIPE_CHARS / 2) head.take(lineBreak) else head
    return TrimmedText(cut.trimEnd(), truncated = true)
}
