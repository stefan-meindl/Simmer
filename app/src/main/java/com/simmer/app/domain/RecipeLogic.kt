package com.simmer.app.domain

import com.simmer.app.domain.model.*

// ---------------------------------------------------------------------------
// Condition evaluation
// ---------------------------------------------------------------------------

/**
 * Evaluates a condition string against a selection.
 * Formats: "key=value", "key!=value", "add:id", "!add:id".
 * Null / empty condition always returns true.
 */
fun cond(c: String?, sel: Selection): Boolean {
    if (c.isNullOrBlank()) return true
    if (c.startsWith("!add:")) return sel.add[c.substring(5)] != true
    if (c.startsWith("add:")) return sel.add[c.substring(4)] == true
    if (c.contains("!=")) {
        val parts = c.split("!=", limit = 2)
        return sel.choice[parts[0]] != parts[1]
    }
    val parts = c.split("=", limit = 2)
    if (parts.size != 2) return true
    return sel.choice[parts[0]] == parts[1]
}

/** True for an add-in's condition ("add:id"); false for "!add:id", method picks and null. */
fun isAddInCondition(c: String?): Boolean = c?.startsWith("add:") == true

/**
 * Evaluates a list of conditions (all must match).
 */
fun condList(conditions: List<String>, sel: Selection): Boolean =
    conditions.all { cond(it, sel) }

// ---------------------------------------------------------------------------
// Default selection
// ---------------------------------------------------------------------------

/**
 * Builds the default [Selection] for a recipe: first option for each method,
 * default value for each swap variation.
 */
fun defaults(r: Recipe): Selection {
    val choice = mutableMapOf<String, String>()
    r.variations.forEach { v -> if (v.kind == "swap" && v.def.isNotBlank()) choice[v.id] = v.def }
    r.methods.forEach { m -> if (m.options.isNotEmpty()) choice[m.id] = m.options[0].value }
    return Selection(choice = choice, add = emptyMap())
}

// ---------------------------------------------------------------------------
// Quantity formatting
// ---------------------------------------------------------------------------

private val FRACTION_GLYPHS = mapOf(0.25 to "¼", 0.5 to "½", 0.75 to "¾")

/** Approximate grams per teaspoon for common spice categories. */
private val SPICE_DENSITIES: List<Pair<Regex, Double>> = listOf(
    Regex("oregano|thyme|basil|parsley|rosemary|herb", RegexOption.IGNORE_CASE) to 1.0,
    Regex("flake", RegexOption.IGNORE_CASE) to 1.5,
    Regex("seed", RegexOption.IGNORE_CASE) to 2.2,
    Regex("cinnamon", RegexOption.IGNORE_CASE) to 2.6,
    Regex("sugar", RegexOption.IGNORE_CASE) to 4.2,
    Regex("salt", RegexOption.IGNORE_CASE) to 6.0,
)
private const val DENS_DEFAULT = 2.5

private fun spiceDensity(name: String): Double =
    SPICE_DENSITIES.firstOrNull { (re, _) -> re.containsMatchIn(name) }?.second ?: DENS_DEFAULT

private fun quarter(q: Double): Pair<Double, String> {
    var r = Math.round(q * 4).toDouble() / 4.0
    if (r == 0.0) r = 0.25
    val whole = r.toInt()
    val frac = r - whole
    val s = (if (whole > 0) whole.toString() else "") + (FRACTION_GLYPHS[frac] ?: "")
    return r to (s.ifEmpty { "0" })
}

private fun imperialG(q: Double): String {
    val oz = q / 28.35
    return if (oz >= 16) {
        quarter(oz / 16).second + " lb"
    } else {
        val rounded = if (oz < 4) oz else Math.round(oz * 2).toDouble() / 2.0
        quarter(rounded).second + " oz"
    }
}

private fun imperialMl(q: Double): String {
    return when {
        q < 14 -> quarter(q / 4.93).second + " tsp"
        q < 59 -> quarter(Math.round(q / 14.79 * 2).toDouble() / 2.0).second + " tbsp"
        else -> {
            val (r, s) = quarter(q / 236.6)
            s + if (r > 1) " cups" else " cup"
        }
    }
}

