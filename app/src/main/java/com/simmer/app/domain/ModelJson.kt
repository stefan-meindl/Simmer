package com.simmer.app.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** What [readModelJson] found in a model reply. */
sealed interface ModelJson {
    data class Found(val json: Map<String, Any?>) : ModelJson

    /** No `{` at all. */
    data object None : ModelJson

    /** The object opened at character [at] never closes. */
    data class CutOff(val at: Int) : ModelJson

    /** Every closed `{...}` span failed to parse; [message] is the first parser message. */
    data class Invalid(val message: String) : ModelJson
}

private const val MAX_PARSE_MESSAGE = 200

/**
 * The first JSON object in a model [reply] that parses, as nested maps, lists, strings, numbers and booleans
 * (the shape `normalize()` takes), or why there is none. Code fences and chatter around the object are
 * ignored, including balanced braces in prose before it (those spans are skipped). An object that never
 * closes is [ModelJson.CutOff]: the reply stopped there.
 */
fun readModelJson(reply: String): ModelJson {
    var invalid: String? = null
    var from = 0
    while (true) {
        val start = reply.indexOf('{', from)
        if (start < 0) return invalid?.let { ModelJson.Invalid(it) } ?: ModelJson.None
        val end = matchingBrace(reply, start) ?: return ModelJson.CutOff(start)
        try {
            val element = Json.parseToJsonElement(reply.substring(start, end + 1))
            @Suppress("UNCHECKED_CAST")
            return ModelJson.Found(toPlain(element as JsonObject) as Map<String, Any?>)
        } catch (e: kotlinx.serialization.SerializationException) {
            if (invalid == null) invalid = e.message.orEmpty().take(MAX_PARSE_MESSAGE)
            from = end + 1
        }
    }
}

/** The first JSON object in a model [reply] (see [readModelJson]); null when there is none, or it is cut off or malformed. */
fun parseModelJson(reply: String): Map<String, Any?>? = (readModelJson(reply) as? ModelJson.Found)?.json

/** Index of the brace closing the one at [open], skipping strings; null when it never closes. */
private fun matchingBrace(text: String, open: Int): Int? {
    var depth = 0
    var inString = false
    var escaped = false
    for (i in open until text.length) {
        val c = text[i]
        when {
            escaped -> escaped = false
            inString -> if (c == '\\') escaped = true else if (c == '"') inString = false
            c == '"' -> inString = true
            c == '{' -> depth++
            c == '}' -> if (--depth == 0) return i
        }
    }
    return null
}

private fun toPlain(e: JsonElement): Any? = when (e) {
    JsonNull -> null
    is JsonObject -> e.mapValues { toPlain(it.value) }
    is JsonArray -> e.map { toPlain(it) }
    is JsonPrimitive -> when {
        e.isString -> e.content
        e.booleanOrNull != null -> e.booleanOrNull
        e.longOrNull != null -> e.longOrNull!!.let { if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else it }
        else -> e.doubleOrNull
    }
}
