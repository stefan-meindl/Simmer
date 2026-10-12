package com.simmer.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {

    /** Returns all recipes as a reactive Flow, oldest saved first. */
    @Query("SELECT * FROM recipes ORDER BY rowid")
    fun observeAll(): Flow<List<RecipeEntity>>

    /** Returns a single recipe by ID, or null if not found. */
    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun getById(id: String): RecipeEntity?

    /** Inserts or replaces a recipe. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(recipe: RecipeEntity)

    /** Deletes a recipe by ID. Only user recipes (mine = true) should be deleted. */
    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Returns only user-created recipes. */
    @Query("SELECT * FROM recipes WHERE mine = 1")
    fun observeMine(): Flow<List<RecipeEntity>>

    /** Returns only built-in recipes (including edited ones). */
    @Query("SELECT * FROM recipes WHERE mine = 0")
    fun observeBuiltIn(): Flow<List<RecipeEntity>>
}
