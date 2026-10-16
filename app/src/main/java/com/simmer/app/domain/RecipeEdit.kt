package com.simmer.app.domain

import com.simmer.app.data.RecipeJson
import com.simmer.app.data.toDomain
import com.simmer.app.data.toEntity
import com.simmer.app.domain.model.Ingredient
import com.simmer.app.domain.model.Recipe
import com.simmer.app.domain.model.Step
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

/** Categories the Edit recipe screen offers (stored in English). */
val RECIPE_CATEGORIES = listOf("Mains", "Pasta", "Breakfast", "Sides", "Soups", "Salads", "Desserts", "Baking")

val RECIPE_DIFFICULTIES = listOf("Easy", "Medium", "Hard")

/** The two [Step] phases, as stored. */
object StepPhase {
    const val PREP = "prep"
    const val COOK = "cook"
    val all = listOf(PREP, COOK)
}

/** Phrases that put a Condition into words; each takes the option or add-in label as `%s`. */
data class ConditionWords(val not: String, val added: String, val notAdded: String)

/** One ingredient row of the Edit recipe form. Amount and unit are the text the user types. */
@Serializable
data class IngredientDraft(
    val key: String,
    val id: String,
    val name: String,
    val qty: String,
    val unit: String,
    val note: String,
    val aisle: String,
    /** What the form cannot edit, carried through to the saved ingredient. */
    val origQty: Double = 0.0,
    /** Name when the form opened; a step's links are redone when it changes. */
    val origName: String = "",
    val origUnit: String = "",
    val uPlural: String = "",
    val tips: List<String> = emptyList(),
    val ifCondition: String? = null,
    val alt: List<Pair<String, Double>> = emptyList(),
)

/** One step row of the Edit recipe form. [text] is plain, without `{id:text}` links. */
@Serializable
data class StepDraft(
    val key: String,
    val id: String,
    val phase: String,
    val title: String,
    val text: String,
    /** Timer in decimal minutes, as typed. */
    val minutes: String,
    val origSecs: Int = 0,
    /** Markup the step had when the form opened; kept when [text] is not changed. */
    val originalBody: String = "",
    val needs: List<String> = emptyList(),
    val ifCondition: String? = null,
)

/**
 * One existing side of the form: its name and its ingredient and step rows, which are the same
 * rows as the main's. Its methods and why-it-fits line are not editable and come from [RecipeDraft.base].
 */
@Serializable
data class SideDraft(
    val id: String,
    val label: String,
    val ingredients: List<IngredientDraft> = emptyList(),
    val steps: List<StepDraft> = emptyList(),
)

/**
 * The Edit recipe form. Built from a [Recipe], edited through the functions below,
 * and turned back into a [Recipe] by [toRecipe]. Serializable so it survives process death.
 */
