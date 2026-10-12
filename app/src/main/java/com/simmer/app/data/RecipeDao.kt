package com.simmer.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {

    /**
     * Returns all recipes as a reactive Flow in display order: User Recipes first, newest first, then
     * built-ins in the order they were seeded. [upsert] keeps a row's `rowid`, so editing never moves a recipe.
     */
    @Query("SELECT * FROM recipes ORDER BY mine DESC, CASE WHEN mine = 1 THEN -rowid ELSE rowid END")
    fun observeAll(): Flow<List<RecipeEntity>>

    /** Returns a single recipe by ID, or null if not found. */
    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun getById(id: String): RecipeEntity?

    /** Inserts a recipe or updates it in place. */
    @Upsert
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
