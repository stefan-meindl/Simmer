package com.simmer.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class ShoppingDao {

    @Query("DELETE FROM shopping_items WHERE recipeId = :recipeId")
    abstract suspend fun deleteForRecipe(recipeId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertItems(items: List<ShoppingItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertPlanned(planned: PlannedRecipeEntity)

    @Query("SELECT * FROM planned_recipes WHERE recipeId = :recipeId")
    abstract suspend fun getPlanned(recipeId: String): PlannedRecipeEntity?

    /** Replaces all of a recipe's items and records it as planned, atomically. */
    @Transaction
    open suspend fun replaceRecipeItems(
        recipeId: String,
        items: List<ShoppingItemEntity>,
        planned: PlannedRecipeEntity,
    ) {
        deleteForRecipe(recipeId)
        insertItems(items)
        upsertPlanned(planned)
    }
}
