package com.simmer.app.domain

/**
 * What to prefill the "Chatbot reply" field with when the cook returns from the chatbot: the [clip]board text
 * if the Reply parser reads it as a recipe, else null (leave the field alone). Null too when the clipboard is
 * empty or is [handled], the text already prefilled once, so editing the field is not undone on every return.
 */
fun autofillReply(clip: String?, handled: String, appliances: Set<String>, kitchenMode: KitchenMode): String? {
    if (clip.isNullOrBlank() || clip == handled) return null
    return clip.takeIf { readChatbotReply(it, id = "u-new", appliances = appliances, kitchenMode = kitchenMode) is ReplyResult.Done }
}
