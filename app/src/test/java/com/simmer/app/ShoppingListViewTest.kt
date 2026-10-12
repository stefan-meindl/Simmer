package com.simmer.app

import com.simmer.app.domain.ShoppingItem
import com.simmer.app.domain.shoppingListView
import org.junit.Assert.assertEquals
import org.junit.Test

class ShoppingListViewTest {
    private fun recipeItem(id: String, aisle: String, checked: Boolean = false, recipe: String = "chilli") =
        ShoppingItem("$recipe-$id", recipe, id.replaceFirstChar { it.uppercase() }, "1", aisle, "Chilli con carne", checked)

    private fun manual(name: String, checked: Boolean = false) =
        ShoppingItem("manual-$name", null, name, "", "", "", checked)

    @Test fun `recipe items group by aisle in first-seen order`() {
        val view = shoppingListView(
            listOf(recipeItem("onions", "Produce"), recipeItem("rice", "Pantry"), recipeItem("garlic", "Produce")),
        )

        assertEquals(listOf("Produce", "Pantry"), view.aisles.map { it.aisle })
        assertEquals(listOf("Onions", "Garlic"), view.aisles[0].items.map { it.name })
    }

    @Test fun `manual items sit in their own section apart from aisle groups`() {
        val view = shoppingListView(listOf(recipeItem("onions", "Produce"), manual("Batteries")))

        assertEquals(listOf("Batteries"), view.manual.map { it.name })
        assertEquals(listOf("Onions"), view.aisles.single().items.map { it.name })
    }

    @Test fun `checked items leave their section for the basket`() {
        val view = shoppingListView(
            listOf(recipeItem("onions", "Produce", checked = true), recipeItem("garlic", "Produce"), manual("Batteries", checked = true)),
        )

        assertEquals(listOf("Garlic"), view.aisles.single().items.map { it.name })
        assertEquals(emptyList<String>(), view.manual.map { it.name })
        assertEquals(listOf("Onions", "Batteries"), view.basket.map { it.name })
    }

    @Test fun `an aisle with only checked items disappears`() {
        val view = shoppingListView(listOf(recipeItem("onions", "Produce", checked = true)))

        assertEquals(emptyList<String>(), view.aisles.map { it.aisle })
    }

    @Test fun `unchecked count covers recipe and manual items but not the basket`() {
        val view = shoppingListView(
            listOf(recipeItem("onions", "Produce"), recipeItem("rice", "Pantry", checked = true), manual("Batteries")),
        )

        assertEquals(2, view.uncheckedCount)
        assertEquals(0, shoppingListView(emptyList()).uncheckedCount)
    }
}
