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
    val d: Int,
    val b: String,
    val needs: List<String> = emptyList(),
    val ifCondition: String? = null,
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
        methods = methods.map { m ->
            MethodJson(m.id, m.label, m.q, m.options.map { o ->
                MethodOptionJson(o.value, o.label, o.icon, o.time, o.d)
            })
        },
        ingredients = ingredients.map { i ->
            IngredientJson(
                id = i.id,
                name = i.name,
                q = i.quantity,
                u = i.unit,
                uPlural = i.uPlural,
                note = i.note,
                aisle = i.aisle,
                tips = i.tips,
                ifCondition = i.ifCondition,
                altConditions = i.alt.map { it.first },
                altValues = i.alt.map { it.second },
            )
        },
        steps = steps.map { s ->
            StepJson(s.id, s.phase, s.title, s.durationSecs, s.body, s.needs, s.ifCondition)
        },
        notes = notes,
    )
    return RecipeEntity(
        id = id,
        json = jsonCodec.encodeToString(RecipeJson.serializer(), rj),
        mine = mine,
        edited = edited,
    )
}

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
        methods = methods.map { m ->
            Method(m.id, m.label, m.q, m.options.map { o ->
                MethodOption(o.v, o.l, o.icon, o.time, o.d)
            })
        },
        ingredients = ingredients.map { i ->
            Ingredient(
                id = i.id,
                name = i.name,
                quantity = i.q,
                unit = i.u,
                uPlural = i.uPlural,
                note = i.note,
                aisle = i.aisle,
                tips = i.tips,
                ifCondition = i.ifCondition,
                alt = i.altConditions.zip(i.altValues),
            )
        },
        steps = steps.map { s ->
            Step(s.id, s.ph, s.t, s.d, s.b, s.needs, s.ifCondition)
        },
        mine = mine,
        edited = edited,
        notes = notes,
    )
}
