package com.simmer.app.domain

import com.simmer.app.domain.model.Recipe

/** Why a pasted chatbot reply is not a recipe; the cook sees one inline message, the Fix prompt names the kind. */
enum class ReplyError { NoJson, CutOff, NotValid, NoTitle, NoIngredient, NoStep }

/** What [readChatbotReply] made of a pasted reply. */
sealed interface ReplyResult {
    data class Done(val recipe: Recipe) : ReplyResult

    /** [detail] is what the parser knows: the character a cut-off object opened at, or the parser's message for invalid JSON; else empty. */
    data class Failed(val error: ReplyError, val detail: String = "") : ReplyResult
}

/**
 * A chatbot's pasted [reply] as a new User Recipe with id [id], or the first [ReplyError] found. Code
 * fences and chatter around the JSON are ignored. Method options for appliances the cook lacks go with
 * their steps (the Kitchen rule, for Strict and Medium). A recipe needs a title, an Ingredient and a Step.
 */
fun readChatbotReply(reply: String, id: String, appliances: Set<String>, kitchenMode: KitchenMode): ReplyResult {
    val json = when (val read = readModelJson(reply)) {
        is ModelJson.Found -> read.json
        ModelJson.None -> return ReplyResult.Failed(ReplyError.NoJson)
        is ModelJson.CutOff -> return ReplyResult.Failed(ReplyError.CutOff, read.at.toString())
        is ModelJson.Invalid -> return ReplyResult.Failed(ReplyError.NotValid, read.message)
    }
    if ((json["title"] as? String).isNullOrBlank()) return ReplyResult.Failed(ReplyError.NoTitle)
    val owned = if (kitchenMode == KitchenMode.Free) null else appliances
    // A Method whose options are all for missing appliances stays (nothing to offer instead), as in the Pipeline.
    val (methods, steps) = filterMethodsByKitchen(mapsIn(json["methods"]), mapsIn(json["steps"]), owned)
    // Each side has its own Methods, so the same rule applies per side.
    val sides = (json["sides"] as? List<*>)?.map { side ->
        if (side !is Map<*, *>) return@map side
        val raw = side.entries.associate { it.key.toString() to it.value }
        val (sideMethods, sideSteps) = filterMethodsByKitchen(mapsIn(raw["methods"]), mapsIn(raw["steps"]), owned)
        raw + mapOf("methods" to sideMethods, "steps" to sideSteps)
    }
    val recipe = normalize(json + mapOf("methods" to methods, "steps" to steps) + if (sides != null) mapOf("sides" to sides) else emptyMap())
    if (recipe.ingredients.isEmpty()) return ReplyResult.Failed(ReplyError.NoIngredient)
    if (recipe.steps.isEmpty()) return ReplyResult.Failed(ReplyError.NoStep)
    return ReplyResult.Done(recipe.copy(id = id, mine = true))
}
