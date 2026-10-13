package com.simmer.app.domain

/**
 * The prose of the Fix prompt in one language (string resources `fix_prompt_*`): [instruction] has
 * `%1$s` for the error and `%2$s` for the JSON shape; [errors] holds the parse error per [ReplyError],
 * where `%1$s` (when present) is the [ReplyResult.Failed.detail] (the position of a cut-off object or the parser's message).
 */
data class FixPromptTexts(val instruction: String, val errors: Map<ReplyError, String>)

/**
 * The Fix prompt: what the cook sends back to the chatbot after a reply that was not a recipe. It
 * names the exact parse [error] with the parser's [detail] and asks for only valid JSON in the Reader
 * prompt's shape, like the Repair retry.
 */
fun fixPrompt(texts: FixPromptTexts, error: ReplyError, detail: String = ""): String =
    texts.instruction.format(texts.errors.getValue(error).format(detail), READER_PROMPT_SCHEMA)
