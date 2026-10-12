package com.simmer.app.data

import com.simmer.app.domain.PlannedChoice
import com.simmer.app.domain.ShoppingEntry
import com.simmer.app.domain.model.Selection
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

fun encodeSelection(sel: Selection): String = buildJsonObject {
    put("choice", buildJsonObject { sel.choice.forEach { (k, v) -> put(k, v) } })
    put("add", buildJsonObject { sel.add.forEach { (k, v) -> put(k, v) } })
}.toString()

fun decodeSelection(json: String): Selection {
    val o = Json.parseToJsonElement(json).jsonObject
    val choice = (o["choice"] as? JsonObject).orEmpty().mapNotNull { (k, v) ->
        (v as? JsonPrimitive)?.contentOrNull?.let { k to it }
    }.toMap()
    val add = (o["add"] as? JsonObject).orEmpty().mapNotNull { (k, v) ->
        (v as? JsonPrimitive)?.booleanOrNull?.let { k to it }
    }.toMap()
    return Selection(choice, add)
}

private fun JsonObject?.orEmpty(): JsonObject = this ?: JsonObject(emptyMap())

/** Shopping list and Planned Recipe persistence (Room). */
@Singleton
class ShoppingRepository @Inject constructor(
    private val dao: ShoppingDao,
) {

    /**
     * Saves [entries] as the recipe's Shopping list items (replacing earlier ones)
     * and records the recipe as Planned with [choice].
     */
    suspend fun addRecipeToList(recipeId: String, entries: List<ShoppingEntry>, choice: PlannedChoice) {
        dao.replaceRecipeItems(
            recipeId = recipeId,
            items = entries.map {
                ShoppingItemEntity(
                    id = "$recipeId-${it.ingredientId}",
                    recipeId = recipeId,
                    name = it.name,
                    amount = it.amount,
                    aisle = it.aisle,
                    note = it.note,
                    checked = false,
                )
            },
            planned = PlannedRecipeEntity(recipeId, choice.servings, encodeSelection(choice.selection)),
        )
    }

    suspend fun getPlanned(recipeId: String): PlannedChoice? =
        dao.getPlanned(recipeId)?.let { PlannedChoice(it.servings, decodeSelection(it.selectionJson)) }
}
