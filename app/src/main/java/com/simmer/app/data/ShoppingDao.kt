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

    /** Removes a recipe's items and its Planned state, atomically. */
    @Transaction
    open suspend fun removeRecipe(recipeId: String) {
        deleteForRecipe(recipeId)
        deletePlanned(recipeId)
    }

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
}
