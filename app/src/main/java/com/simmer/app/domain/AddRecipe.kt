package com.simmer.app.domain

import com.simmer.app.domain.model.Ingredient
import com.simmer.app.domain.model.Method
import com.simmer.app.domain.model.MethodOption
import com.simmer.app.domain.model.Recipe
import com.simmer.app.domain.model.ResolvedIngredient
import com.simmer.app.domain.model.ResolvedStep
import com.simmer.app.domain.model.Step
import com.simmer.app.domain.model.Variation

/** Fewest characters (ignoring surrounding spaces) the Add recipe text needs before Convert or Download is offered. */
const val MIN_RECIPE_TEXT = 30

/** What "Try an example" puts in the text area. */
val EXAMPLE_TEXT = """Sheet-pan lemon chicken with potatoes
Serves 4 · about 1 hour

This is the one I make every other Sunday. Crispy skin, jammy garlic, almost no washing up.

Ingredients
- 8 chicken thighs, skin on and bone in
- 750g baby potatoes
- 2 lemons
- 6 garlic cloves, unpeeled
- 3 tbsp olive oil
- 1 tsp dried oregano
- 1 red onion
- small bunch of parsley
- pinch of chilli flakes (optional)

Method
Heat the oven to 220C (200C fan). Halve the potatoes and toss them on a big tray with half the oil, salt and pepper. Roast for 15 mins while you get the chicken ready.

Zest one lemon and mix with the rest of the oil, the oregano and chilli flakes if using. Rub all over the chicken.

Take the tray out, add the chicken skin side up, the onion in wedges, the garlic and the other lemon cut into quarters. Roast for 35-40 mins until the skin is crisp and golden.

You can also do this in an air fryer in two batches, 200C for about 25 mins each.

Squeeze the roasted garlic out of the skins, chop the parsley and scatter over before serving."""

/** What the bottom bar offers for the pasted text: Convert, or Download recipe reader. */
enum class ReaderAction { Convert, Download }

fun canConvert(text: String): Boolean = text.trim().length >= MIN_RECIPE_TEXT

private fun isExampleText(text: String): Boolean = text.trim() == EXAMPLE_TEXT.trim()

/**
 * Until the on-device recipe reader exists, only the built-in example can be converted.
 * Every other text asks for the reader download.
 */
fun readerAction(text: String): ReaderAction = if (isExampleText(text)) ReaderAction.Convert else ReaderAction.Download

/**
 * The stand-in for the recipe reader: the example's fixed result as a new User Recipe with [id],
 * or null for any other text. The result is not filtered by the cook's appliances.
 */
fun stubConvert(text: String, id: String): Recipe? = if (isExampleText(text)) exampleRecipe(id) else null

private fun exampleRecipe(id: String): Recipe = Recipe(
    id = id,
    mine = true,
    title = "Sheet-pan lemon chicken",
    category = "Mains",
    tags = listOf("One-pot"),
    time = 60,
    difficulty = "Easy",
    base = 4,
    icon = "flame",
    summary = "Crisp chicken thighs roasted with potatoes, lemon and whole garlic on one tray.",
    ingredients = listOf(
        Ingredient("i1", "Chicken thighs", 8.0, "", note = "skin on, bone in", aisle = "Meat",
            tips = listOf("Pat the skin dry with kitchen paper so it crisps.")),
        Ingredient("i2", "Baby potatoes", 750.0, "g", note = "halved", aisle = "Produce"),
        Ingredient("i3", "Lemons", 2.0, "", aisle = "Produce"),
        Ingredient("i4", "Garlic", 6.0, "clove", uPlural = "cloves", note = "unpeeled", aisle = "Produce",
            tips = listOf("Leave the skins on so the cloves roast soft instead of burning.")),
        Ingredient("i5", "Olive oil", 3.0, "tbsp", aisle = "Pantry"),
        Ingredient("i6", "Dried oregano", 1.0, "tsp", aisle = "Spices"),
        Ingredient("i7", "Red onion", 1.0, "", note = "cut into wedges", aisle = "Produce"),
        Ingredient("i8", "Parsley", 1.0, "small bunch", uPlural = "small bunches", note = "chopped", aisle = "Produce"),
        Ingredient("i9", "Chilli flakes", 1.0, "pinch", uPlural = "pinches", aisle = "Spices", ifCondition = "add:v1"),
    ),
    variations = listOf(
        Variation("v1", "add", "Chilli flakes", d = "A gentle heat in the lemon rub.", ing = "i9"),
    ),
    methods = listOf(
        Method(
            id = "m1", label = "The chicken", q = "How do you want to cook it?",
            options = listOf(
                MethodOption("oven", "Oven", "flame", "55 min", "One tray at 220°C, potatoes first."),
                MethodOption("air", "Air fryer", "zap", "50 min", "Two batches at 200°C."),
            ),
        ),
    ),
    steps = listOf(
        Step("p1", "prep", "Cut the vegetables", 300,
            "Halve the {i2:baby potatoes} and cut the {i7:red onion} into wedges."),
        Step("p2", "prep", "Make the lemon rub", 240,
            "Zest one {i3:lemon} and mix it with 1½ tbsp of the {i5:olive oil} and the {i6:oregano}. Rub it all over the {i1:chicken thighs}.",
            ifCondition = "!add:v1"),
        Step("p2", "prep", "Make the lemon rub", 240,
            "Zest one {i3:lemon} and mix it with 1½ tbsp of the {i5:olive oil}, the {i6:oregano} and the {i9:chilli flakes}. Rub it all over the {i1:chicken thighs}.",
            ifCondition = "add:v1"),
        Step("p3", "prep", "Quarter the lemon", 60,
            "Cut the other {i3:lemon} into quarters and chop the {i8:parsley}."),
        Step("c1", "cook", "Roast the potatoes", 900,
            "Heat the oven to 220°C (200°C fan). Toss the {i2:potatoes} on a large tray with the rest of the {i5:olive oil}, salt and pepper, and roast.",
            needs = listOf("p1"), ifCondition = "m1=oven"),
        Step("c2", "cook", "Roast the chicken", 2280,
            "Add the {i1:chicken} skin side up with the {i7:onion}, {i4:garlic} and lemon quarters. Roast until the skin is crisp and golden.",
            needs = listOf("p2", "p3"), ifCondition = "m1=oven"),
        Step("c1", "cook", "Air-fry the first batch", 1500,
            "Toss the {i2:potatoes} with the rest of the {i5:olive oil}. Air-fry half the potatoes and chicken with half the {i7:onion}, {i4:garlic} and lemon at 200°C.",
            needs = listOf("p1", "p2", "p3"), ifCondition = "m1=air"),
        Step("c2", "cook", "Air-fry the second batch", 1500,
            "Keep the first batch warm and cook the rest the same way.", ifCondition = "m1=air"),
        Step("c3", "cook", "Finish and serve", 120,
            "Squeeze the roasted {i4:garlic} out of its skins and scatter over the tray with the {i8:parsley}."),
    ),
    notes = listOf(
        "The roast time was given as 35–40 min; the timer uses 38 min.",
        "Air fryer time is per batch, so it runs as two steps.",
        "Chilli flakes were marked optional, so they are an add-in.",
    ),
)

