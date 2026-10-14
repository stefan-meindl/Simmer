package com.simmer.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ShoppingDao {

    @Query("DELETE FROM shopping_items WHERE recipeId = :recipeId")
    abstract suspend fun deleteForRecipe(recipeId: String)

    @Query("SELECT * FROM shopping_items WHERE recipeId = :recipeId")
    abstract suspend fun getForRecipe(recipeId: String): List<ShoppingItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertItems(items: List<ShoppingItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertPlanned(planned: PlannedRecipeEntity)

    @Query("SELECT * FROM planned_recipes WHERE recipeId = :recipeId")
    abstract suspend fun getPlanned(recipeId: String): PlannedRecipeEntity?

    @Query("DELETE FROM planned_recipes WHERE recipeId = :recipeId")
    abstract suspend fun deletePlanned(recipeId: String)

    /** Removes a recipe's items and its Planned state, atomically. Returns what went, for Undo. */
    @Transaction
    open suspend fun removeRecipe(recipeId: String): ShoppingSnapshot {
        val all = getAllItems()
        val planned = listOfNotNull(getPlanned(recipeId))
        val items = all.filter { it.recipeId == recipeId }
        deleteForRecipe(recipeId)
        deletePlanned(recipeId)
        return ShoppingSnapshot(items, planned, all.map { it.id })
    }

    @Query("SELECT * FROM shopping_items ORDER BY rowid")
    abstract suspend fun getAllItems(): List<ShoppingItemEntity>

    @Query("SELECT * FROM planned_recipes")
    abstract suspend fun getAllPlanned(): List<PlannedRecipeEntity>

    @Query("DELETE FROM shopping_items WHERE id IN (:ids)")
    abstract suspend fun deleteItems(ids: List<String>)

    @Query("DELETE FROM planned_recipes WHERE recipeId IN (:recipeIds)")
    abstract suspend fun deletePlannedFor(recipeIds: List<String>)

    @Query("SELECT * FROM shopping_items ORDER BY rowid")
    abstract fun observeItems(): Flow<List<ShoppingItemEntity>>

    @Query("UPDATE shopping_items SET checked = :checked WHERE id = :id")
    abstract suspend fun setChecked(id: String, checked: Boolean)

    @Query("SELECT * FROM planned_recipes")
    abstract fun observeAllPlanned(): Flow<List<PlannedRecipeEntity>>

    /**
     * Replaces all of a recipe's items and records it as planned, atomically.
     * Items that remain keep their `checked` state.
     */
    @Transaction
    open suspend fun replaceRecipeItems(
        recipeId: String,
        items: List<ShoppingItemEntity>,
        planned: PlannedRecipeEntity,
    ) {
        val checked = getForRecipe(recipeId).filter { it.checked }.map { it.id }.toSet()
        deleteForRecipe(recipeId)
        insertItems(items.map { it.copy(checked = it.id in checked) })
        upsertPlanned(planned)
    }

    /** Removes every ticked item. Planned goes only for recipes left with no items. Read and delete are one transaction. */
    @Transaction
    open suspend fun clearTicked(): ShoppingSnapshot {
        val all = getAllItems()
        val ticked = all.filter { it.checked }
        val remainingRecipes = all.filterNot { it.checked }.mapNotNull { it.recipeId }.toSet()
        val tickedRecipes = ticked.mapNotNull { it.recipeId }.toSet()
        val dropped = getAllPlanned().filter { it.recipeId in tickedRecipes && it.recipeId !in remainingRecipes }
        deleteItems(ticked.map { it.id })
        deletePlannedFor(dropped.map { it.recipeId })
        return ShoppingSnapshot(ticked, dropped, all.map { it.id })
    }

    /** Removes every item and all Planned state. Read and delete are one transaction. */
    @Transaction
    open suspend fun clearAll(): ShoppingSnapshot {
        val all = getAllItems()
        val planned = getAllPlanned()
        deleteItems(all.map { it.id })
        deletePlannedFor(planned.map { it.recipeId })
        return ShoppingSnapshot(all, planned, all.map { it.id })
    }

    /**
     * Puts a cleared [snapshot] back exactly as it was (including `checked`), atomically. Items keep their
     * original relative order; items added since the clear go after them. The list is rewritten because
     * REPLACE hands out new rowids, and the list is ordered by rowid.
     */
    @Transaction
    open suspend fun putBack(snapshot: ShoppingSnapshot) {
        val current = getAllItems()
        val position = snapshot.order.withIndex().associate { (i, id) -> id to i }
        val merged = (current + snapshot.items).distinctBy { it.id }
            .sortedBy { position[it.id] ?: Int.MAX_VALUE } // stable: newer items stay in their current order
        deleteItems(current.map { it.id })
        insertItems(merged)
        snapshot.planned.forEach { upsertPlanned(it) }
    }
}
