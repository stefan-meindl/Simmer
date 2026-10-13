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

/**
 * The first JSON object in a model [reply] as nested maps, lists, strings, numbers and booleans
 * (the shape `normalize()` takes); null when there is none or it is cut off or malformed.
 * Code fences and chatter around the object are ignored.
 */
fun parseModelJson(reply: String): Map<String, Any?>? {
    val start = reply.indexOf('{')
    if (start < 0) return null
    val end = matchingBrace(reply, start) ?: return null
    val element = try {
        Json.parseToJsonElement(reply.substring(start, end + 1))
    } catch (e: kotlinx.serialization.SerializationException) {
        return null
    }
    @Suppress("UNCHECKED_CAST")
    return (element as? JsonObject)?.let { toPlain(it) as Map<String, Any?> }
}

/** True when [reply] opens a JSON object that never closes (the chatbot stopped mid-reply). */
fun isCutOffJson(reply: String): Boolean {
    val start = reply.indexOf('{')
    return start >= 0 && matchingBrace(reply, start) == null
}

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
