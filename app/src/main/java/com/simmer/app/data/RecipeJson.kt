package com.simmer.app.data

import com.simmer.app.domain.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

// ---------------------------------------------------------------------------
// Serializable mirror types (for kotlinx.serialization)
// ---------------------------------------------------------------------------

@Serializable
data class MethodOptionJson(
    val v: String,
    val l: String,
    val icon: String = "flame",
    val time: String = "",
    val d: String = "",
)

@Serializable
data class MethodJson(
    val id: String,
    val label: String,
    val q: String,
    val options: List<MethodOptionJson>,
)

@Serializable
data class OptionJson(val v: String, val l: String)

@Serializable
data class VariationJson(
    val id: String,
    val kind: String,
    val label: String,
    val options: List<OptionJson> = emptyList(),
    val def: String = "",
    val d: String = "",
    val ing: String = "",
)

@Serializable
data class IngredientJson(
    val id: String,
    val name: String,
    val q: Double,
    val u: String,
    val uPlural: String = "",
    val note: String = "",
    val aisle: String = "",
    val tips: List<String> = emptyList(),
    val ifCondition: String? = null,
    val altConditions: List<String> = emptyList(),
    val altValues: List<Double> = emptyList(),
)

@Serializable
data class StepJson(
    val id: String,
    val ph: String,
    val t: String,
    val d: Int = 0,
    val b: String,
    val needs: List<String> = emptyList(),
    val ifCondition: String? = null,
)

@Serializable
data class SideJson(
    val id: String,
    val label: String,
    val d: String = "",
    val ingredients: List<IngredientJson> = emptyList(),
    val methods: List<MethodJson> = emptyList(),
    val steps: List<StepJson> = emptyList(),
)

@Serializable
data class RecipeJson(
    val id: String,
    val title: String,
    val category: String,
    val tags: List<String> = emptyList(),
    val time: Int,
    val difficulty: String,
    val base: Int,
    val icon: String = "utensils",
    val summary: String = "",
    val variations: List<VariationJson> = emptyList(),
    val methods: List<MethodJson> = emptyList(),
    val ingredients: List<IngredientJson> = emptyList(),
    val steps: List<StepJson> = emptyList(),
    val notes: List<String> = emptyList(),
    val sourceUrl: String? = null,
    val sides: List<SideJson> = emptyList(),
)

// ---------------------------------------------------------------------------
// Mapping helpers
// ---------------------------------------------------------------------------

private val jsonCodec = Json { ignoreUnknownKeys = true }

fun Recipe.toEntity(): RecipeEntity {
    val rj = RecipeJson(
        id = id,
        title = title,
        category = category,
        tags = tags,
        time = time,
        difficulty = difficulty,
        base = base,
        icon = icon,
        summary = summary,
        variations = variations.map { v ->
            VariationJson(v.id, v.kind, v.label, v.options.map { OptionJson(it.value, it.label) }, v.def, v.d, v.ing)
        },
        methods = methods.map { it.toJson() },
        ingredients = ingredients.map { it.toJson() },
        steps = steps.map { it.toJson() },
        notes = notes,
        sourceUrl = sourceUrl,
        sides = sides.map { sd ->
            SideJson(sd.id, sd.label, sd.d, sd.ingredients.map { it.toJson() }, sd.methods.map { it.toJson() }, sd.steps.map { it.toJson() })
        },
    )
    return RecipeEntity(
        id = id,
        json = jsonCodec.encodeToString(RecipeJson.serializer(), rj),
        mine = mine,
        edited = edited,
    )
}

/** The Add recipe draft as text, for saved state. */
fun Recipe.toDraftJson(): String = toEntity().json

/** Inverse of [toDraftJson]; the draft is always a User Recipe that is not an edited built-in. */
fun draftFromJson(json: String): Recipe =
    RecipeEntity(id = "", json = json, mine = true, edited = false).toDomain()

fun RecipeEntity.toDomain(): Recipe {
    val rj = jsonCodec.decodeFromString(RecipeJson.serializer(), this.json)
    return rj.toDomain(mine = this.mine, edited = this.edited)
}

fun RecipeJson.toDomain(mine: Boolean = false, edited: Boolean = false): Recipe {
    return Recipe(
        id = id,
        title = title,
        category = category,
        tags = tags,
        time = time,
        difficulty = difficulty,
        base = base,
        icon = icon,
        summary = summary,
        variations = variations.map { v ->
            Variation(v.id, v.kind, v.label, v.options.map { Option(it.v, it.l) }, v.def, v.d, v.ing)
        },
        methods = methods.map { it.toDomain() },
        ingredients = ingredients.map { it.toDomain() },
        steps = steps.map { it.toDomain() },
        mine = mine,
        edited = edited,
        notes = notes,
        sourceUrl = sourceUrl,
        sides = sides.map { sd ->
            Side(sd.id, sd.label, sd.d, sd.ingredients.map { it.toDomain() }, sd.methods.map { it.toDomain() }, sd.steps.map { it.toDomain() })
        },
    )
}

private fun Method.toJson() =
    MethodJson(id, label, q, options.map { o -> MethodOptionJson(o.value, o.label, o.icon, o.time, o.d) })

private fun MethodJson.toDomain() =
    Method(id, label, q, options.map { o -> MethodOption(o.v, o.l, o.icon, o.time, o.d) })

private fun Ingredient.toJson() = IngredientJson(
    id = id,
    name = name,
    q = quantity,
    u = unit,
    uPlural = uPlural,
    note = note,
    aisle = aisle,
    tips = tips,
    ifCondition = ifCondition,
    altConditions = alt.map { it.first },
    altValues = alt.map { it.second },
)

private fun IngredientJson.toDomain() = Ingredient(
    id = id,
    name = name,
    quantity = q,
    unit = u,
    uPlural = uPlural,
    note = note,
    aisle = aisle,
    tips = tips,
    ifCondition = ifCondition,
    alt = altConditions.zip(altValues),
)

private fun Step.toJson() = StepJson(id, phase, title, durationSecs, body, needs, ifCondition)

private fun StepJson.toDomain() = Step(id, ph, t, d, b, needs, ifCondition)