/**
 * Formats a quantity with unit, respecting user preferences for units and spices.
 *
 * @param q        The quantity value
 * @param u        The unit string (e.g. "g", "ml", "tsp", "tbsp", "")
 * @param ing      The ingredient (used for spice density lookup and plural unit)
 * @param units    "metric" or "imperial"
 * @param spices   "spoons" or "grams"
 * @param lang     "en" or "de"; German uses a decimal comma (1,5 g)
 */
fun fmtQty(q: Double, u: String, ing: Ingredient?, units: String, spices: String, lang: String = "en"): String {
    val formatted = fmtQtyDotted(q, u, ing, units, spices)
    return if (lang == "de") formatted.replace(Regex("(\\d)\\.(\\d)"), "$1,$2") else formatted
}

private fun fmtQtyDotted(q: Double, u: String, ing: Ingredient?, units: String, spices: String): String {
    // Spices-in-grams mode
    if (ing != null && spices == "grams" && ing.aisle == "Spices" && (u == "tsp" || u == "tbsp")) {
        val per = spiceDensity(ing.name)
        val g0 = q * (if (u == "tbsp") 3.0 else 1.0) * per
        val g = if (g0 < 10) maxOf(0.5, Math.round(g0 * 2).toDouble() / 2.0) else Math.round(g0).toDouble()
        val formatted = if (g == g.toLong().toDouble()) g.toLong().toString() else g.toString()
        return "$formatted g"
    }

    // Imperial units
    if (units == "imperial") {
        if (u == "g") return imperialG(q)
        if (u == "ml") return imperialMl(q)
    }

    // Metric g/ml
    if (u == "g" || u == "ml") {
        if (q >= 1000) {
            val val_ = Math.round(q / 100.0).toDouble() / 10.0
            val s = if (val_ == val_.toLong().toDouble()) val_.toLong().toString() else val_.toString()
            return "$s ${if (u == "g") "kg" else "l"}"
        }
        val step = if (q < 50) 5 else 10
        val rounded = maxOf(step, Math.round(q / step).toInt() * step)
        return "$rounded $u"
    }

    // Non-unit / tsp / tbsp / etc with fraction glyphs
    var r = Math.round(q * 4).toDouble() / 4.0
    if (r == 0.0) r = 0.25
    val whole = r.toInt()
    val frac = r - whole
    val num = (if (whole > 0) whole.toString() else "") + (FRACTION_GLYPHS[frac] ?: "")
    val numStr = num.ifEmpty { "0" }

    // Use plural unit when quantity rounds to >= 2 and ingredient has a plural form
    if (u.isNotBlank() && r >= 2.0 && ing != null && ing.uPlural.isNotBlank()) {
        return "$numStr ${ing.uPlural}"
    }

    return if (u.isNotBlank()) "$numStr $u" else numStr
}

// ---------------------------------------------------------------------------
// Temperature conversion
// ---------------------------------------------------------------------------

/**
 * If units = "imperial", replaces all °C temperatures with °F (rounded to nearest 5).
 */
fun temp(s: String, units: String): String {
    if (units != "imperial") return s
    return s.replace(Regex("(\\d+)\\s?°C")) { mr ->
        val c = mr.groupValues[1].toDouble()
        val f = Math.round((c * 9.0 / 5.0 + 32.0) / 5.0) * 5
        "$f°F"
    }
}

// ---------------------------------------------------------------------------
// Body segment parsing
// ---------------------------------------------------------------------------

private val LINK_RE = Regex("\\{(\\w+):([^}]+)\\}")

/**
 * Parses a step body string into a list of [BodySegment]s.
 * `{id:text}` tokens become [BodySegment.IngredientLinkSegment]; everything
 * else becomes [BodySegment.TextSegment]. Unknown IDs fall back to plain text.
 */
