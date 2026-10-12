package com.simmer.app

import com.simmer.app.domain.PlannedChoice
import com.simmer.app.domain.RecipeFilters
import com.simmer.app.domain.model.Selection
import com.simmer.app.domain.recipesOverview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipesOverviewTest {
    private val all = BuiltInRecipes.all()

    private fun ids(filters: RecipeFilters) = recipesOverview(all, emptyMap(), filters).recipes.map { it.id }

    @Test fun `no filters shows every recipe`() {
        assertEquals(listOf("chilli", "curry", "tagliatelle", "shakshuka", "carrots"), ids(RecipeFilters()))
    }

    @Test fun `search matches titles ignoring case and surrounding spaces`() {
        assertEquals(listOf("chilli"), ids(RecipeFilters(query = "  CHILLI ")))
    }

    @Test fun `search matches ingredient names`() {
        assertEquals(listOf("shakshuka"), ids(RecipeFilters(query = "feta")))
    }

    @Test fun `category tabs are the distinct categories in recipe order`() {
        assertEquals(
            listOf("Mains", "Pasta", "Breakfast", "Sides"),
            recipesOverview(all, emptyMap(), RecipeFilters()).categories,
        )
    }

    @Test fun `category filter keeps only that category`() {
        assertEquals(listOf("chilli", "curry"), ids(RecipeFilters(category = "Mains")))
    }

    @Test fun `a category no longer present falls back to All`() {
        val o = recipesOverview(all, emptyMap(), RecipeFilters(category = "Desserts"))
        assertEquals(5, o.recipes.size)
        assertNull(o.category)
    }

    @Test fun `tags are multi-select and AND together`() {
        assertEquals(listOf("curry", "tagliatelle"), ids(RecipeFilters(tags = setOf("Quick"))))
        assertEquals(listOf("tagliatelle"), ids(RecipeFilters(tags = setOf("Quick", "Vegetarian"))))
    }

    @Test fun `search category and tags all AND together`() {
        assertEquals(listOf("curry"), ids(RecipeFilters(query = "basil", category = "Mains", tags = setOf("Quick"))))
        assertEquals(emptyList<String>(), ids(RecipeFilters(query = "feta", category = "Mains")))
    }

    @Test fun `filters are active only when something is set`() {
        assertFalse(recipesOverview(all, emptyMap(), RecipeFilters()).filtersActive)
        assertFalse(recipesOverview(all, emptyMap(), RecipeFilters(query = "   ")).filtersActive)
        assertTrue(recipesOverview(all, emptyMap(), RecipeFilters(query = "x")).filtersActive)
        assertTrue(recipesOverview(all, emptyMap(), RecipeFilters(category = "Mains")).filtersActive)
        assertTrue(recipesOverview(all, emptyMap(), RecipeFilters(tags = setOf("Spicy"))).filtersActive)
    }

    @Test fun `planned section lists planned recipes with their choice, ignoring filters`() {
        val planned = mapOf("chilli" to PlannedChoice(4, Selection(mapOf("mince" to "plant"), mapOf("beer" to true))))
        val o = recipesOverview(all, planned, RecipeFilters(query = "carrots"))
        assertEquals(listOf("chilli"), o.planned.map { it.recipe.id })
        assertEquals(4, o.planned.single().choice.servings)
        assertEquals(listOf("Plant-based", "Dark beer"), o.planned.single().variationLabels)
    }

    @Test fun `planned recipes that no longer exist are dropped`() {
        val o = recipesOverview(all, mapOf("ghost" to PlannedChoice(2, Selection())), RecipeFilters())
        assertTrue(o.planned.isEmpty())
    }
}
