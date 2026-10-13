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

/** Catalogue appliance keys in display order. Custom ones are stored as "c:Name". */
val APPLIANCE_CATALOGUE = listOf(
    "hob", "oven", "microwave", "airfryer", "slowcooker", "pressure", "multicooker", "ricecooker",
    "steamoven", "grill", "charcoal", "smoker", "pizzaoven", "grillpan", "wok", "dutchoven", "sousvide",
    "toaster", "kettle", "processor", "mixer", "handmixer", "blender", "stickblender", "breadmaker",
    "waffle", "icecream", "dehydrator",
)

/** [keys] in catalogue order, then any other keys (custom appliances) alphabetically. */
fun orderAppliances(keys: Set<String>): List<String> =
    APPLIANCE_CATALOGUE.filter { it in keys } + (keys - APPLIANCE_CATALOGUE.toSet()).sorted()

private val APPLIANCE_ENGLISH = mapOf(
    "hob" to "Hob", "oven" to "Oven", "microwave" to "Microwave", "airfryer" to "Air fryer",
    "slowcooker" to "Slow cooker", "pressure" to "Pressure cooker", "multicooker" to "Multi-cooker",
    "ricecooker" to "Rice cooker", "steamoven" to "Steam oven", "grill" to "Gas grill",
    "charcoal" to "Charcoal grill", "smoker" to "Smoker", "pizzaoven" to "Pizza oven",
    "grillpan" to "Grill pan", "wok" to "Wok", "dutchoven" to "Cast-iron casserole",
    "sousvide" to "Sous vide", "toaster" to "Toaster", "kettle" to "Kettle",
    "processor" to "Food processor", "mixer" to "Stand mixer", "handmixer" to "Hand mixer",
    "blender" to "Blender", "stickblender" to "Hand blender", "breadmaker" to "Bread maker",
    "waffle" to "Waffle iron", "icecream" to "Ice cream maker", "dehydrator" to "Dehydrator",
)

/** The prompt clause for the cook's [appliances] and [mode] ([kitchenRule] with English appliance names). */
fun kitchenRuleFor(appliances: Set<String>, mode: KitchenMode): String =
    kitchenRule(
        orderAppliances(appliances).map { if (it.startsWith("c:")) it.removePrefix("c:") else APPLIANCE_ENGLISH[it] ?: it },
        mode.key,
    )

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