fun parseBody(b: String, byId: Map<String, ResolvedIngredient>, units: String): List<BodySegment> {
    val segs = mutableListOf<BodySegment>()
    var last = 0
    LINK_RE.findAll(b).forEach { m ->
        val off = m.range.first
        if (off > last) {
            val text = temp(b.substring(last, off), units)
            if (text.isNotEmpty()) segs.add(BodySegment.TextSegment(text))
        }
        val id = m.groupValues[1]
        val text = m.groupValues[2]
        if (byId.containsKey(id)) {
            segs.add(BodySegment.IngredientLinkSegment(id, text))
        } else {
            segs.add(BodySegment.TextSegment(text))
        }
        last = m.range.last + 1
    }
    if (last < b.length) {
        val tail = temp(b.substring(last), units)
        if (tail.isNotEmpty()) segs.add(BodySegment.TextSegment(tail))
    }
    return segs
}

// ---------------------------------------------------------------------------
// Resolve
// ---------------------------------------------------------------------------

/**
 * Resolves a recipe for a given servings count and selection.
 *
 * - Filters ingredients and steps by condition
 * - Applies alt quantity overrides
 * - Scales quantities by servings/base
 * - Sorts steps: prep first, then cook
 * - Labels steps: "Prep 1", "Prep 2", ... / "Step 1", "Step 2", ...
 *   (or German equivalents when lang = "de": "Vorb. 1" / "Schritt 1")
 * - Resolves needs references
 * - Parses body segments
 *
 * @param lang  Language for step labels: "en" (default) or "de"
 */
fun resolve(
    r: Recipe,
    servings: Int,
    sel: Selection,
    units: String = "metric",
    spices: String = "spoons",
    lang: String = "en",
): ResolvedRecipe {
    val k = servings.toDouble() / r.base.toDouble()

    val prepPrefix = if (lang == "de") "Vorb." else "Prep"
    val cookPrefix = if (lang == "de") "Schritt" else "Step"

    // Resolve ingredients
    val ingredients = r.ingredients.filter { cond(it.ifCondition, sel) }.map { ing ->
        var q = ing.quantity
        ing.alt.forEach { (c, v) -> if (cond(c, sel)) q = v }
        val scaled = q * k
        val amount = fmtQty(scaled, ing.unit, ing, units, spices, lang)
        ResolvedIngredient(
            id = ing.id,
            name = ing.name,
            qty = scaled,
            amount = amount,
            unit = ing.unit,
            uPlural = ing.uPlural,
            note = ing.note,
            aisle = ing.aisle,
            tips = ing.tips,
            variation = ing.ifCondition != null,
        )
    }
    val byId = ingredients.associateBy { it.id }

    // Filter and order steps: prep before cook
    val raw = r.steps.filter { cond(it.ifCondition, sel) }
    val ordered = raw.filter { it.phase == "prep" } + raw.filter { it.phase == "cook" }

    // Assign labels
    var prepCount = 0
    var cookCount = 0
    val labeled = ordered.map { s ->
        val label = if (s.phase == "prep") "$prepPrefix ${++prepCount}" else "$cookPrefix ${++cookCount}"
        s to label
    }

    // Build a lookup from step id to labeled step (for needs resolution)
    // When multiple steps share the same id (conditional variants), the last one wins
    // in the filtered list — but here we just need any representative for needs lookup.
    val labelByStepId = labeled.associate { (s, lbl) -> s.id to lbl }

    // Build the resolved steps (needs resolution happens in a second pass)
    // First pass: build without needs
    val resolvedNoNeeds = labeled.map { (s, label) ->
        ResolvedStep(
            id = s.id,
            phase = s.phase,
            title = s.title,
            durationSecs = s.durationSecs,
            body = s.body,
            label = label,
            needs = emptyList(),
            segs = parseBody(s.body, byId, units),
        )
    }
    val resolvedById = resolvedNoNeeds.associateBy { it.id }

    // Second pass: resolve needs
    return ResolvedRecipe(
        ingredients = ingredients,
        steps = labeled.mapIndexed { idx, (s, label) ->
            val needsResolved = (s.needs).mapNotNull { resolvedById[it] }
            resolvedNoNeeds[idx].copy(needs = needsResolved)
        },
    )
}

