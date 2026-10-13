package com.simmer.app

import com.simmer.app.domain.ShoppingItem
import com.simmer.app.domain.shoppingListView
import com.simmer.app.domain.uncheckedCount
import org.junit.Assert.assertEquals
import org.junit.Test

class ShoppingListViewTest {
    private fun recipeItem(id: String, aisle: String, checked: Boolean = false, recipe: String = "chilli") =
        ShoppingItem("$recipe-$id", recipe, id.replaceFirstChar { it.uppercase() }, "1", aisle, "Chilli con carne", checked)

    private fun manual(name: String, checked: Boolean = false) =
        ShoppingItem("manual-$name", null, name, "", "", "", checked)

    @Test fun `recipe items group by aisle keeping their order within an aisle`() {
        val view = shoppingListView(
            listOf(recipeItem("onions", "Produce"), recipeItem("rice", "Pantry"), recipeItem("garlic", "Produce")),
        )

        assertEquals(listOf("Onions", "Garlic"), view.aisles.first { it.aisle == "Produce" }.items.map { it.name })
    }

    @Test fun `aisles follow the shop walk order whatever order recipes were added in`() {
        val items = listOf(
            recipeItem("cumin", "Spices"), recipeItem("rice", "Pantry"), recipeItem("onions", "Produce"),
            recipeItem("mince", "Meat"), recipeItem("beer", "Drinks"),
        )

        val expected = listOf("Produce", "Meat", "Pantry", "Spices", "Drinks")
        assertEquals(expected, shoppingListView(items).aisles.map { it.aisle })
        assertEquals(expected, shoppingListView(items.reversed()).aisles.map { it.aisle })
    }

    @Test fun `unknown aisles come after known ones in alphabetical order`() {
        val view = shoppingListView(
            listOf(recipeItem("a", "Zebra"), recipeItem("b", "Pantry"), recipeItem("c", "apples")),
        )

        assertEquals(listOf("Pantry", "apples", "Zebra"), view.aisles.map { it.aisle })
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

    @Test fun `unchecked count of the raw items matches the view`() {
        val items = listOf(recipeItem("onions", "Produce"), recipeItem("rice", "Pantry", checked = true), manual("Batteries"))

        assertEquals(shoppingListView(items).uncheckedCount, uncheckedCount(items))
    }

    @Test fun `a lingering checked recipe item keeps its aisle, struck through, and stays out of the basket`() {
        val items = listOf(
            recipeItem("onions", "Produce", checked = true), recipeItem("garlic", "Produce"),
            recipeItem("rice", "Pantry", checked = true),
        )

        val view = shoppingListView(items, lingering = setOf("chilli-onions"))

        assertEquals(listOf("Onions", "Garlic"), view.aisles.single().items.map { it.name })
        assertEquals(true, view.aisles.single().items.first().checked)
        assertEquals(listOf("Rice"), view.basket.map { it.name })
    }

    @Test fun `a lingering checked manual item stays under Other items`() {
        val view = shoppingListView(listOf(manual("Batteries", checked = true)), lingering = setOf("manual-Batteries"))

        assertEquals(listOf("Batteries"), view.manual.map { it.name })
        assertEquals(emptyList<String>(), view.basket.map { it.name })
    }

    @Test fun `lingering only holds items that are still checked`() {
        val view = shoppingListView(listOf(manual("Batteries")), lingering = setOf("manual-Batteries"))

        assertEquals(listOf("Batteries"), view.manual.map { it.name })
    }
}
