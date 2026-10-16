package com.simmer.app.domain.model

/**
 * A named wrapper for a condition string (e.g. "key=value", "add:id").
 * Storage remains a plain String; this type exists to name the concept.
 */
data class Condition(val raw: String)

/**
 * A cooking-technique choice option (e.g. "pot", "microwave", "rice cooker").
 */
data class MethodOption(
    val value: String,
    val label: String,
    val icon: String = "flame",
    val time: String = "",
    val d: String = "",
)

/**
 * A method defines alternative ways to cook (e.g. hob vs oven vs slow cooker).
 */
data class Method(
    val id: String,
    val label: String,
    val q: String,
    val options: List<MethodOption>,
)

/**
 * A variation option value (used inside swap variations).
 */
data class Option(
    val value: String,
    val label: String,
)

/**
 * An optional modification to a recipe.
 * kind = "swap": replaces one set of ingredients with another.
 * kind = "add": adds extra optional ingredients (add-in).
 * kind = "suggest": an add-in the Reader proposed that the source does not have ("Extra suggestion"); off by default.
 */
data class Variation(
    val id: String,
    val kind: String,  // "swap" | "add" | "suggest"
    val label: String,
    val options: List<Option> = emptyList(),  // for swap
    val def: String = "",                      // default option for swap
    val d: String = "",                        // description for add-in
    val ing: String = "",                      // ingredient id for add-in
)

/**
 * A single item used in a recipe with a quantity, unit, and optional aisle/tips.
 * May be conditional on the current Selection.
 */
data class Ingredient(
    val id: String,
    val name: String,
    val quantity: Double,
    val unit: String,          // unit string or empty for countable
    val uPlural: String = "",  // plural form (if applicable)
    val note: String = "",
    val aisle: String = "",
    val tips: List<String> = emptyList(),
    val ifCondition: String? = null,
    val alt: List<Pair<String, Double>> = emptyList(),
)

/**
 * A segment of a step body: either plain text or an ingredient link.
 */
sealed class BodySegment {
    data class TextSegment(val text: String) : BodySegment()
    data class IngredientLinkSegment(val id: String, val text: String) : BodySegment()
}

/**
 * A single instruction in a recipe belonging to exactly one phase.
 * phase = "prep" (preparation) always comes before phase = "cook".
 */
data class Step(
    val id: String,
    val phase: String,  // "prep" | "cook"
    val title: String,
    val durationSecs: Int,      // timer in seconds; 0 = no timer
    val body: String,   // body text with {id:text} links
    val needs: List<String> = emptyList(),
    val ifCondition: String? = null,
)

/**
 * A resolved step with computed label and parsed body segments.
 */
data class ResolvedStep(
    val id: String,
    val phase: String,
    val title: String,
    val durationSecs: Int,
    val body: String,
    val label: String,
    val needs: List<ResolvedStep>,
    val segs: List<BodySegment>,
)

/**
 * A resolved ingredient with computed quantity and formatted amount.
 */
data class ResolvedIngredient(
    val id: String,
    val name: String,
    val qty: Double,
    val amount: String,
    val unit: String,
    val uPlural: String,
    val note: String,
    val aisle: String,
    val tips: List<String>,
    val variation: Boolean,
)

/**
 * The complete set of a user's current choices for a recipe.
 * choice: map of method picks and swap variation picks.
 * add: map of add-in toggle states.
 * sides: map of ticked side ids.
 */
data class Selection(
    val choice: Map<String, String> = emptyMap(),
    val add: Map<String, Boolean> = emptyMap(),
    /** Ticked side ids; unticked equals absent. A side's method picks live in [choice] as `s_<sideId>_<methodId>`. */
    val sides: Map<String, Boolean> = emptyMap(),
)

/**
 * The central entity the app revolves around.
 * Immutable while cooking; editable outside the flow.
 */
data class Recipe(
    val id: String,
    val title: String,
    val category: String,
    val tags: List<String> = emptyList(),
    val time: Int,
    val difficulty: String,
    val base: Int,
    val icon: String = "utensils",
    val summary: String = "",
    val variations: List<Variation> = emptyList(),
    val methods: List<Method> = emptyList(),
    val ingredients: List<Ingredient> = emptyList(),
    val steps: List<Step> = emptyList(),
    val mine: Boolean = false,
    val edited: Boolean = false,
    val notes: List<String> = emptyList(),
    val sourceUrl: String? = null,
    val sides: List<Side> = emptyList(),
)

/**
 * A side dish a main can be cooked with. Self-contained: its own ingredients, [methods] and steps,
 * with ids local to the side. It scales with the main's servings and has no servings of its own.
 */
data class Side(
    val id: String,
    val label: String,
    /** Why it fits the main. */
    val d: String = "",
    val ingredients: List<Ingredient> = emptyList(),
    val methods: List<Method> = emptyList(),
    val steps: List<Step> = emptyList(),
)

/**
 * Result of resolving a recipe for a given servings count and selection.
 */
data class ResolvedRecipe(
    val ingredients: List<ResolvedIngredient>,
    val steps: List<ResolvedStep>,
)
