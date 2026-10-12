package com.simmer.app

import com.simmer.app.data.PlannedRecipeEntity
import com.simmer.app.data.ShoppingDao
import com.simmer.app.data.ShoppingItemEntity
import com.simmer.app.data.ShoppingRepository
import com.simmer.app.domain.PlannedChoice
import com.simmer.app.domain.ShoppingEntry
import com.simmer.app.domain.model.Selection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** In-memory stand-in for the Room-generated DAO. The @Transaction method under test is the real one. */
private class FakeShoppingDao : ShoppingDao() {
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

    override fun observeAllPlanned(): Flow<List<PlannedRecipeEntity>> = flow { emit(planned.values.toList()) }
}

class ShoppingRepositoryTest {
    private val dao = FakeShoppingDao()
    private val repo = ShoppingRepository(dao)

    private fun entry(id: String, aisle: String = "Produce", amount: String = "1", note: String = "Chilli con carne") =
        ShoppingEntry(id, id.replaceFirstChar { it.uppercase() }, amount, aisle, note)

    private val choice = PlannedChoice(6, Selection(mapOf("mince" to "plant"), mapOf("beer" to true)))

    @Test fun `adding stores one item per entry with aisle, amount and recipe title as note`() = runBlocking {
        repo.addRecipeToList("chilli", listOf(entry("onions", "Produce", "3"), entry("rice", "Pantry", "450 g")), choice)

        assertEquals(2, dao.items.size)
        val onions = dao.items.first { it.name == "Onions" }
        assertEquals("chilli", onions.recipeId)
        assertEquals("Produce", onions.aisle)
        assertEquals("3", onions.amount)
        assertEquals("Chilli con carne", onions.note)
        assertEquals(false, onions.checked)
    }

    @Test fun `adding again replaces the recipe's earlier items and leaves others alone`() = runBlocking {
        repo.addRecipeToList("curry", listOf(entry("ginger", note = "Curry")), choice)
        dao.items.add(ShoppingItemEntity("manual-1", null, "Batteries", "1", "", "", false))
        repo.addRecipeToList("chilli", listOf(entry("onions"), entry("garlic")), choice)

        repo.addRecipeToList("chilli", listOf(entry("onions", amount = "6")), choice)

        val chilli = dao.items.filter { it.recipeId == "chilli" }
        assertEquals(listOf("Onions"), chilli.map { it.name })
        assertEquals("6", chilli.single().amount)
        assertEquals(listOf("Ginger"), dao.items.filter { it.recipeId == "curry" }.map { it.name })
        assertEquals(1, dao.items.count { it.recipeId == null })
    }

    @Test fun `adding again keeps checked state of ingredients that remain`() = runBlocking {
        repo.addRecipeToList("chilli", listOf(entry("onions"), entry("garlic")), choice)
        dao.items.replaceAll { it.copy(checked = true) }

        repo.addRecipeToList("chilli", listOf(entry("onions", amount = "6"), entry("rice")), choice)

        val byName = dao.items.associateBy { it.name }
        assertEquals(true, byName.getValue("Onions").checked)
        assertEquals("6", byName.getValue("Onions").amount)
        assertEquals(false, byName.getValue("Rice").checked)
    }

    @Test fun `adding marks the recipe as planned with servings and selection`() = runBlocking {
        repo.addRecipeToList("chilli", listOf(entry("onions")), choice)

        assertEquals(choice, repo.getPlanned("chilli"))
    }

    @Test fun `unplanned recipe has no planned choice`() = runBlocking {
        assertNull(repo.getPlanned("chilli"))
    }

    @Test fun `selection survives the round trip through storage`() {
        val stored = com.simmer.app.data.encodeSelection(choice.selection)
        assertEquals(choice.selection, com.simmer.app.data.decodeSelection(stored))
        assertEquals(
            Selection(mapOf("mince" to "plant"), mapOf("beer" to true)),
            com.simmer.app.data.decodeSelection("""{"choice":{"mince":"plant"},"add":{"beer":true}}"""),
        )
    }

    @Test fun `planned recipes are observable by recipe id with their saved choice`() = runBlocking {
        repo.addRecipeToList("chilli", listOf(entry("onions")), choice)

        assertEquals(mapOf("chilli" to choice), repo.planned.first())
    }
}
