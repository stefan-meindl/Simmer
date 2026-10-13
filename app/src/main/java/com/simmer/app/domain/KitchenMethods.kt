package com.simmer.app.domain

private val APPLIANCE_TOKENS: Map<String, List<String>> = mapOf(
    "hob" to listOf("hob", "stovetop", "stove top", "stove", "cooktop", "hotplate"),
    "oven" to listOf("oven"),
    "microwave" to listOf("microwave"),
    "airfryer" to listOf("air fryer", "air-fryer", "airfryer", "air fry"),
    "slowcooker" to listOf("slow cooker", "slow-cooker", "crockpot", "crock pot"),
    "pressure" to listOf("pressure cooker", "instant pot"),
    "multicooker" to listOf("multi-cooker", "multicooker", "multi cooker"),
    "ricecooker" to listOf("rice cooker", "rice-cooker"),
    "steamoven" to listOf("steam oven"),
    "grill" to listOf("gas grill", "grill", "barbecue", "bbq"),
    "charcoal" to listOf("charcoal"),
    "smoker" to listOf("smoker"),
    "pizzaoven" to listOf("pizza oven"),
    "grillpan" to listOf("grill pan", "griddle pan"),
    "wok" to listOf("wok"),
    "dutchoven" to listOf("dutch oven", "cast-iron casserole", "cast iron casserole"),
    "sousvide" to listOf("sous vide", "sous-vide", "sousvide"),
    "toaster" to listOf("toaster"),
    "kettle" to listOf("kettle"),
    "processor" to listOf("food processor"),
    "mixer" to listOf("stand mixer"),
    "handmixer" to listOf("hand mixer"),
    "blender" to listOf("blender"),
    "stickblender" to listOf("hand blender", "stick blender", "immersion blender"),
    "breadmaker" to listOf("bread maker"),
    "waffle" to listOf("waffle iron"),
    "icecream" to listOf("ice cream maker"),
    "dehydrator" to listOf("dehydrator"),
)

/** The catalogue appliance a Method option names (longest match wins, so "steam oven" is not "oven"); null when none. */
fun applianceOfOption(value: String, label: String): String? {
    val text = "$label $value".lowercase()
    return APPLIANCE_TOKENS.flatMap { (key, tokens) -> tokens.map { key to it } }
        .filter { (_, token) -> Regex("(?<![\\p{L}])${Regex.escape(token)}(?![\\p{L}])").containsMatchIn(text) }
        .maxByOrNull { it.second.length }
        ?.first
}

/**
 * Applies the kitchen rule in code, as the prompt asks for it: Method options that need a catalogue
 * appliance not in [owned] go, with the steps that only belong to them. A Method left with one option
 * is no choice any more: it goes and that option's steps lose their condition. A Method whose options
 * are all missing stays as it is (nothing to offer instead). [owned] null means Free for all.
 * Methods and steps are the maps of the pass 2 reply (`id`, `options[].v|l`, `steps[].if`).
 */
fun filterMethodsByKitchen(
    methods: List<Map<String, Any?>>,
    steps: List<Map<String, Any?>>,
    owned: Set<String>?,
): Pair<List<Map<String, Any?>>, List<Map<String, Any?>>> {
    if (owned == null) return methods to steps
    var keptMethods = emptyList<Map<String, Any?>>()
    var keptSteps = steps
    for (method in methods) {
        val id = method["id"].toString()
        val options = mapsIn(method["options"])
        val (allowed, missing) = options.partition {
            val appliance = applianceOfOption(it["v"].toString(), it["l"].toString())
            appliance == null || appliance in owned
        }
        if (missing.isEmpty() || allowed.isEmpty()) {
            keptMethods = keptMethods + method
            continue
        }
        val missingConditions = missing.map { "$id=${it["v"]}" }.toSet()
        keptSteps = keptSteps.filter { it["if"] !in missingConditions }
        if (allowed.size == 1) {
            val only = "$id=${allowed.single()["v"]}"
            keptSteps = keptSteps.map { if (it["if"] == only) it - "if" else it }
        } else {
            keptMethods = keptMethods + (method + ("options" to allowed))
        }
    }
    return keptMethods to keptSteps
}

/** The objects of a JSON array [value] as string-keyed maps; anything else is skipped. */
internal fun mapsIn(value: Any?): List<Map<String, Any?>> =
    (value as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().map { m -> m.entries.associate { it.key.toString() to it.value } }
