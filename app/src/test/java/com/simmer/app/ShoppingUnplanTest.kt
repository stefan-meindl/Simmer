package com.simmer.app

import com.simmer.app.data.ShoppingRepository
import com.simmer.app.domain.PlannedChoice
import com.simmer.app.domain.ShoppingEntry
import com.simmer.app.domain.model.Selection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** Unplanning returns a snapshot that Undo can put back. */
class ShoppingUnplanTest {
    private val dao = FakeShoppingDao()
    private val repo = ShoppingRepository(dao)
    private val chilliChoice = PlannedChoice(4, Selection(mapOf("mince" to "plant"), mapOf("beer" to true)))
    private val curryChoice = PlannedChoice(2, Selection(emptyMap(), emptyMap()))
    private fun entry(id: String) = ShoppingEntry(id, id, "1", "Produce", "note")

    private suspend fun seed() {
        repo.addRecipeToList("chilli", listOf(entry("onions"), entry("garlic")), chilliChoice)
        repo.addRecipeToList("curry", listOf(entry("ginger")), curryChoice)
        repo.addManualItem("Batteries")
    }

    @Test fun `unplan returns the recipe's items and Planned choice`() = runBlocking {
        seed()

        val snap = repo.removeRecipe("chilli")

        assertEquals(listOf("onions", "garlic"), snap.items.map { it.name })
        assertEquals(listOf("chilli"), snap.planned.map { it.recipeId })
    }

    @Test fun `unplan leaves other recipes and manual items untouched`() = runBlocking {
        seed()

        repo.removeRecipe("chilli")

        assertEquals(setOf("curry"), dao.planned.keys)
        assertEquals(listOf("ginger", "Batteries"), dao.items.map { it.name })
    }

    @Test fun `restore after unplan brings back items in original order, checked state and the Planned choice`() = runBlocking {
        seed()
        dao.items.replaceAll { if (it.name == "garlic") it.copy(checked = true) else it }
        val before = dao.items.toList()

        repo.restore(repo.removeRecipe("chilli"))

        assertEquals(before, dao.items)
        assertEquals(chilliChoice, repo.getPlanned("chilli"))
    }
}
