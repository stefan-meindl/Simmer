package com.simmer.app.ui.add

import android.content.Context
import android.content.res.Configuration
import com.simmer.app.R
import com.simmer.app.domain.FixPromptTexts
import com.simmer.app.domain.ReaderPromptTexts
import com.simmer.app.domain.ReplyError
import com.simmer.app.domain.fixPrompt
import com.simmer.app.domain.readerPrompt
import com.simmer.app.ui.settings.APPLIANCE_LABELS
import java.util.Locale

/**
 * The Reader prompt for [ui] in the Settings language, which may differ from the device's (as with the
 * Pipeline's notes): its prose from the string resources, appliance names as the cook sees them in that language.
 */
fun readerPromptFor(context: Context, ui: AddRecipeUi.Chatbot): String {
    val config = Configuration(context.resources.configuration).apply { setLocale(Locale(ui.lang)) }
    val localized = context.createConfigurationContext(config)
    val texts = ReaderPromptTexts(
        intro = localized.getString(R.string.reader_prompt_intro),
        rules = localized.getString(R.string.reader_prompt_rules),
        schemaHeading = localized.getString(R.string.reader_prompt_schema_heading),
        exampleHeading = localized.getString(R.string.reader_prompt_example_heading),
        kitchenAny = localized.getString(R.string.reader_prompt_kitchen_any),
        kitchenOnly = localized.getString(R.string.reader_prompt_kitchen_only),
        kitchenNone = localized.getString(R.string.reader_prompt_kitchen_none),
        kitchenStrict = localized.getString(R.string.reader_prompt_kitchen_strict),
        kitchenMedium = localized.getString(R.string.reader_prompt_kitchen_medium),
        recipeTextHeading = localized.getString(R.string.reader_prompt_recipe_text_heading),
        noRecipeText = localized.getString(R.string.reader_prompt_no_recipe_text),
    )
    val names = ui.kitchen.appliances.map { key ->
        if (key.startsWith("c:")) key.removePrefix("c:") else APPLIANCE_LABELS[key]?.let(localized::getString) ?: key
    }
    return readerPrompt(texts, ui.promptText, names, ui.kitchen.mode, ui.lang)
}

/** The Fix prompt for [error] in the Settings language [lang], which may differ from the device's. */
fun fixPromptFor(context: Context, lang: String, error: ReplyError): String {
    val config = Configuration(context.resources.configuration).apply { setLocale(Locale(lang)) }
    val localized = context.createConfigurationContext(config)
    val errors = mapOf(
        ReplyError.NoJson to R.string.fix_prompt_error_no_json,
        ReplyError.CutOff to R.string.fix_prompt_error_cut_off,
        ReplyError.NotValid to R.string.fix_prompt_error_not_valid,
        ReplyError.NoTitle to R.string.fix_prompt_error_no_title,
        ReplyError.NoIngredient to R.string.fix_prompt_error_no_ingredient,
        ReplyError.NoStep to R.string.fix_prompt_error_no_step,
    ).mapValues { localized.getString(it.value) }
    return fixPrompt(FixPromptTexts(localized.getString(R.string.fix_prompt_instruction), errors), error)
}
