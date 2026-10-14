package com.simmer.app.data

import com.simmer.app.domain.PlannedChoice
import com.simmer.app.domain.ShoppingEntry
import com.simmer.app.domain.ShoppingItem
import com.simmer.app.domain.model.Selection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.util.UUID
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

private const val MANUAL_ID_PREFIX = "manual-"

private fun ShoppingItemEntity.toDomain() = ShoppingItem(
    id = id,
    recipeId = recipeId,
    name = name,
    amount = amount,
    aisle = aisle,
    note = note,
    checked = checked,
)

/**
 * What a clear action removed, kept so Undo can [ShoppingRepository.restore] it.
 * [order] is the id order of the whole list before the clear.
 */
data class ShoppingSnapshot(
    val items: List<ShoppingItemEntity>,
    val planned: List<PlannedRecipeEntity>,
    val order: List<String> = items.map { it.id },
) {
    val isEmpty: Boolean get() = items.isEmpty() && planned.isEmpty()
}

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

    /** Every Shopping list item, recipe and manual, in the order they were added. */
    val items: Flow<List<ShoppingItem>> = dao.observeItems().map { all ->
        all.map { it.toDomain() }
    }

    /** Adds a manual item by [name]; blank names are ignored. */
    suspend fun addManualItem(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        dao.insertItems(
            listOf(
                ShoppingItemEntity(
                    id = "$MANUAL_ID_PREFIX${UUID.randomUUID()}",
                    recipeId = null,
                    name = trimmed,
                    amount = "",
                    aisle = "",
                    note = "",
                    checked = false,
                ),
            ),
        )
    }

    /** Drops a deleted recipe's Shopping list items and its Planned state; manual items stay. Returns what went, so Undo can [restore] it. */
    suspend fun removeRecipe(recipeId: String): ShoppingSnapshot = dao.removeRecipe(recipeId)

    /** Removes every ticked item, recipe or manual. Planned goes only for recipes left with no items. Returns what went. */
    suspend fun clearTicked(): ShoppingSnapshot = dao.clearTicked()

    /** Removes every item and all Planned state. Returns what went. */
    suspend fun clearAll(): ShoppingSnapshot = dao.clearAll()

    /** Puts a cleared [snapshot] back: items in their original order with their `checked` state, and the Planned choices. */
    suspend fun restore(snapshot: ShoppingSnapshot) = dao.putBack(snapshot)

    suspend fun setChecked(id: String, checked: Boolean) = dao.setChecked(id, checked)

    /** Every Planned Recipe's saved choice, by recipe id. */
    val planned: Flow<Map<String, PlannedChoice>> = dao.observeAllPlanned().map { all ->
        all.associate { it.recipeId to PlannedChoice(it.servings, decodeSelection(it.selectionJson)) }
    }

    suspend fun getPlanned(recipeId: String): PlannedChoice? =
        dao.getPlanned(recipeId)?.let { PlannedChoice(it.servings, decodeSelection(it.selectionJson)) }
}
