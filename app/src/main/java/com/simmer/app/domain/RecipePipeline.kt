package com.simmer.app.domain

import com.simmer.app.domain.model.Recipe

/** The Pipeline's four passes, in order. They are the stages the Working screen lists. */
enum class PipelineStage { Ingredients, Phases, Linking, Timers }

sealed interface PipelineResult {
    /** [repairs] counts the repair retries used; the fixture target is 0 for 90% of recipes. */
    data class Done(val recipe: Recipe, val repairs: Int) : PipelineResult

    /** The pass at [stage] gave no usable JSON even after the repair retry, or the text is not a recipe. */
    data class Failed(val stage: PipelineStage) : PipelineResult
}

/** Added to the review notes when the pasted text was cut. */
const val TRUNCATED_NOTE = "This recipe is long. Only the first part was read."

/** Each pass asks for this many tokens at most (between 1,200 and 2,000). */
private const val TOKENS_LARGE = 2_000
private const val TOKENS_SMALL = 1_200

private typealias Json = Map<String, Any?>

/**
 * The four-pass on-device conversion of pasted text into a Recipe. Each pass is one short
 * [RecipeReader.generate] call; an invalid reply gets one repair retry, a second one fails the run.
 * Cancel by cancelling the calling coroutine.
 */
class RecipePipeline(private val reader: RecipeReader) {

    /**
     * Converts [text] into a User Recipe with id [id]. [kitchenRule] is the `kitchenRule(...)` clause,
     * [lang] the app language. [onStage] is called as each pass starts.
     */
    suspend fun run(
        text: String,
        kitchenRule: String,
        lang: String,
        id: String,
        onStage: (PipelineStage) -> Unit,
    ): PipelineResult {
        val trimmed = trimRecipeText(text)
        val german = if (lang == "de") GERMAN_RULE else ""
        var repairs = 0
        suspend fun pass(stage: PipelineStage, system: String, user: String, shape: String, tokens: Int, valid: (Json) -> Boolean): Json? {
            onStage(stage)
            val first = ask(system + german, user, tokens)
            parseModelJson(first)?.takeIf(valid)?.let { return it }
            repairs++
            val second = ask(REPAIR_SYSTEM.format(shape) + german, "Broken output:\n$first\n\nRecipe text:\n${trimmed.text}", tokens)
            return parseModelJson(second)?.takeIf(valid)
        }

        // Pass 1: ingredients and metadata.
        val p1 = pass(PipelineStage.Ingredients, PASS1_SYSTEM, "Recipe text:\n${trimmed.text}", PASS1_SHAPE, TOKENS_LARGE) {
            it["ok"] == false || firstMaps(it["ingredients"]).isNotEmpty()
        } ?: return PipelineResult.Failed(PipelineStage.Ingredients)
        if (p1["ok"] == false) return PipelineResult.Failed(PipelineStage.Ingredients)
        val ingredients = firstMaps(p1["ingredients"]).mapIndexed { k, i -> i + ("id" to "i${k + 1}") }
        val ingredientObjects = normalize(mapOf("ingredients" to ingredients)).ingredients
        val ingredientsJson = ingredients.joinToString(",", "[", "]") { toJson(it) }

        // Pass 2: methods and steps.
        val p2 = pass(
            PipelineStage.Phases,
            PASS2_SYSTEM + kitchenRule,
            "Recipe text:\n${trimmed.text}\n\nIngredients already read:\n$ingredientsJson",
            PASS2_SHAPE,
            TOKENS_LARGE,
        ) { stepMaps(it).isNotEmpty() } ?: return PipelineResult.Failed(PipelineStage.Phases)
        var prepCount = 0
        var cookCount = 0
        val steps = stepMaps(p2).map { s ->
            val prep = s["ph"] == "prep"
            val stepId = if (prep) "p${++prepCount}" else "c${++cookCount}"
            s + mapOf("id" to stepId, "ph" to if (prep) "prep" else "cook")
        }

        // Pass 3: links are deterministic; the model only fills `needs` for cook steps.
        val linked = steps.map { s ->
            s + ("b" to link(s["b"].toString().replace(LINK_MARKUP, "$2"), ingredientObjects))
        }
        var needs: Map<String, List<String>> = emptyMap()
        if (prepCount > 0 && cookCount > 0) {
            val p3 = pass(
                PipelineStage.Linking,
                PASS3_SYSTEM,
                "Steps:\n${stepsForPrompt(linked)}",
                PASS3_SHAPE,
                TOKENS_SMALL,
            ) { it["needs"] is Map<*, *> } ?: return PipelineResult.Failed(PipelineStage.Linking)
            val prepIds = linked.filter { it["ph"] == "prep" }.map { it["id"] }.toSet()
            needs = (p3["needs"] as Map<*, *>).entries.associate { (k, v) ->
                k.toString() to (v as? List<*>).orEmpty().map { it.toString() }.filter { it in prepIds }
            }
        } else {
            onStage(PipelineStage.Linking)
        }
        val withNeeds = linked.map { s ->
            if (s["ph"] == "cook") s + ("needs" to (needs[s["id"]] ?: emptyList())) else s
        }

        // Pass 4: timers and notes.
        val p4 = pass(
            PipelineStage.Timers,
            PASS4_SYSTEM,
            "Recipe text:\n${trimmed.text}\n\nSteps:\n${stepsForPrompt(withNeeds)}",
            PASS4_SHAPE,
            TOKENS_SMALL,
        ) { it["timers"] is Map<*, *> } ?: return PipelineResult.Failed(PipelineStage.Timers)
        val timers = (p4["timers"] as Map<*, *>).entries.mapNotNull { (k, v) ->
            (v as? Number)?.toInt()?.takeIf { it > 0 }?.let { k.toString() to it }
        }.toMap()
        val withTimers = withNeeds.map { s -> s + ("d" to (timers[s["id"]] ?: 0)) }

        val notes = listOfNotNull(TRUNCATED_NOTE.takeIf { trimmed.truncated }) +
            strings(p2["notes"]) + strings(p4["notes"])
        val recipe = normalize(
            p1 + mapOf(
                "ingredients" to ingredients,
                "methods" to firstMaps(p2["methods"]),
                "steps" to withTimers,
                "notes" to notes,
            ),
        )
        return PipelineResult.Done(recipe.copy(id = id, mine = true), repairs)
    }