@Serializable
data class RecipeDraft(
    val base: RecipeJson,
    val mine: Boolean,
    val edited: Boolean,
    val title: String,
    val summary: String,
    val servings: String,
    val time: String,
    val difficulty: String,
    val category: String,
    val ingredients: List<IngredientDraft>,
    val steps: List<StepDraft>,
    val nextKey: Int,
    /** Existing sides. Row keys are unique across the main and all sides. */
    val sides: List<SideDraft> = emptyList(),
    /** False for the Add recipe draft, which is not in storage yet. */
    val stored: Boolean = true,
) {
    /** A built-in recipe with a local override can be restored. */
    val canRestore: Boolean get() = stored && !mine && edited

    /** User Recipes can be deleted; built-ins and drafts cannot. */
    val canDelete: Boolean get() = stored && mine

    /**
     * Save needs a title, a named ingredient and a step with text, and every row [toRecipe] keeps
     * must have a readable amount or timer. Rows [toRecipe] drops (nameless ingredients, textless
     * steps) are ignored here too, so Save never depends on a row that is not saved.
     */
    val canSave: Boolean
        get() = title.isNotBlank() && keptIngredients().isNotEmpty() && keptSteps().isNotEmpty() &&
            keptIngredients().all { it.qtyValid } && keptSteps().all { it.minutesValid } &&
            sides.all { s -> s.keptIngredients().all { it.qtyValid } && s.keptSteps().all { it.minutesValid } }

    private fun keptIngredients() = ingredients.filter { it.name.isNotBlank() }

    private fun keptSteps() = steps.filter { it.text.isNotBlank() }

    private fun SideDraft.keptIngredients() = ingredients.filter { it.name.isNotBlank() }

    private fun SideDraft.keptSteps() = steps.filter { it.text.isNotBlank() }

    /** Applies [change] to the ingredient rows holding [key]: the main's or one side's. */
    private fun editIngredients(key: String, change: (List<IngredientDraft>) -> List<IngredientDraft>): RecipeDraft =
        if (ingredients.any { it.key == key }) copy(ingredients = change(ingredients))
        else copy(sides = sides.map { s -> if (s.ingredients.any { it.key == key }) s.copy(ingredients = change(s.ingredients)) else s })

    /** Applies [change] to the step rows holding [key]: the main's or one side's. */
    private fun editSteps(key: String, change: (List<StepDraft>) -> List<StepDraft>): RecipeDraft =
        if (steps.any { it.key == key }) copy(steps = change(steps))
        else copy(sides = sides.map { s -> if (s.steps.any { it.key == key }) s.copy(steps = change(s.steps)) else s })

    private fun stepsHolding(key: String): List<StepDraft> =
        if (steps.any { it.key == key }) steps else sides.firstOrNull { s -> s.steps.any { it.key == key } }?.steps ?: emptyList()

    fun renameSide(id: String, label: String) = copy(sides = sides.map { if (it.id == id) it.copy(label = label) else it })

    fun removeSide(id: String) = copy(sides = sides.filter { it.id != id })

    fun stepsIn(phase: String): List<StepDraft> = steps.filter { it.phase == phase }

    fun updateIngredient(key: String, change: (IngredientDraft) -> IngredientDraft) =
        editIngredients(key) { rows -> rows.map { if (it.key == key) change(it) else it } }

    fun removeIngredient(key: String) = editIngredients(key) { rows -> rows.filter { it.key != key } }

    /** Appends a blank ingredient; it is the last row. */
    fun addIngredient(): RecipeDraft {
        val n = nextKey
        val id = freshId("e$n", ingredients.map { it.id })
        val row = IngredientDraft(key = "k$n", id = id, name = "", qty = "1", unit = "", note = "", aisle = "", origQty = 1.0)
        return copy(ingredients = ingredients + row, nextKey = n + 1)
    }

    fun updateStep(key: String, change: (StepDraft) -> StepDraft) =
        editSteps(key) { rows -> rows.map { if (it.key == key) change(it) else it } }

    fun removeStep(key: String) = editSteps(key) { rows -> rows.filter { it.key != key } }

    /** Appends a blank step to [phase] (`prep` or `cook`); it is the last row. */
    fun addStep(phase: String): RecipeDraft {
        val n = nextKey
        val id = freshId("s-e$n", steps.map { it.id })
        val row = StepDraft(key = "k$n", id = id, phase = phase, title = "", text = "", minutes = "", origSecs = 0)
        return copy(steps = steps + row, nextKey = n + 1)
    }

    /** Moves the step to [phase], at the end of that group. */
    fun setPhase(key: String, phase: String): RecipeDraft {
        val step = stepsHolding(key).firstOrNull { it.key == key } ?: return this
        if (step.phase == phase) return this
        return editSteps(key) { rows -> rows.filter { it.key != key } + step.copy(phase = phase) }
    }

    private fun neighbour(key: String, dir: Int): StepDraft? {
        val rows = stepsHolding(key)
        val me = rows.firstOrNull { it.key == key } ?: return null
        val group = rows.filter { it.phase == me.phase }
        return group.getOrNull(group.indexOfFirst { it.key == key } + dir)
    }

    /** Whether the step can move up (-1) or down (1) within its phase. */
    fun canMove(key: String, dir: Int): Boolean = neighbour(key, dir) != null

    /** Swaps the step with its neighbour in the same phase; steps of the other phase stay put. */
    fun moveStep(key: String, dir: Int): RecipeDraft {
        val other = neighbour(key, dir) ?: return this
        return editSteps(key) { rows ->
            rows.map {
                when (it.key) {
                    key -> other
                    other.key -> rows.first { s -> s.key == key }
                    else -> it
                }
            }
        }
    }

    /** The ingredients a step can link to: its side's, or the main's. */
    private fun linkable(stepKey: String): List<Ingredient> {
        val rows = sides.firstOrNull { s -> s.steps.any { it.key == stepKey } }?.keptIngredients() ?: keptIngredients()
        return rows.map { Ingredient(it.id, it.name.trim(), 0.0, "") }
    }

    /** Names of the ingredients found in the step's text, in reading order. */
    fun linkedNames(stepKey: String): List<String> {
        val step = stepsHolding(stepKey).firstOrNull { it.key == stepKey } ?: return emptyList()
        val byId = linkable(stepKey).associateBy { it.id }
        return linkMarks(step.text, byId.values.toList()).map { it.id }.distinct().mapNotNull { byId[it]?.name }
    }

    /** Puts an ingredient or step Condition into words, e.g. "Plant-based" or "Dark beer added". */
    fun conditionLabel(condition: String, words: ConditionWords, sideId: String? = null): String {
        val recipe = base.toDomain()
        if (sideId != null) {
            val side = recipe.sides.firstOrNull { it.id == sideId }
            val negated = condition.contains("!=")
            val (key, value) = condition.split(if (negated) "!=" else "=", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            val label = side?.methods?.firstOrNull { it.id == key }?.options?.firstOrNull { it.value == value }?.label ?: value
            return if (negated) String.format(words.not, label) else label
        }
        if (condition.startsWith("!add:") || condition.startsWith("add:")) {
            val negated = condition.startsWith("!")
            val id = condition.substringAfter("add:")
            val label = recipe.variations.firstOrNull { it.id == id }?.label ?: id
            return String.format(if (negated) words.notAdded else words.added, label)
        }
        val negated = condition.contains("!=")
        val (key, value) = condition.split(if (negated) "!=" else "=", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        val options = recipe.methods.firstOrNull { it.id == key }?.options?.map { it.value to it.label }
            ?: recipe.variations.firstOrNull { it.id == key }?.options?.map { it.value to it.label }
            ?: emptyList()
        val label = options.firstOrNull { it.first == value }?.second ?: value
        return if (negated) String.format(words.not, label) else label
    }

    /**
     * The saved recipe. Nameless ingredients and textless steps are dropped (their rows, even if
     * invalid, do not matter). Step text left as it was keeps its markup unless a linked ingredient
     * was deleted or renamed; otherwise it is linked again with [link]. Add-in variations whose
     * ingredient is gone are dropped.
     */
    fun toRecipe(): Recipe {
        val original = base.toDomain(mine = mine, edited = edited)
        val (ingredientsOut, stepsOut) = buildRows(keptIngredients(), keptSteps())
        val sidesOut = sides.mapNotNull { s ->
            val (ings, sideSteps) = buildRows(s.keptIngredients(), s.keptSteps())
            val orig = original.sides.firstOrNull { it.id == s.id } ?: return@mapNotNull null
            if (s.label.isBlank() || ings.isEmpty() || sideSteps.isEmpty()) null
            else orig.copy(label = s.label.trim(), ingredients = ings, steps = sideSteps)
        }
        return original.copy(
            title = title.trim(),
            summary = summary.trim(),
            base = servings.trim().toIntOrNull()?.coerceIn(1, MAX_SERVINGS) ?: original.base,
            time = time.trim().toIntOrNull()?.coerceAtLeast(1) ?: original.time,
            difficulty = difficulty,
            category = category,
            ingredients = ingredientsOut,
            steps = stepsOut,
            variations = original.variations.filter { v -> v.kind == "swap" || ingredientsOut.any { it.id == v.ing } },
            sides = sidesOut,
        )
    }

    /** Ingredients and steps for kept rows of one scope (the main or a side); links are redone against that scope only. */
    private fun buildRows(kept: List<IngredientDraft>, stepRows: List<StepDraft>): Pair<List<Ingredient>, List<Step>> {
        val ingredientsOut = kept.map { it.toIngredient() }
        val stepIds = stepRows.map { it.id }.toSet()
        val origNames = kept.associate { r -> r.id to r.origName.trim() }
        return ingredientsOut to stepRows.map { it.toStep(ingredientsOut, origNames, stepIds) }
    }

    companion object {
        fun from(recipe: Recipe): RecipeDraft {
            val ingredients = recipe.ingredients.mapIndexed { i, ing -> ingredientDraft(ing, "k${i}i") }
            val steps = recipe.steps.mapIndexed { i, s -> stepDraft(s, "k${i}s") }
            var n = ingredients.size + steps.size
            val sideDrafts = recipe.sides.map { side ->
                SideDraft(
                    id = side.id, label = side.label,
                    ingredients = side.ingredients.map { ing -> ingredientDraft(ing, "k${n++}i") },
                    steps = side.steps.map { s -> stepDraft(s, "k${n++}s") },
                )
            }
            return RecipeDraft(
                base = Json.decodeFromString(RecipeJson.serializer(), recipe.toEntity().json),
                mine = recipe.mine,
                edited = recipe.edited,
                title = recipe.title,
                summary = recipe.summary,
                servings = recipe.base.toString(),
                time = recipe.time.toString(),
                difficulty = recipe.difficulty,
                category = recipe.category,
                ingredients = ingredients,
                steps = steps,
                nextKey = n,
                sides = sideDrafts,
            )
        }
    }
}

private fun ingredientDraft(ing: Ingredient, key: String) = IngredientDraft(
    key = key, id = ing.id, name = ing.name, qty = decimalText(ing.quantity), unit = ing.unit,
    note = ing.note, aisle = ing.aisle, origQty = ing.quantity, origName = ing.name, origUnit = ing.unit, uPlural = ing.uPlural,
    tips = ing.tips, ifCondition = ing.ifCondition, alt = ing.alt,
)

private fun stepDraft(s: Step, key: String) = StepDraft(
    key = key, id = s.id, phase = s.phase, title = s.title, text = plainText(s.body),
    minutes = minutesText(s.durationSecs), origSecs = s.durationSecs,
    originalBody = s.body, needs = s.needs, ifCondition = s.ifCondition,
)

/** The amount is as it was, or reads as a number above zero. */
val IngredientDraft.qtyValid: Boolean get() = qty.trim() == decimalText(origQty) || parseQty(qty) > 0

/** The timer is empty (no timer), as it was, or reads as a number. */
val StepDraft.minutesValid: Boolean
    get() = minutes.isBlank() || minutes.trim() == minutesText(origSecs) || parseQty(minutes) > 0 ||
        minutes.trim().replace(',', '.').toDoubleOrNull() == 0.0

/** Timer field text for [secs]; empty when the Step has no timer. */
private fun minutesText(secs: Int): String = if (secs <= 0) "" else decimalText(Math.round(secs / 6.0) / 10.0)

private fun IngredientDraft.toIngredient(): Ingredient {
    val u = unit.trim()
    val quantity = if (qty.trim() == decimalText(origQty)) origQty else parseQty(qty).takeIf { it > 0 } ?: origQty
    return Ingredient(
        id = id, name = name.trim(), quantity = quantity, unit = u,
        uPlural = if (u == origUnit) uPlural else "",
        note = note.trim(), aisle = aisle.trim(), tips = tips, ifCondition = ifCondition, alt = alt,
    )
}

private fun StepDraft.toStep(ingredients: List<Ingredient>, origNames: Map<String, String>, stepIds: Set<String>): Step {
    val minutesValue = parseQty(minutes)
    val secs = when {
        minutes.trim() == minutesText(origSecs) -> origSecs
        minutesValue > 0 -> Math.round(minutesValue * 60).toInt().coerceAtLeast(1)
        else -> 0
    }
    val unchanged = text == plainText(originalBody) && originalBody.linkedIds().all { id ->
        val kept = ingredients.firstOrNull { it.id == id }
        kept != null && kept.name == origNames[id]
    }
    val body = if (unchanged) originalBody else link(text.trim(), ingredients)
    return Step(
        id = id, phase = phase,
        title = title.trim().ifEmpty { text.trim().split(Regex("[.!]"))[0].take(40).trim() },
        durationSecs = secs, body = body, needs = needs.filter { it in stepIds }, ifCondition = ifCondition,
    )
}

private val LINK = Regex("\\{([^:{}]+):([^}]+)\\}")

private fun String.linkedIds(): List<String> = LINK.findAll(this).map { it.groupValues[1] }.toList()

/** Step text with its `{id:text}` links replaced by the shown text. */
fun plainText(body: String): String = LINK.replace(body, "$2")

/** Number as typed in the form: no trailing zeros, at most three decimals. */
fun decimalText(value: Double): String =
    String.format(Locale.ROOT, "%.3f", value).trimEnd('0').trimEnd('.')

private fun freshId(candidate: String, taken: List<String>): String {
    var id = candidate
    while (id in taken) id += "x"
    return id
}
