package com.simmer.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [RecipeEntity::class, ShoppingItemEntity::class, PlannedRecipeEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun shoppingDao(): ShoppingDao
}

/** Adds the Shopping list and Planned Recipe tables, keeping existing recipes. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `shopping_items` (`id` TEXT NOT NULL, `recipeId` TEXT, " +
                "`name` TEXT NOT NULL, `amount` TEXT NOT NULL, `aisle` TEXT NOT NULL, " +
                "`note` TEXT NOT NULL, `checked` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `planned_recipes` (`recipeId` TEXT NOT NULL, " +
                "`servings` INTEGER NOT NULL, `selectionJson` TEXT NOT NULL, PRIMARY KEY(`recipeId`))",
        )
    }
}
