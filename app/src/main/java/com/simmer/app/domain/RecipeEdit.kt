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

private const val MIN_STEP_SECS = 30

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
    /** False for the Add recipe draft, which is not in storage yet. */
    val stored: Boolean = true,
) {
    /** A built-in recipe with a local override can be restored. */
    val canRestore: Boolean get() = stored && !mine && edited

    /** User Recipes can be deleted; built-ins and drafts cannot. */
    val canDelete: Boolean get() = stored && mine

    /** Save needs a title, a named ingredient and a step with text. */
    val canSave: Boolean
        get() = title.isNotBlank() && ingredients.any { it.name.isNotBlank() } && steps.any { it.text.isNotBlank() }

    fun stepsIn(phase: String): List<StepDraft> = steps.filter { it.phase == phase }

    fun updateIngredient(key: String, change: (IngredientDraft) -> IngredientDraft) =
        copy(ingredients = ingredients.map { if (it.key == key) change(it) else it })

    fun removeIngredient(key: String) = copy(ingredients = ingredients.filter { it.key != key })

    /** Appends a blank ingredient; it is the last row. */
    fun addIngredient(): RecipeDraft {
        val n = nextKey
        val id = freshId("e$n", ingredients.map { it.id })
        val row = IngredientDraft(key = "k$n", id = id, name = "", qty = "1", unit = "", note = "", aisle = "", origQty = 1.0)
        return copy(ingredients = ingredients + row, nextKey = n + 1)
    }

    fun updateStep(key: String, change: (StepDraft) -> StepDraft) =
        copy(steps = steps.map { if (it.key == key) change(it) else it })

    fun removeStep(key: String) = copy(steps = steps.filter { it.key != key })

    /** Appends a blank step to [phase] (`prep` or `cook`); it is the last row. */
    fun addStep(phase: String): RecipeDraft {
        val n = nextKey
        val id = freshId("s-e$n", steps.map { it.id })
        val row = StepDraft(key = "k$n", id = id, phase = phase, title = "", text = "", minutes = "5", origSecs = 300)
        return copy(steps = steps + row, nextKey = n + 1)
    }

    fun setPhase(key: String, phase: String) = updateStep(key) { it.copy(phase = phase) }

    private fun neighbour(key: String, dir: Int): StepDraft? {
        val me = steps.firstOrNull { it.key == key } ?: return null
        val group = stepsIn(me.phase)
        return group.getOrNull(group.indexOfFirst { it.key == key } + dir)
    }

    /** Whether the step can move up (-1) or down (1) within its phase. */
    fun canMove(key: String, dir: Int): Boolean = neighbour(key, dir) != null

    /** Swaps the step with its neighbour in the same phase; steps of the other phase stay put. */
    fun moveStep(key: String, dir: Int): RecipeDraft {
        val other = neighbour(key, dir) ?: return this
        return copy(
            steps = steps.map {
                when (it.key) {
                    key -> other
                    other.key -> steps.first { s -> s.key == key }
                    else -> it
                }
            },
        )
    }

    private fun named(): List<IngredientDraft> = ingredients.filter { it.name.isNotBlank() }

    private fun linkable(): List<Ingredient> = named().map { Ingredient(it.id, it.name.trim(), 0.0, "") }

    /** Names of the ingredients found in the step's text, in reading order. */
    fun linkedNames(stepKey: String): List<String> {
        val step = steps.firstOrNull { it.key == stepKey } ?: return emptyList()
        val byId = linkable().associateBy { it.id }
        return linkMarks(step.text, byId.values.toList()).map { it.id }.distinct().mapNotNull { byId[it]?.name }
    }

    /** Puts an ingredient or step Condition into words, e.g. "Plant-based" or "Dark beer added". */
    fun conditionLabel(condition: String, words: ConditionWords): String {
        val recipe = base.toDomain()
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
     * The saved recipe. Nameless ingredients and textless steps are dropped. Step text left as it was
     * keeps its markup; other text is linked again with [link]. Add-in variations whose ingredient is gone are dropped.
     */
    fun toRecipe(): Recipe {
        val kept = named()
        val ingredientsOut = kept.map { it.toIngredient() }
        val stepRows = steps.filter { it.text.isNotBlank() }
        val stepIds = stepRows.map { it.id }.toSet()
        val original = base.toDomain(mine = mine, edited = edited)
        return original.copy(
            title = title.trim(),
            summary = summary.trim(),
            base = servings.trim().toIntOrNull()?.coerceIn(1, MAX_SERVINGS) ?: original.base,
            time = time.trim().toIntOrNull()?.coerceAtLeast(1) ?: original.time,
            difficulty = difficulty,
            category = category,
            ingredients = ingredientsOut,
            steps = stepRows.map { it.toStep(ingredientsOut, stepIds) },
            variations = original.variations.filter { v -> v.kind != "add" || ingredientsOut.any { it.id == v.ing } },
        )
    }

    companion object {
        fun from(recipe: Recipe): RecipeDraft {
            val ingredients = recipe.ingredients.mapIndexed { i, ing ->
                IngredientDraft(
                    key = "k${i}i", id = ing.id, name = ing.name, qty = decimalText(ing.quantity), unit = ing.unit,
                    note = ing.note, aisle = ing.aisle, origQty = ing.quantity, origUnit = ing.unit, uPlural = ing.uPlural,
                    tips = ing.tips, ifCondition = ing.ifCondition, alt = ing.alt,
                )
            }
            val steps = recipe.steps.mapIndexed { i, s ->
                StepDraft(
                    key = "k${i}s", id = s.id, phase = s.phase, title = s.title, text = plainText(s.body),
                    minutes = decimalText(Math.round(s.durationSecs / 6.0) / 10.0), origSecs = s.durationSecs,
                    originalBody = s.body, needs = s.needs, ifCondition = s.ifCondition,
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
                nextKey = ingredients.size + steps.size,
            )
        }
    }
}

private fun IngredientDraft.toIngredient(): Ingredient {
    val u = unit.trim()
    val quantity = if (qty.trim() == decimalText(origQty)) origQty else parseQty(qty).takeIf { it > 0 } ?: origQty
    return Ingredient(
        id = id, name = name.trim(), quantity = quantity, unit = u,
        uPlural = if (u == origUnit) uPlural else "",
        note = note.trim(), aisle = aisle.trim(), tips = tips, ifCondition = ifCondition, alt = alt,
    )
}

private fun StepDraft.toStep(ingredients: List<Ingredient>, stepIds: Set<String>): Step {
    val minutesValue = parseQty(minutes)
    val secs = when {
        minutes.trim() == decimalText(Math.round(origSecs / 6.0) / 10.0) -> origSecs
        minutesValue > 0 -> Math.round(minutesValue * 60).toInt().coerceAtLeast(MIN_STEP_SECS)
        else -> origSecs
    }
    val body = if (text == plainText(originalBody)) originalBody else link(text.trim(), ingredients)
    return Step(
        id = id, phase = phase,
        title = title.trim().ifEmpty { text.trim().split(Regex("[.!]"))[0].take(40).trim() },
        durationSecs = secs, body = body, needs = needs.filter { it in stepIds }, ifCondition = ifCondition,
    )
}

private val LINK = Regex("\\{[^:{}]+:([^}]+)\\}")

/** Step text with its `{id:text}` links replaced by the shown text. */
fun plainText(body: String): String = LINK.replace(body, "$1")

/** Number as typed in the form: no trailing zeros, at most three decimals. */
fun decimalText(value: Double): String =
    String.format(Locale.ROOT, "%.3f", value).trimEnd('0').trimEnd('.')

private fun freshId(candidate: String, taken: List<String>): String {
    var id = candidate
    while (id in taken) id += "x"
    return id
}