// ---------------------------------------------------------------------------
// parseQty
// ---------------------------------------------------------------------------

/**
 * Parses a quantity string into a Double.
 * Supports: decimals (1.5, 1,5), slash fractions (1/2), mixed (1 1/2),
 * and Unicode fraction glyphs (½ ¼ ¾).
 */
fun parseQty(s: String): Double {
    val t = s.trim()
        .replace(',', '.')
        .replace("½", " 1/2")
        .replace("¼", " 1/4")
        .replace("¾", " 3/4")
        .trim()
    if (t.isEmpty()) return 0.0
    return t.split(Regex("\\s+")).sumOf { part ->
        val f = Regex("^(\\d+)/(\\d+)$").matchEntire(part)
        if (f != null) {
            f.groupValues[1].toDouble() / f.groupValues[2].toDouble()
        } else {
            part.toDoubleOrNull() ?: 0.0
        }
    }
}

// ---------------------------------------------------------------------------
// Link / linkMarks
// ---------------------------------------------------------------------------

private fun escapeRegex(s: String): String = buildString {
    for (ch in s) {
        if (ch in setOf('.', '*', '+', '?', '^', '$', '{', '}', '(', ')', '|', '[', ']', '\\')) {
            append('\\')
        }
        append(ch)
    }
}

/**
 * Finds ingredient mentions in plain text using longest-match, case-insensitive,
 * Unicode letter boundaries, no overlaps.
 * Returns a list of [Mark]s sorted by start position.
 */
data class Mark(val s: Int, val e: Int, val id: String)

fun linkMarks(text: String, ings: List<Ingredient>): List<Mark> {
    val cands = mutableListOf<Pair<String, String>>() // (id, candidate)
    ings.forEach { ing ->
        val n = ing.name.lowercase().trim()
        if (n.isEmpty()) return@forEach
        val last = n.split(Regex("\\s+")).last()
        setOf(n, n.replace(Regex("e?s$"), ""), last, last.replace(Regex("e?s$"), ""))
            .filter { it.length >= 3 }
            .forEach { c -> cands.add(ing.id to c) }
    }
    cands.sortByDescending { (_, c) -> c.length }

    val marks = mutableListOf<Mark>()
    cands.forEach { (id, c) ->
        val re = Regex("(?<![\\p{L}])${escapeRegex(c)}(?:e?s)?(?![\\p{L}])", setOf(RegexOption.IGNORE_CASE))
        re.findAll(text).forEach { m ->
            val start = m.range.first
            val end = m.range.last + 1
            if (marks.none { start < it.e && end > it.s }) {
                marks.add(Mark(start, end, id))
            }
        }
    }
    return marks.sortedBy { it.s }
}

/**
 * Wraps ingredient mentions in [text] as `{id:text}` links.
 * Uses longest-match, case-insensitive, Unicode letter boundaries.
 */
fun link(text: String, ings: List<Ingredient>): String {
    val marks = linkMarks(text, ings)
    val sb = StringBuilder()
    var last = 0
    marks.forEach { mark ->
        sb.append(text, last, mark.s)
        sb.append("{${mark.id}:${text.substring(mark.s, mark.e)}}")
        last = mark.e
    }
    sb.append(text, last, text.length)
    return sb.toString()
}

// ---------------------------------------------------------------------------
// normalize
// ---------------------------------------------------------------------------

private val CATS = setOf("Mains", "Pasta", "Breakfast", "Sides", "Soups", "Salads", "Desserts", "Baking")
private val TAGS = setOf("Vegetarian", "Quick", "Spicy", "One-pot", "Make-ahead")
private val ICONS = setOf("flame", "chef-hat", "utensils", "egg", "carrot")
private val VALID_ICONS_OPTION = setOf("flame", "zap", "timer", "layers", "clock")

/**
 * Validates and sanitizes a raw recipe map (as produced by the AI pipeline).
 * Strips invalid conditions, validates step durations (30s–24h),
 * enforces ≥2 method options, removes invalid variation refs.
 */
