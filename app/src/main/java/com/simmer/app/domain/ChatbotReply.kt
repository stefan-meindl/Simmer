package com.simmer.app.domain

import com.simmer.app.domain.model.Recipe

/** Why a pasted chatbot reply is not a recipe; the cook sees one inline message, the Fix prompt names the kind. */
enum class ReplyError { NoJson, CutOff, NotValid, NoTitle, NoIngredient, NoStep }

/** What [readChatbotReply] made of a pasted reply. */
sealed interface ReplyResult {
    data class Done(val recipe: Recipe) : ReplyResult
    data class Failed(val error: ReplyError) : ReplyResult
}

/**
 * A chatbot's pasted [reply] as a new User Recipe with id [id], or the first [ReplyError] found. Code
 * fences and chatter around the JSON are ignored. Method options for appliances the cook lacks go with
 * their steps (the Kitchen rule, for Strict and Medium). A recipe needs a title, an Ingredient and a Step.
 */
fun readChatbotReply(reply: String, id: String, appliances: Set<String>, kitchenMode: KitchenMode): ReplyResult {
    val json = parseModelJson(reply) ?: return ReplyResult.Failed(
        when {
            isCutOffJson(reply) -> ReplyError.CutOff
            reply.indexOf('{') < 0 -> ReplyError.NoJson
            else -> ReplyError.NotValid
        },
    )
    if ((json["title"] as? String).isNullOrBlank()) return ReplyResult.Failed(ReplyError.NoTitle)
    val owned = if (kitchenMode == KitchenMode.Free) null else appliances
    // A Method whose options are all for missing appliances stays (nothing to offer instead), as in the Pipeline.
    val (methods, steps) = filterMethodsByKitchen(mapsIn(json["methods"]), mapsIn(json["steps"]), owned)
    val recipe = normalize(json + mapOf("methods" to methods, "steps" to steps))
    if (recipe.ingredients.isEmpty()) return ReplyResult.Failed(ReplyError.NoIngredient)
    if (recipe.steps.isEmpty()) return ReplyResult.Failed(ReplyError.NoStep)
    return ReplyResult.Done(recipe.copy(id = id, mine = true))
}

/** The recipe in a pasted chatbot [reply], or null when it holds none. See [readChatbotReply]. */
fun parseChatbotReply(reply: String, id: String, appliances: Set<String>, kitchenMode: KitchenMode): Recipe? =
    (readChatbotReply(reply, id, appliances, kitchenMode) as? ReplyResult.Done)?.recipe
