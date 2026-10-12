package com.simmer.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A Shopping list entry. Recipe items carry the source [recipeId]; manual items have none.
 * [id] is "<recipeId>-<ingredientId>" for recipe items.
 */
@Entity(tableName = "shopping_items")
data class ShoppingItemEntity(
    @PrimaryKey val id: String,
    val recipeId: String?,
    val name: String,
    val amount: String,
    val aisle: String,
    /** Source recipe title for recipe items. */
    val note: String,
    val checked: Boolean,
)

/** A Planned Recipe: servings and Selection (as JSON) saved by "Add to list". */
@Entity(tableName = "planned_recipes")
data class PlannedRecipeEntity(
    @PrimaryKey val recipeId: String,
    val servings: Int,
    val selectionJson: String,
)