// ---------------------------------------------------------------------------
// Review
// ---------------------------------------------------------------------------

/** One row of the review's Ingredients tab. */
data class ReviewIngredient(
    val name: String,
    /** True for an add-in's ingredient. */
    val optional: Boolean,
    val note: String,
    val aisle: String,
    val amount: String,
)

/** One row of "Choices found". */
sealed interface ReviewChoice {
    data class Method(val label: String, val icon: String, val options: List<String>) : ReviewChoice
    data class AddIn(val label: String, val description: String) : ReviewChoice
}

/** What the Add recipe review shows about a converted [recipe]. */
data class AddRecipeReview(
    val recipe: Recipe,
    val ingredients: List<ReviewIngredient>,
    val choices: List<ReviewChoice>,
    /** Steps for the default choices (each Method's first option, add-ins off), in display order. */
    val prepSteps: List<ResolvedStep>,
    val cookSteps: List<ResolvedStep>,
    /** First option of each Method, for "Shown for ...". */
    val defaultMethodLabels: List<String>,
    /** Ingredients of the default choices by id, for the Link popovers. */
    val ingredientsById: Map<String, ResolvedIngredient>,
) {
    val ingredientCount: Int get() = ingredients.size
}

/**
 * The review of [recipe] at its own servings and default choices, with amounts in the cook's
 * [units] and [spices] and labels in [lang].
 */
fun addRecipeReview(recipe: Recipe, units: String, spices: String, lang: String): AddRecipeReview {
    val resolved = resolve(recipe, recipe.base, defaults(recipe), units, spices, lang)
    return AddRecipeReview(
        recipe = recipe,
        ingredients = recipe.ingredients.map {
            ReviewIngredient(
                name = it.name,
                optional = isAddInCondition(it.ifCondition),
                note = it.note,
                aisle = it.aisle,
                amount = fmtQty(it.quantity, it.unit, it, units, spices, lang),
            )
        },
        choices = recipe.methods.map { m ->
            ReviewChoice.Method(m.label, m.options.firstOrNull()?.icon ?: "flame", m.options.map { it.label })
        } + recipe.variations.map { ReviewChoice.AddIn(it.label, it.d) },
        prepSteps = resolved.steps.filter { it.phase == StepPhase.PREP },
        cookSteps = resolved.steps.filter { it.phase == StepPhase.COOK },
        defaultMethodLabels = recipe.methods.mapNotNull { it.options.firstOrNull()?.label },
        ingredientsById = resolved.ingredients.associateBy { it.id },
    )
}

// ---------------------------------------------------------------------------
// Kitchen line
// ---------------------------------------------------------------------------

/** What the paste stage says about which appliances the new Methods may use. */
sealed interface KitchenLine {
    /** Kitchen mode Free for all. */
    data object AnyAppliance : KitchenLine

    /** Strict or Medium: only these appliance keys (catalogue keys, or "c:Name" custom ones); may be empty. */
    data class OnlyYours(val appliances: List<String>) : KitchenLine
}

fun kitchenLine(appliances: Set<String>, mode: KitchenMode): KitchenLine =
    if (mode == KitchenMode.Free) KitchenLine.AnyAppliance else KitchenLine.OnlyYours(orderAppliances(appliances))
