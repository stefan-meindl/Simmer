package com.simmer.app

import com.simmer.app.domain.EXAMPLE_TEXT
import com.simmer.app.domain.ReviewChoice
import com.simmer.app.domain.addRecipeReview
import com.simmer.app.domain.stubConvert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Seam: [addRecipeReview], what the Add recipe review stage shows about a converted recipe. */
class AddRecipeReviewTest {
    private val example = stubConvert(EXAMPLE_TEXT, id = "u1")!!
    private val review = addRecipeReview(example, units = "metric", spices = "spoons", lang = "en")

    @Test fun `counts are every ingredient, then preparation and cooking steps for the default choices`() {
        assertEquals(9, review.ingredientCount)
        assertEquals(3, review.prepSteps.size)
        assertEquals(3, review.cookSteps.size)
    }

    @Test fun `steps show the first method option and skip the add-in's variant`() {
        assertEquals(listOf("Make the lemon rub"), review.prepSteps.map { it.title }.filter { it.startsWith("Make") })
        assertEquals(
            listOf("Roast the potatoes", "Roast the chicken", "Finish and serve"),
            review.cookSteps.map { it.title },
        )
        assertEquals(listOf("Oven"), review.defaultMethodLabels)
    }

    @Test fun `steps are labelled and name the preparation steps they need`() {
        assertEquals(listOf("Prep 1", "Prep 2", "Prep 3"), review.prepSteps.map { it.label })
        val roastChicken = review.cookSteps.single { it.title == "Roast the chicken" }
        assertEquals(listOf("Prep 2", "Prep 3"), roastChicken.needs.map { it.label })
    }

    @Test fun `choices found are each method then each add-in variation`() {
        assertEquals(
            listOf(
                ReviewChoice.Method(label = "The chicken", icon = "flame", options = listOf("Oven", "Air fryer")),
                ReviewChoice.AddIn(label = "Chilli flakes", description = "A gentle heat in the lemon rub."),
            ),
            review.choices,
        )
    }

    @Test fun `a recipe without methods or variations has no choices and no default note`() {
        val plain = addRecipeReview(example.copy(methods = emptyList(), variations = emptyList()), "metric", "spoons", "en")

        assertTrue(plain.choices.isEmpty())
        assertTrue(plain.defaultMethodLabels.isEmpty())
    }

    @Test fun `the ingredient list shows amounts at the recipe's own servings`() {
        val chicken = review.ingredients.first { it.name == "Chicken thighs" }
        val potatoes = review.ingredients.first { it.name == "Baby potatoes" }
        val garlic = review.ingredients.first { it.name == "Garlic" }

        assertEquals("8", chicken.amount)
        assertEquals("750 g", potatoes.amount)
        assertEquals("6 cloves", garlic.amount)
        assertEquals("skin on, bone in", chicken.note)
        assertEquals("Meat", chicken.aisle)
    }

    @Test fun `only add-in ingredients are tagged optional`() {
        assertEquals(listOf("Chilli flakes"), review.ingredients.filter { it.optional }.map { it.name })
    }

    @Test fun `amounts follow the units and language settings`() {
        val imperial = addRecipeReview(example, units = "imperial", spices = "spoons", lang = "de")

        assertEquals("1¾ lb", imperial.ingredients.first { it.name == "Baby potatoes" }.amount)
        assertFalse(imperial.prepSteps.first().label == "Prep 1")
        assertEquals("Vorb. 1", imperial.prepSteps.first().label)
    }

    @Test fun `ingredient links in steps resolve for the popovers`() {
        assertTrue("i2" in review.ingredientsById)
        assertEquals("750 g", review.ingredientsById.getValue("i2").amount)
    }
}
