package com.simmer.app.domain

import com.simmer.app.domain.model.Recipe
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/** The Pipeline's four passes, in order. They are the stages the Working screen lists. */
enum class PipelineStage { Ingredients, Splitting, Linking, Timers }

/** What one conversion works from: the pasted [text], the app [lang] and the cook's kitchen. */
data class ImportRequest(
    val text: String,
    val lang: String,
    val appliances: Set<String>,
    val kitchenMode: KitchenMode,
    val style: ImportStyle = ImportStyle.Faithful,
)

sealed interface PipelineResult {
    /** [repairs] counts the repair retries used; the fixture target is 0 for 90% of recipes. */
    data class Done(val recipe: Recipe, val repairs: Int) : PipelineResult

    /** The pass at [stage] gave no usable JSON even after the repair retry, or the text is not a recipe. */
    data class Failed(val stage: PipelineStage) : PipelineResult
}

/** One pass produced nothing within its time limit (the model stalled). */
class PassTimeoutException(val stage: PipelineStage) : Exception("Pass $stage timed out")

/** Each pass asks for this many tokens at most (between 1,200 and 2,000). */
private const val TOKENS_LARGE = 2_000
private const val TOKENS_SMALL = 1_200

/** Liberty adds suggestions, tips and alternative methods to the two big passes, so they may run longer (the engines cap near 4,096). */
private const val TOKENS_LIBERTY = 3_000

/** The Liberty sides pass writes up to three small recipes, so it gets the big budget. */
private const val TOKENS_SIDES = 3_000

/** Only a main dish gets sides suggested. */
private val MAIN_CATEGORIES = setOf("Mains", "Pasta")

/** A repair prompt carries at most this much of the broken reply and of the recipe text, so it fits the model's context. */
const val REPAIR_BROKEN_CHARS = 2_000
const val REPAIR_TEXT_CHARS = 3_000

/** A pass may take this long, thermal wait included. */
const val PASS_TIMEOUT_MS = 10 * 60_000L

private typealias JsonMap = Map<String, Any?>

/**
 * The four-pass on-device conversion of pasted text into a Recipe. Each pass is one short
 * [RecipeReader.generate] call; an invalid reply gets one repair retry, a second one fails the run.
 * Cancel by cancelling the calling coroutine.
 */
