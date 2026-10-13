package com.simmer.app

import com.simmer.app.data.PlannedRecipeEntity
import com.simmer.app.data.ShoppingItemEntity
import com.simmer.app.data.ShoppingRepository
import com.simmer.app.domain.PlannedChoice
import com.simmer.app.domain.ShoppingEntry
import com.simmer.app.domain.model.Selection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ShoppingClearTest {
    private val dao = FakeShoppingDao()
    private val repo = ShoppingRepository(dao)
    private val choice = PlannedChoice(2, Selection(emptyMap(), emptyMap()))
    private fun entry(id: String) = ShoppingEntry(id, id, "1", "Produce", "Chilli con carne")

    private suspend fun seed() {
        repo.addRecipeToList("chilli", listOf(entry("onions"), entry("garlic")), choice)
        repo.addRecipeToList("curry", listOf(entry("ginger")), choice)
        repo.addManualItem("Batteries")
    }

    private fun tick(vararg names: String) = dao.items.replaceAll { if (it.name in names) it.copy(checked = true) else it }

    @Test fun `clear ticked removes ticked recipe and manual items only`() = runBlocking {
        seed()
        tick("onions", "Batteries")

        val snap = repo.clearTicked()

        assertEquals(setOf("garlic", "ginger"), dao.items.map { it.name }.toSet())
        assertEquals(2, snap.items.size)
    }

    @Test fun `clear ticked drops Planned only for recipes with no items left`() = runBlocking {
        seed()
        tick("onions", "ginger")

        repo.clearTicked()

        assertEquals(setOf("chilli"), dao.planned.keys)
    }

    @Test fun `clear all removes manual items and all Planned state`() = runBlocking {
        seed()
        tick("onions")

        val snap = repo.clearAll()

        assertEquals(emptyList<ShoppingItemEntity>(), dao.items)
        assertEquals(emptyMap<String, PlannedRecipeEntity>(), dao.planned)
        assertEquals(4, snap.items.size)
    }

    @Test fun `restore brings back items with checked state and Planned choices`() = runBlocking {
        seed()
        tick("onions", "ginger")
        val before = dao.items.toList()
        val plannedBefore = dao.planned.toMap()

        repo.restore(repo.clearAll())

        assertEquals(before.toSet(), dao.items.toSet())
        assertEquals(plannedBefore, dao.planned)
    }

    @Test fun `restore after clear ticked brings back only what was removed`() = runBlocking {
        seed()
        tick("ginger")
        val plannedBefore = dao.planned.toMap()

        repo.restore(repo.clearTicked())

        assertEquals(setOf("onions", "garlic", "ginger", "Batteries"), dao.items.map { it.name }.toSet())
        assertEquals(true, dao.items.first { it.name == "ginger" }.checked)
        assertEquals(plannedBefore, dao.planned)
    }

    @Test fun `nothing to clear gives an empty snapshot`() = runBlocking {
        seed()
        assertEquals(true, repo.clearTicked().isEmpty)
    }

    @Test fun `restore after clear ticked puts items back in their original order`() = runBlocking {
        seed()
        tick("onions", "ginger")
        val before = dao.getAllItems().map { it.name }

        repo.restore(repo.clearTicked())

        assertEquals(before, dao.getAllItems().map { it.name })
    }

    @Test fun `restore after clear all puts items back in their original order`() = runBlocking {
        seed()
        val before = dao.getAllItems().map { it.name }

        repo.restore(repo.clearAll())

        assertEquals(before, dao.getAllItems().map { it.name })
    }

    @Test fun `restore keeps items added after the clear and their ticks`() = runBlocking {
        seed()
        tick("onions")
        val snap = repo.clearTicked()
        repo.addManualItem("Milk")
        tick("Milk")

        repo.restore(snap)

        assertEquals(listOf("onions", "garlic", "ginger", "Batteries", "Milk"), dao.getAllItems().map { it.name })
        assertEquals(true, dao.items.first { it.name == "Milk" }.checked)
    }

    @Test fun `clear all with only Planned state still clears and restores it`() = runBlocking {
        dao.planned["chilli"] = PlannedRecipeEntity("chilli", 2, "{}")

        val snap = repo.clearAll()

        assertEquals(false, snap.isEmpty)
        assertEquals(emptyMap<String, PlannedRecipeEntity>(), dao.planned)
        repo.restore(snap)
        assertEquals(setOf("chilli"), dao.planned.keys)
    }
}
