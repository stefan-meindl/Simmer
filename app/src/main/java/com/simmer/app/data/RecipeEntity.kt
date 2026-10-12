package com.simmer.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for persisting recipes.
 * The full recipe JSON is stored as a serialized string so that the flexible
 * structure (variations, methods, ingredients, steps) is kept without a
 * complex multi-table schema at this stage.
 */
@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey
    val id: String,
    /** Serialized JSON of the recipe data (variations, methods, ingredients, steps, etc.) */
    val json: String,
    /** true if this is a user-created recipe (mine = true in domain model) */
    val mine: Boolean,
    /** true if this is a built-in recipe that the user has edited */
    val edited: Boolean,
)
