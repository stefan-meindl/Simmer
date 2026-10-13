package com.simmer.app.domain

import com.simmer.app.domain.model.Recipe

/**
 * A chatbot's pasted [reply] as a new User Recipe with id [id], or null when it holds no usable
 * recipe. Code fences and chatter around the JSON are ignored. Method options for appliances the
 * cook lacks go with their steps (the Kitchen rule, for Strict and Medium). A recipe needs a title,
 * an Ingredient and a Step.
 */
fun parseChatbotReply(reply: String, id: String, appliances: Set<String>, kitchenMode: KitchenMode): Recipe? {
    val json = parseModelJson(reply) ?: return null
    if ((json["title"] as? String).isNullOrBlank()) return null
    val owned = if (kitchenMode == KitchenMode.Free) null else appliances
    val (methods, steps) = filterMethodsByKitchen(mapsIn(json["methods"]), mapsIn(json["steps"]), owned)
    val recipe = normalize(json + mapOf("methods" to methods, "steps" to steps))
    if (recipe.ingredients.isEmpty() || recipe.steps.isEmpty()) return null
    return recipe.copy(id = id, mine = true)
}

private fun mapsIn(value: Any?): List<Map<String, Any?>> =
    (value as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().map { m -> m.entries.associate { it.key.toString() to it.value } }