fun normalize(j: Map<String, Any?>): Recipe {
    val ingredients = (j["ingredients"] as? List<*>)
        ?.filterIsInstance<Map<*, *>>()
        ?.filter { it["name"] != null }
        ?.mapIndexed { k, i ->
            val uRaw = i["u"]
            val (u, uPlural) = when {
                uRaw is List<*> && uRaw.size == 2 -> (uRaw[0]?.toString() ?: "") to (uRaw[1]?.toString() ?: "")
                uRaw is String -> uRaw to ""
                else -> "" to ""
            }
            Ingredient(
                id = i["id"]?.toString() ?: "i${k + 1}",
                name = i["name"]?.toString() ?: "Ingredient",
                quantity = (i["q"] as? Number)?.toDouble()?.takeIf { it > 0 } ?: 1.0,
                unit = u,
                uPlural = uPlural,
                note = i["note"]?.toString() ?: "",
                aisle = i["aisle"]?.toString() ?: "",
                tips = (i["tips"] as? List<*>)
                    ?.filterIsInstance<String>()
                    ?.take(3)
                    ?: emptyList(),
                ifCondition = i["if"]?.toString()?.takeIf { it.isNotBlank() },
            )
        } ?: emptyList()

    val ingIds = ingredients.map { it.id }.toSet()

    val methods = (j["methods"] as? List<*>)
        ?.filterIsInstance<Map<*, *>>()
        ?.filter { m -> m["id"] != null && (m["options"] as? List<*>)?.size?.let { it >= 2 } == true }
        ?.map { m ->
            val options = (m["options"] as List<*>).filterIsInstance<Map<*, *>>().mapIndexed { k, o ->
                MethodOption(
                    value = o["v"]?.toString() ?: "o$k",
                    label = o["l"]?.toString() ?: "Option ${k + 1}",
                    icon = o["icon"]?.toString()?.takeIf { it in VALID_ICONS_OPTION } ?: "flame",
                    time = o["time"]?.toString() ?: "",
                    d = o["d"]?.toString() ?: "",
                )
            }
            Method(
                id = m["id"].toString(),
                label = m["label"]?.toString() ?: "Method",
                q = m["q"]?.toString() ?: "How do you want to cook it?",
                options = options,
            )
        } ?: emptyList()

    val mset = methods.associate { m -> m.id to m.options.map { it.value }.toSet() }

    val variations = (j["variations"] as? List<*>)
        ?.filterIsInstance<Map<*, *>>()
        ?.filter { v -> v["id"] != null && ingIds.contains(v["ing"]?.toString()) }
        ?.map { v ->
            val kind = v["kind"]?.toString() ?: "add"
            if (kind == "swap") {
                val options = (v["options"] as? List<*>)?.filterIsInstance<Map<*, *>>()?.mapIndexed { k, o ->
                    Option(
                        value = o["v"]?.toString() ?: "o$k",
                        label = o["l"]?.toString() ?: "Option ${k + 1}",
                    )
                } ?: emptyList()
                Variation(
                    id = v["id"].toString(),
                    kind = "swap",
                    label = v["label"]?.toString() ?: "Swap",
                    options = options,
                    def = v["def"]?.toString() ?: "",
                    ing = v["ing"]?.toString() ?: "",
                )
            } else {
                Variation(
                    id = v["id"].toString(),
                    kind = "add",
                    label = v["label"]?.toString() ?: "Add-in",
                    d = v["d"]?.toString() ?: "",
                    ing = v["ing"]?.toString() ?: "",
                )
            }
        } ?: emptyList()

    val vset = variations.map { it.id }.toSet()

    fun condOk(c: String?): Boolean {
        if (c.isNullOrBlank()) return true
        val mAdd = Regex("^!?add:(\\w+)$").matchEntire(c)
        if (mAdd != null) return vset.contains(mAdd.groupValues[1])
        val mMethod = Regex("^(\\w+)!?=(\\w+)$").matchEntire(c)
        if (mMethod != null) return mset[mMethod.groupValues[1]]?.contains(mMethod.groupValues[2]) == true
        return false
    }

    // Strip invalid conditions from ingredients
    val cleanedIngredients = ingredients.map { ing ->
        if (condOk(ing.ifCondition)) ing else ing.copy(ifCondition = null)
    }

    val steps = (j["steps"] as? List<*>)
        ?.filterIsInstance<Map<*, *>>()
        ?.filter { s -> s["b"] != null }
        ?.mapIndexed { k, s ->
            val rawD = (s["d"] as? Number)?.toDouble()?.toInt() ?: 0
            val d = if (rawD > 0) minOf(86400, maxOf(30, rawD)) else 0
            val rawIf = s["if"]?.toString()?.takeIf { it.isNotBlank() }
            val cleanIf = if (condOk(rawIf)) rawIf else null
            val rawBody = s["b"].toString()
            // Strip links for unknown ingredient IDs
            val body = rawBody.replace(Regex("\\{(\\w+):([^}]+)\\}")) { mr ->
                val id = mr.groupValues[1]
                val text = mr.groupValues[2]
                if (ingIds.contains(id)) mr.value else text
            }
            Step(
                id = s["id"]?.toString() ?: "s${k + 1}",
                phase = if (s["ph"]?.toString() == "prep") "prep" else "cook",
                title = s["t"]?.toString() ?: "Step ${k + 1}",
                durationSecs = d,
                body = body,
                needs = (s["needs"] as? List<*>)?.map { it.toString() } ?: emptyList(),
                ifCondition = cleanIf,
            )
        } ?: emptyList()

    return Recipe(
        id = "u${System.currentTimeMillis().toString(36)}",
        mine = true,
        title = j["title"]?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: "Untitled recipe",
        summary = j["summary"]?.toString() ?: "",
        category = (j["category"]?.toString())?.takeIf { it in CATS } ?: "Mains",
        tags = (j["tags"] as? List<*>)?.filterIsInstance<String>()?.filter { it in TAGS } ?: emptyList(),
        time = maxOf(5, (j["time"] as? Number)?.toInt() ?: 30),
        difficulty = (j["difficulty"]?.toString())?.takeIf { it in setOf("Easy", "Medium", "Hard") } ?: "Easy",
        base = minOf(12, maxOf(1, (j["base"] as? Number)?.toInt() ?: 4)),
        icon = (j["icon"]?.toString())?.takeIf { it in ICONS } ?: "utensils",
        variations = variations,
        methods = methods,
        ingredients = cleanedIngredients,
        steps = steps,
        notes = (j["notes"] as? List<*>)?.filterIsInstance<String>()?.take(5) ?: emptyList(),
    )
}

// ---------------------------------------------------------------------------
// kitchenRule
// ---------------------------------------------------------------------------

/**
 * Generates a system-prompt clause for the AI import pipeline based on
 * the user's appliances and strictness mode.
 *
 * @param appliances  list of appliance names the user owns
 * @param mode        "free" | "medium" | "strict"
 */
fun kitchenRule(appliances: List<String>?, mode: String): String {
    if (mode == "free" || appliances == null) return ""
    val owned = if (appliances.isNotEmpty()) appliances.joinToString(", ") else "none"
    val base = "\n- The cook owns these appliances: $owned. Never offer a method option for an appliance not on this list. If the recipe needs an appliance they do not have, adapt it to one they own (for example oven instead of grill, a knife instead of a food processor) and mention the change in notes."
    return if (mode == "strict") {
        base + " Be strict: assume no other equipment beyond knives, a chopping board, bowls, spoons, a sieve, pots and pans (pots and pans only if Hob is listed). Rewrite any step that needs other tools, such as a grater, whisk, peeler or rolling pin, and mention it in notes."
    } else {
        base + " Common small tools (grater, whisk, peeler, sieve, rolling pin, baking tray, measuring jug) are fine to assume; only adapt steps for larger appliances that are missing."
    }
}
