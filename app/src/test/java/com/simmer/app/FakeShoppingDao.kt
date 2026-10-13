package com.simmer.app

import com.simmer.app.data.PlannedRecipeEntity
import com.simmer.app.data.ShoppingDao
import com.simmer.app.data.ShoppingItemEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** In-memory stand-in for the Room-generated DAO. The @Transaction methods under test are the real ones. */
class FakeShoppingDao : ShoppingDao() {
    val items = mutableListOf<ShoppingItemEntity>()
    val planned = mutableMapOf<String, PlannedRecipeEntity>()

    override suspend fun getForRecipe(recipeId: String): List<ShoppingItemEntity> =
        items.filter { it.recipeId == recipeId }

    override suspend fun deleteForRecipe(recipeId: String) {
        items.removeAll { it.recipeId == recipeId }
    }

    override suspend fun insertItems(items: List<ShoppingItemEntity>) {
        items.forEach { new ->
            this.items.removeAll { it.id == new.id }
            this.items.add(new)
        }
    }

    override suspend fun upsertPlanned(planned: PlannedRecipeEntity) {
        this.planned[planned.recipeId] = planned
    }

    override suspend fun getPlanned(recipeId: String): PlannedRecipeEntity? = planned[recipeId]

    override suspend fun deletePlanned(recipeId: String) {
        planned.remove(recipeId)
    }

    override suspend fun getAllItems(): List<ShoppingItemEntity> = items.toList()

    override suspend fun getAllPlanned(): List<PlannedRecipeEntity> = planned.values.toList()

    override suspend fun deleteItems(ids: List<String>) {
        items.removeAll { it.id in ids }
    }

    override suspend fun deletePlannedFor(recipeIds: List<String>) {
        recipeIds.forEach { planned.remove(it) }
    }

    override fun observeItems(): Flow<List<ShoppingItemEntity>> = flow { emit(items.toList()) }

    override suspend fun setChecked(id: String, checked: Boolean) {
        items.replaceAll { if (it.id == id) it.copy(checked = checked) else it }
    }

    override fun observeAllPlanned(): Flow<List<PlannedRecipeEntity>> = flow { emit(planned.values.toList()) }
}
