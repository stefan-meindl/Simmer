package com.simmer.app.domain

import com.simmer.app.domain.model.Recipe

/**
 * How tightly recipe creation limits generated Methods to owned Appliances.
 * Stored as [key]; has no effect on cooking an existing Recipe.
 */
enum class KitchenMode(val key: String) {
    Strict("strict"),
    Medium("medium"),
    Free("free");

    companion object {
        /** Unknown or missing values fall back to the default, [Medium]. */
        fun fromKey(key: String?): KitchenMode = entries.firstOrNull { it.key == key } ?: Medium
    }
}

/** One line of the Settings preview: an ingredient name and its amount in the user's units. */
data class PreviewRow(val name: String, val amount: String)

/**
 * The Settings preview panel: a few ingredients and the oven temperature of one
 * recipe, formatted with the current Units and Spices settings.
 */
data class SettingsPreview(
    val ingredients: List<PreviewRow>,
    /** Null when the recipe has no oven temperature to show. */
    val ovenTemperature: String?,
)

/** Number of servings the preview shows. */
const val PREVIEW_SERVINGS = 4

private val PREVIEW_INGREDIENT_IDS = listOf("mb", "st", "cu", "or")
private val OVEN_TEMPERATURE = Regex("\\d+\\s?°C")

/**
 * Builds the preview for [recipe] (Chilli con carne) at [PREVIEW_SERVINGS] servings
 * with its default Selection.
 */
fun settingsPreview(recipe: Recipe, units: String, spices: String, lang: String): SettingsPreview {
    val resolved = resolve(recipe, PREVIEW_SERVINGS, defaults(recipe), units, spices, lang).ingredients
    val rows = PREVIEW_INGREDIENT_IDS.mapNotNull { id ->
        resolved.firstOrNull { it.id == id }?.let { PreviewRow(it.name, it.amount) }
    }
    val oven = recipe.methods.flatMap { it.options }
        .firstOrNull { it.value == "oven" }
        ?.let { OVEN_TEMPERATURE.find(it.d)?.value }
        ?.let { temp(it, units) }
    return SettingsPreview(rows, oven)
}