    private suspend fun ask(system: String, user: String, maxTokens: Int): String {
        val out = StringBuilder()
        reader.generate(system, user, maxTokens).collect { out.append(it) }
        return out.toString()
    }

    private fun firstMaps(v: Any?): List<Map<String, Any?>> =
        (v as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().filter { it["name"] != null || it["b"] != null || it["id"] != null }
            .map { m -> m.entries.associate { it.key.toString() to it.value } }

    private fun stepMaps(j: Json): List<Map<String, Any?>> =
        firstMaps(j["steps"]).filter { (it["b"] as? String)?.isNotBlank() == true }

    private fun strings(v: Any?): List<String> = (v as? List<*>).orEmpty().filterIsInstance<String>()

    private fun stepsForPrompt(steps: List<Map<String, Any?>>): String =
        steps.joinToString("\n") { "${it["id"]} (${it["ph"]}) ${it["t"]}: ${it["b"]}" }

    private fun toJson(v: Any?): String = when (v) {
        null -> "null"
        is String -> "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
        is Map<*, *> -> v.entries.joinToString(",", "{", "}") { "${toJson(it.key.toString())}:${toJson(it.value)}" }
        is List<*> -> v.joinToString(",", "[", "]") { toJson(it) }
        else -> v.toString()
    }

    private companion object {
        val LINK_MARKUP = Regex("\\{(\\w+):([^}]+)\\}")

        const val GERMAN_RULE = "\nThe recipe may be German. Write every text value (title, summary, notes, step titles and bodies, option labels) in German; keep JSON keys, ids and category/tag/icon words in English."

        const val REPAIR_SYSTEM = "Return only valid JSON matching this shape: %s\nReply with one JSON object and nothing else. Fix the broken output you are given; do not add commentary."

        const val PASS1_SHAPE = """{"ok":true,"title":"","summary":"","category":"","tags":[],"time":0,"difficulty":"","base":0,"icon":"","ingredients":[{"name":"","q":0,"u":"","note":"","aisle":"","tips":[],"if":""}],"variations":[{"id":"v1","kind":"add","label":"","d":"","ing":"i1"}]}"""
        const val PASS2_SHAPE = """{"methods":[{"id":"m1","label":"","q":"","options":[{"v":"","l":"","icon":"flame","time":"","d":""}]}],"steps":[{"ph":"prep","t":"","b":"","if":""}],"notes":[]}"""
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

        const val PASS3_SYSTEM = """You link steps for the Simmer cooking app. Reply with one JSON object and nothing else.

For each cook step, list the prep step ids it depends on (a cook step needs a prep step when it uses something made in it). Reply with this shape: $PASS3_SHAPE
Use [] when a cook step needs none."""

        const val PASS4_SYSTEM = """You set timers for the Simmer cooking app. Reply with one JSON object and nothing else.

Reply with this shape: $PASS4_SHAPE
- "timers" maps a step id to seconds of waiting or heat that the step needs (roasting, simmering, resting, chilling). Steps with no meaningful wait, such as chopping vegetables, get no entry.
- Use the time in the text. If it is a range, use the middle. Estimate a missing time for a step that needs one.
- "notes" lists each time you estimated or converted, in one short sentence each."""
    }
}