class RecipePipeline(
    private val reader: RecipeReader,
    private val passTimeoutMs: Long = PASS_TIMEOUT_MS,
) {

    /**
     * Converts [request] into a User Recipe with id [id]. [truncatedNote] is the localised "This recipe
     * is long" note, added when the text had to be cut. [onStage] is called as each pass starts.
     * Throws [PassTimeoutException] when the model stalls.
     */
    suspend fun run(
        request: ImportRequest,
        id: String,
        truncatedNote: String,
        onStage: (PipelineStage) -> Unit,
    ): PipelineResult {
        val trimmed = trimRecipeText(request.text)
        val kitchenRule = kitchenRuleFor(request.appliances, request.kitchenMode)
        val owned = if (request.kitchenMode == KitchenMode.Free) null else request.appliances
        val liberty = request.style == ImportStyle.Liberty
        val tokensLarge = if (liberty) TOKENS_LIBERTY else TOKENS_LARGE
        val ingredientRules = if (liberty) PASS1_LIBERTY else PASS1_FAITHFUL
        val stepRules = if (liberty) PASS2_LIBERTY else PASS2_FAITHFUL
        val german = if (request.lang == "de") GERMAN_RULE else ""
        var repairs = 0

        suspend fun pass(stage: PipelineStage, system: String, user: String, shape: String, tokens: Int, announce: Boolean = true, valid: (JsonMap) -> Boolean): JsonMap? {
            if (announce) onStage(stage)
            val first = ask(stage, system + german, user, tokens)
            parseModelJson(first)?.takeIf(valid)?.let { return it }
            repairs++
            val repairUser = "Broken output:\n${first.take(REPAIR_BROKEN_CHARS)}\n\nRecipe text:\n${trimmed.text.take(REPAIR_TEXT_CHARS)}"
            val second = ask(stage, REPAIR_SYSTEM.format(shape) + german, repairUser, tokens)
            return parseModelJson(second)?.takeIf(valid)
        }

        // Pass 1: ingredients and metadata.
        val ingredientsReply = pass(PipelineStage.Ingredients, PASS1_SYSTEM + ingredientRules, "Recipe text:\n${trimmed.text}", PASS1_SHAPE, tokensLarge) {
            it["ok"] == false || objectsIn(it["ingredients"]).isNotEmpty()
        } ?: return PipelineResult.Failed(PipelineStage.Ingredients)
        if (ingredientsReply["ok"] == false) return PipelineResult.Failed(PipelineStage.Ingredients)
        val ingredients = objectsIn(ingredientsReply["ingredients"]).mapIndexed { k, ingredient -> ingredient + ("id" to "i${k + 1}") }
        val ingredientObjects = normalize(mapOf("ingredients" to ingredients)).ingredients
        val ingredientsJson = ingredients.joinToString(",", "[", "]") { toJson(it) }

        // Pass 2: methods and steps.
        val splitReply = pass(
            PipelineStage.Splitting,
            PASS2_SYSTEM + stepRules + kitchenRule,
            "Recipe text:\n${trimmed.text}\n\nIngredients already read:\n$ingredientsJson",
            PASS2_SHAPE,
            tokensLarge,
        ) { reply ->
            val steps = stepsIn(reply)
            steps.isNotEmpty() && steps.all { it["ph"] == "prep" || it["ph"] == "cook" }
        } ?: return PipelineResult.Failed(PipelineStage.Splitting)
        var prepCount = 0
        var cookCount = 0
        val numbered = stepsIn(splitReply).map { step ->
            val stepId = if (step["ph"] == "prep") "p${++prepCount}" else "c${++cookCount}"
            step + ("id" to stepId)
        }
        val (methods, steps) = filterMethodsByKitchen(objectsIn(splitReply["methods"]), numbered, owned)

        // Pass 3: links are deterministic; the model only fills `needs` for cook steps.
        val linked = steps.map { step ->
            step + ("b" to link(step["b"].toString().replace(LINK_MARKUP, "$2"), ingredientObjects))
        }
        val prepIds = linked.filter { it["ph"] == "prep" }.map { it["id"] }.toSet()
        var needs: Map<String, List<String>> = emptyMap()
        if (prepIds.isNotEmpty() && linked.any { it["ph"] == "cook" }) {
            val needsReply = pass(
                PipelineStage.Linking,
                PASS3_SYSTEM,
                "Steps:\n${stepsForPrompt(linked)}",
                PASS3_SHAPE,
                TOKENS_SMALL,
            ) { it["needs"] is Map<*, *> } ?: return PipelineResult.Failed(PipelineStage.Linking)
            needs = (needsReply["needs"] as Map<*, *>).entries.associate { (stepId, ids) ->
                stepId.toString() to (ids as? List<*>).orEmpty().map { it.toString() }.filter { it in prepIds }
            }
        } else {
            onStage(PipelineStage.Linking)
        }
        val withNeeds = linked.map { step ->
            if (step["ph"] == "cook") step + ("needs" to (needs[step["id"]] ?: emptyList())) else step
        }

        // Pass 4: timers and notes.
        val timersReply = pass(
            PipelineStage.Timers,
            PASS4_SYSTEM,
            "Recipe text:\n${trimmed.text}\n\nSteps:\n${stepsForPrompt(withNeeds)}",
            PASS4_SHAPE,
            TOKENS_SMALL,
        ) { it["timers"] is Map<*, *> } ?: return PipelineResult.Failed(PipelineStage.Timers)
        val timers = (timersReply["timers"] as Map<*, *>).entries.mapNotNull { (stepId, seconds) ->
            (seconds as? Number)?.toDouble()?.roundToInt()?.takeIf { it > 0 }?.let { stepId.toString() to it }
        }.toMap()
        val withTimers = withNeeds.map { step -> step + ("d" to (timers[step["id"]] ?: 0)) }

        // Extra pass (Liberty, mains only): sides. Any failure leaves the recipe without sides, never Failed.
        val isMain = ingredientsReply["category"] in MAIN_CATEGORIES
        val sides: List<JsonMap> = if (liberty && isMain) {
            try {
                val sidesReply = pass(
                    PipelineStage.Timers,
                    SIDES_SYSTEM + kitchenRule,
                    "Recipe text:\n${trimmed.text}\n\nIngredients of the main dish:\n$ingredientsJson",
                    SIDES_SHAPE,
                    TOKENS_SIDES,
                    announce = false,
                ) { it["sides"] is List<*> }
                objectsIn(sidesReply?.get("sides")).map { side ->
                    val (sideMethods, sideSteps) = filterMethodsByKitchen(mapsIn(side["methods"]), mapsIn(side["steps"]), owned)
                    side + mapOf("methods" to sideMethods, "steps" to sideSteps)
                }
            } catch (_: PassTimeoutException) {
                emptyList()
            }
        } else {
            emptyList()
        }

        val notes = listOfNotNull(truncatedNote.takeIf { trimmed.truncated }) +
            strings(splitReply["notes"]) + strings(timersReply["notes"])
        val recipe = normalize(
            ingredientsReply + mapOf(
                "ingredients" to ingredients,
                "methods" to methods,
                "steps" to withTimers,
                "notes" to notes,
                "sides" to sides,
            ),
        )
        return PipelineResult.Done(recipe.copy(id = id, mine = true), repairs)
    }

    private suspend fun ask(stage: PipelineStage, system: String, user: String, maxTokens: Int): String {
        val out = StringBuilder()
        withTimeoutOrNull(passTimeoutMs) {
            reader.generate(system, user, maxTokens).collect { out.append(it) }
        } ?: throw PassTimeoutException(stage)
        return out.toString()
    }

    /** The objects of a JSON array that look like ingredients, methods or steps. */
    private fun objectsIn(value: Any?): List<JsonMap> =
        (value as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().filter { it["name"] != null || it["b"] != null || it["id"] != null }
            .map { m -> m.entries.associate { it.key.toString() to it.value } }

    /** Steps of a pass 2 reply that have a body. */
    private fun stepsIn(reply: JsonMap): List<JsonMap> =
        objectsIn(reply["steps"]).filter { (it["b"] as? String)?.isNotBlank() == true }

    private fun strings(value: Any?): List<String> = (value as? List<*>).orEmpty().filterIsInstance<String>()

    private fun stepsForPrompt(steps: List<JsonMap>): String =
        steps.joinToString("\n") { "${it["id"]} (${it["ph"]}) ${it["t"]}: ${it["b"]}" }

    private fun toJson(value: Any?): String = when (value) {
        null -> "null"
        is String -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
        is Map<*, *> -> value.entries.joinToString(",", "{", "}") { "${toJson(it.key.toString())}:${toJson(it.value)}" }
        is List<*> -> value.joinToString(",", "[", "]") { toJson(it) }
        else -> value.toString()
    }

    private companion object {
        val LINK_MARKUP = Regex("\\{(\\w+):([^}]+)\\}")

        const val GERMAN_RULE = "\nThe recipe may be German. Write every text value (title, summary, notes, step titles and bodies, option labels) in German; keep JSON keys, ids and category/tag/icon words in English."

        const val REPAIR_SYSTEM = "Return only valid JSON matching this shape: %s\nReply with one JSON object and nothing else. Fix the broken output you are given; do not add commentary."

        const val PASS1_SHAPE = """{"ok":true,"title":"","summary":"","category":"","tags":[],"time":0,"difficulty":"","base":0,"icon":"","ingredients":[{"name":"","q":0,"u":"","note":"","aisle":"","tips":[],"if":""}],"variations":[{"id":"v1","kind":"add","label":"","d":"","ing":"i1"}]}"""
        const val PASS2_SHAPE = """{"methods":[{"id":"m1","label":"","q":"","options":[{"v":"","l":"","icon":"flame","time":"","d":""}]}],"steps":[{"ph":"prep","t":"","b":"","if":""}],"notes":[]}"""
        const val SIDES_SHAPE = """{"sides":[{"id":"s1","label":"","d":"","ingredients":[{"id":"i1","name":"","q":0,"u":"","aisle":""}],"methods":[{"id":"m1","label":"","q":"","options":[{"v":"","l":"","icon":"flame","time":"","d":""}]}],"steps":[{"id":"p1","ph":"prep","t":"","b":"","d":0,"needs":[],"if":""}]}]}"""
        const val PASS3_SHAPE = """{"needs":{"c1":["p1"]}}"""
        const val PASS4_SHAPE = """{"timers":{"c1":900},"notes":[]}"""

        const val PASS1_SYSTEM = """You read pasted recipe text for the Simmer cooking app. Reply with one JSON object and nothing else.

If the text is not a recipe, reply {"ok":false,"reason":"<one short sentence>"}.

Otherwise reply with this shape: $PASS1_SHAPE
- "category" is one of ["Mains","Pasta","Breakfast","Sides","Soups","Salads","Desserts","Baking"]; "tags" is a subset of ["Vegetarian","Quick","Spicy","One-pot","Make-ahead"]; "difficulty" is "Easy", "Medium" or "Hard"; "icon" is one of ["flame","chef-hat","utensils","egg","carrot"].
- "time" is total minutes (integer). "base" is the servings the amounts are for (integer, guess 4 if missing). Summary: one calm sentence, 10-16 words, no hype or exclamation marks.
- Amounts: "q" is a number, "u" is "g", "ml", "tsp", "tbsp", "" for countable items, or a [singular, plural] pair like ["clove","cloves"]. Convert cups, ounces and pounds to g or ml.
- "aisle" is a short shop section: Produce, Meat, Fish, Dairy, Dairy & eggs, Bakery, Pantry, Spices, Tins & jars, World foods, Frozen, Drinks. Omit aisle for water.
- Tips only where genuinely useful; most ingredients have none.
- List the ingredients in order; ids are assigned for you. Optional ingredients become "variations" of kind "add" (id v1, v2, ...); give the ingredient "if": "add:<variation id>" and the variation "ing" the ingredient's id: i1 for the first ingredient, i2 for the second, and so on.
- Write in British English, sentence case, no emoji. Use °C."""

        const val PASS2_SYSTEM = """You split a recipe's method into steps for the Simmer cooking app. Reply with one JSON object and nothing else.

Reply with this shape: $PASS2_SHAPE
- Only add "methods" when the text offers alternative ways to cook (oven or air fryer, hob or slow cooker); otherwise use []. Each option needs its own steps, marked "if": "<method id>=<option v>". The first option is the default. Omit "if" on other steps.
- Split the method into preparation steps ("ph":"prep": chopping, mixing, marinating, anything without heat) and cooking steps ("ph":"cook"). Preparation comes first.
- Each step: a short imperative title "t" and a body "b" of 1-3 sentences in plain text, with no markup or ids. Keep the author's quantities and temperatures; use °C.
- "notes" lists anything you changed or could not read."""

        /** Both styles: ingredient tips. Liberty also proposes extra ingredients as "suggest" variations. */
        const val PASS1_FAITHFUL = "\n- Put a useful tip (a substitute, how to choose or prepare it) in an ingredient's \"tips\" when you know one."
        const val PASS1_LIBERTY = PASS1_FAITHFUL +
            "\n- Also suggest 3 to 5 extra ingredients that would improve the dish, as variations of kind \"suggest\" (ids after the optional ones, e.g. v3). Each is its own extra ingredient entry at the end of the list with \"if\": \"add:<variation id>\"; the variation's \"d\" says what it adds to the dish in a few words, like \"smoky depth\". Never change the amounts or main ingredients."

        /** Both styles: expand vague steps. Liberty may add methods and restructure. */
        const val PASS2_FAITHFUL = "\n- Expand vague steps with heat levels, doneness cues, timings and technique. Write other tips into the step body. Keep any methods the text lists. Adapt steps to the kitchen rule below, also for tips. In \"notes\" add one short line per kind of addition (tips, methods, restructuring)."
        const val PASS2_LIBERTY = PASS2_FAITHFUL +
            "\n- Also offer \"methods\" (oven, air fryer, pan, ...) whenever an appliance the cook owns fits the recipe, even if the text does not list it; never an appliance the cook lacks. You may reorder, merge or split steps. Keep the amounts and main ingredients."

        const val PASS3_SYSTEM = """You link steps for the Simmer cooking app. Reply with one JSON object and nothing else.

For each cook step, list the prep step ids it depends on (a cook step needs a prep step when it uses something made in it). Reply with this shape: $PASS3_SHAPE
Use [] when a cook step needs none."""

        const val SIDES_SYSTEM = """You suggest sides for a main dish in the Simmer cooking app. Reply with one JSON object and nothing else.

Reply with this shape: $SIDES_SHAPE
- Suggest at most 3 sides that go well with the main dish, each a small recipe of its own: a short "label", a few words in "d" on why it goes with the dish, about 6 ingredients at most, 1-2 prep steps and 1-2 cook steps.
- Ids are local to the side (i1, p1, c1, m1). A cook step "needs" the prep step ids it depends on. Link ingredients in step bodies as {i1:rice}. "d" on a step is seconds of waiting, 0 if none.
- Only add "methods" (at least 2 options) when an appliance the cook owns gives a real alternative; each option needs its own steps, marked "if": "<method id>=<option v>". Otherwise use []. Never use an appliance the cook lacks.
- Do not repeat ingredients of the main dish as the side. Use no "add:" conditions.
- Write in British English, sentence case, no emoji. Use °C."""

        const val PASS4_SYSTEM = """You set timers for the Simmer cooking app. Reply with one JSON object and nothing else.

Reply with this shape: $PASS4_SHAPE
- "timers" maps a step id to seconds of waiting or heat that the step needs (roasting, simmering, resting, chilling). Steps with no meaningful wait, such as chopping vegetables, get no entry.
- Use the time in the text. If it is a range, use the middle. Estimate a missing time for a step that needs one.
- "notes" lists each time you estimated or converted, in one short sentence each."""
    }
}
