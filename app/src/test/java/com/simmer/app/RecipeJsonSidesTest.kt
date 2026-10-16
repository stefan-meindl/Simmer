package com.simmer.app

import com.simmer.app.data.RecipeEntity
import com.simmer.app.data.toDomain
import com.simmer.app.data.toEntity
import com.simmer.app.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Seam: [Recipe.toEntity] and [RecipeEntity.toDomain], the stored JSON of a recipe. */
class RecipeJsonSidesTest {
    private val rice = Side(
        id = "rice",
        label = "Rice",
        d = "Soaks up the sauce.",
        ingredients = listOf(Ingredient("i1", "Rice", 300.0, "g", aisle = "Pantry", tips = listOf("Rinse it."))),
        methods = listOf(
            Method("m1", "Rice", "How?", listOf(MethodOption("pot", "Pot", "flame", "15 min", "d"), MethodOption("micro", "Microwave"))),
        ),
        steps = listOf(
            Step("p1", "prep", "Rinse", 0, "Rinse the {i1:rice}."),
            Step("c1", "cook", "Cook", 600, "Cook it.", listOf("p1"), "m1=pot"),
        ),
    )
    private val main = Recipe(id = "r1", title = "Curry", category = "Mains", time = 30, difficulty = "Easy", base = 4)

    @Test fun `sides survive a round trip`() {
        val recipe = main.copy(sides = listOf(rice))

        assertEquals(recipe, recipe.toEntity().toDomain())
    }

    @Test fun `a recipe has no sides by default`() {
        assertTrue(main.sides.isEmpty())
        assertTrue(main.toEntity().toDomain().sides.isEmpty())
    }

    @Test fun `stored JSON from before sides reads as no sides`() {
        val old = """{"id":"r1","title":"Curry","category":"Mains","time":30,"difficulty":"Easy","base":4}"""

        assertTrue(RecipeEntity(id = "r1", json = old, mine = true, edited = false).toDomain().sides.isEmpty())
    }
}
