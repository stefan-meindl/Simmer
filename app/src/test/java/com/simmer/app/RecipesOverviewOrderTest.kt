package com.simmer.app

import com.simmer.app.domain.RecipeFilters
import com.simmer.app.domain.recipesOverview
import org.junit.Assert.assertEquals
import org.junit.Test

/** Seam: [recipesOverview], where recipes the user added come first, newest first. */
class RecipesOverviewOrderTest {
    private val builtIns = BuiltInRecipes.all()
    private fun mine(id: String) = BuiltInRecipes.curry().copy(id = id, mine = true)

    /** [all] is in storage order: oldest first. */
    private fun ids(all: List<com.simmer.app.domain.model.Recipe>, filters: RecipeFilters = RecipeFilters()) =
        recipesOverview(all, emptyMap(), filters).recipes.map { it.id }

    @Test fun `a user recipe is listed above the built-ins`() {
        assertEquals(
            listOf("u1", "chilli", "curry", "tagliatelle", "shakshuka", "carrots"),
            ids(builtIns + mine("u1")),
        )
    }

    @Test fun `the newest user recipe is first`() {
        assertEquals(
            listOf("u2", "u1", "chilli"),
            ids(listOf(BuiltInRecipes.chilli(), mine("u1"), mine("u2"))),
        )
    }

    @Test fun `filters still apply to the reordered list`() {
        assertEquals(listOf("u1", "curry"), ids(builtIns + mine("u1"), RecipeFilters(query = "curry")))
    }
}
