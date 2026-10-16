package com.simmer.app.ui.add

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import com.simmer.app.R
import com.simmer.app.domain.FixPromptTexts
import com.simmer.app.domain.ReaderPromptTexts
import com.simmer.app.domain.ReplyError
import com.simmer.app.domain.ReplyResult
import com.simmer.app.domain.fixPrompt
import com.simmer.app.domain.readerPrompt
import com.simmer.app.ui.settings.APPLIANCE_LABELS
import java.util.Locale

/** [context] with resources in the Settings language [lang], which may differ from the device's (as with the Pipeline's notes). */
private fun localizedContext(context: Context, lang: String): Context {
    val config = Configuration(context.resources.configuration).apply { setLocale(Locale(lang)) }
    return context.createConfigurationContext(config)
}

/**
 * The Reader prompt for [ui] in the Settings language: its prose from the string resources,
 * appliance names as the cook sees them in that language.
 */
fun readerPromptFor(context: Context, ui: AddRecipeUi.Chatbot): String {
    val localized = localizedContext(context, ui.lang)
    val texts = ReaderPromptTexts(
        intro = localized.getString(R.string.reader_prompt_intro),
        rules = localized.getString(R.string.reader_prompt_rules),
        enrichment = localized.getString(R.string.reader_prompt_enrichment),
        faithful = localized.getString(R.string.reader_prompt_faithful),
        liberty = localized.getString(R.string.reader_prompt_liberty),
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
    return readerPrompt(texts, ui.promptText, names, ui.kitchen.mode, ui.lang, ui.style)
}

private const val GEMINI_PACKAGE = "com.google.android.apps.bard"

/** Whether the Gemini app is installed (the manifest's `<queries>` entry makes it visible). */
fun isGeminiInstalled(context: Context): Boolean =
    context.packageManager.getLaunchIntentForPackage(GEMINI_PACKAGE) != null

/** Opens the Gemini app with [prompt] shared as one text extra; false if it cannot be opened. Gemini waits for the user to send. */
fun openInGemini(context: Context, prompt: String): Boolean {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, prompt)
        setPackage(GEMINI_PACKAGE)
    }
    return try {
        context.startActivity(send)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

/** The Fix prompt for [failed] in the Settings language [lang]. */
fun fixPromptFor(context: Context, lang: String, failed: ReplyResult.Failed): String {
    val localized = localizedContext(context, lang)
    // Exhaustive `when`: a new ReplyError does not compile until it has a string.
    val errors = ReplyError.entries.associateWith { error ->
        localized.getString(
            when (error) {
                ReplyError.NoJson -> R.string.fix_prompt_error_no_json
                ReplyError.CutOff -> R.string.fix_prompt_error_cut_off
                ReplyError.NotValid -> R.string.fix_prompt_error_not_valid
                ReplyError.NoTitle -> R.string.fix_prompt_error_no_title
                ReplyError.NoIngredient -> R.string.fix_prompt_error_no_ingredient
                ReplyError.NoStep -> R.string.fix_prompt_error_no_step
            },
        )
    }
    return fixPrompt(FixPromptTexts(localized.getString(R.string.fix_prompt_instruction), errors), failed.error, failed.detail)
}
