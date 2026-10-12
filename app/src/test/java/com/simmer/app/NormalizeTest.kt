package com.simmer.app

import com.simmer.app.domain.normalize
import com.simmer.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class NormalizeTest {

    // The EXAMPLE_RESULT fixture from simmer-import.js
    private fun exampleInput(): Map<String, Any?> = mapOf(
        "ok" to true,
        "title" to "Sheet-pan lemon chicken",
        "category" to "Mains",
        "tags" to listOf("One-pot"),
        "time" to 60,
        "difficulty" to "Easy",
        "base" to 4,
        "icon" to "flame",
        "summary" to "Crisp chicken thighs roasted with potatoes, lemon and whole garlic on one tray.",
        "ingredients" to listOf(
            mapOf("id" to "i1", "name" to "Chicken thighs", "q" to 8, "u" to "", "note" to "skin on, bone in", "aisle" to "Meat"),
            mapOf("id" to "i2", "name" to "Baby potatoes", "q" to 750, "u" to "g", "aisle" to "Produce"),
            mapOf("id" to "i3", "name" to "Lemons", "q" to 2, "u" to "", "aisle" to "Produce"),
            mapOf("id" to "i4", "name" to "Garlic", "q" to 6, "u" to listOf("clove", "cloves"), "note" to "unpeeled", "aisle" to "Produce"),
            mapOf("id" to "i5", "name" to "Olive oil", "q" to 3, "u" to "tbsp", "aisle" to "Pantry"),
            mapOf("id" to "i6", "name" to "Dried oregano", "q" to 1, "u" to "tsp", "aisle" to "Spices"),
            mapOf("id" to "i7", "name" to "Red onion", "q" to 1, "u" to "", "note" to "cut into wedges", "aisle" to "Produce"),
            mapOf("id" to "i8", "name" to "Parsley", "q" to 1, "u" to listOf("small bunch", "small bunches"), "note" to "chopped", "aisle" to "Produce"),
            mapOf("id" to "i9", "name" to "Chilli flakes", "q" to 1, "u" to listOf("pinch", "pinches"), "if" to "add:v1", "aisle" to "Spices"),
        ),
        "variations" to listOf(
            mapOf("id" to "v1", "kind" to "add", "label" to "Chilli flakes", "d" to "A gentle heat in the lemon rub.", "ing" to "i9")
        ),
        "methods" to listOf(
            mapOf(
                "id" to "m1", "label" to "The chicken", "q" to "How do you want to cook it?",
                "options" to listOf(
                    mapOf("v" to "oven", "l" to "Oven", "icon" to "flame", "time" to "55 min", "d" to "One tray at 220°C, potatoes first."),
                    mapOf("v" to "air", "l" to "Air fryer", "icon" to "zap", "time" to "50 min", "d" to "Two batches at 200°C."),
                )
            )
        ),
        "steps" to listOf(
            mapOf("id" to "p1", "ph" to "prep", "t" to "Cut the vegetables", "d" to 300, "b" to "Halve the {i2:baby potatoes} and cut the {i7:red onion} into wedges."),
            mapOf("id" to "p2", "ph" to "prep", "if" to "!add:v1", "t" to "Make the lemon rub", "d" to 240, "b" to "Zest one {i3:lemon}."),
            mapOf("id" to "p2", "ph" to "prep", "if" to "add:v1", "t" to "Make the lemon rub", "d" to 240, "b" to "Zest one {i3:lemon} and add {i9:chilli flakes}."),
            mapOf("id" to "c1", "ph" to "cook", "if" to "m1=oven", "t" to "Roast the potatoes", "d" to 900, "needs" to listOf("p1"), "b" to "Heat the oven to 220°C."),
            mapOf("id" to "c2", "ph" to "cook", "if" to "m1=oven", "t" to "Roast the chicken", "d" to 2280, "needs" to listOf("p2"), "b" to "Add the {i1:chicken}."),
            mapOf("id" to "c3", "ph" to "cook", "t" to "Finish and serve", "d" to 120, "b" to "Scatter the {i8:parsley} over."),
        ),
        "notes" to listOf("The roast time was given as 35–40 min; the timer uses 38 min."),
    )

    @Test fun `normalize produces recipe with correct title`() {
        val recipe = normalize(exampleInput())
        assertEquals("Sheet-pan lemon chicken", recipe.title)
    }

    @Test fun `normalize produces correct ingredient count`() {
        val recipe = normalize(exampleInput())
        assertEquals(9, recipe.ingredients.size)
    }

    @Test fun `normalize strips invalid condition on ingredient`() {
        val input = exampleInput().toMutableMap()
        @Suppress("UNCHECKED_CAST")
        val ings = (input["ingredients"] as List<Map<String, Any?>>).toMutableList()
        ings.add(mapOf("id" to "ix", "name" to "Ghost ingredient", "q" to 1, "u" to "", "if" to "add:nonexistent"))
        input["ingredients"] = ings
        val recipe = normalize(input)
        val ghostIng = recipe.ingredients.find { it.id == "ix" }
        assertNotNull("Ghost ingredient should still be present", ghostIng)
        assertNull("Invalid condition should be stripped", ghostIng?.ifCondition)
    }

    @Test fun `normalize clamps step duration to 30s minimum`() {
        val input = exampleInput().toMutableMap()
        @Suppress("UNCHECKED_CAST")
        val steps = (input["steps"] as List<Map<String, Any?>>).toMutableList()
        steps.add(mapOf("id" to "p99", "ph" to "prep", "t" to "Quick", "d" to 5, "b" to "A quick step."))
        input["steps"] = steps
        val recipe = normalize(input)
        val step = recipe.steps.find { it.id == "p99" }
        assertNotNull(step)
        assertTrue("Duration should be at least 30", (step?.durationSecs ?: 0) >= 30)
    }

    @Test fun `normalize leaves a step without d as no timer`() {
        val input = exampleInput().toMutableMap()
        @Suppress("UNCHECKED_CAST")
        val steps = (input["steps"] as List<Map<String, Any?>>).toMutableList()
        steps.add(mapOf("id" to "p97", "ph" to "prep", "t" to "Rest", "b" to "Let it rest."))
        input["steps"] = steps
        assertEquals(0, normalize(input).steps.find { it.id == "p97" }?.durationSecs)
    }

    @Test fun `normalize clamps step duration to 24h maximum`() {
        val input = exampleInput().toMutableMap()
        @Suppress("UNCHECKED_CAST")
        val steps = (input["steps"] as List<Map<String, Any?>>).toMutableList()
        steps.add(mapOf("id" to "p98", "ph" to "prep", "t" to "Long", "d" to 999999, "b" to "A very long step."))
        input["steps"] = steps
        val recipe = normalize(input)
        val step = recipe.steps.find { it.id == "p98" }
        assertNotNull(step)
        assertTrue("Duration should be at most 86400", (step?.durationSecs ?: 0) <= 86400)
    }

    @Test fun `normalize rejects method with fewer than 2 options`() {
        val input = exampleInput().toMutableMap()
        input["methods"] = listOf(
            mapOf(
                "id" to "m2", "label" to "Bad method", "q" to "How?",
                "options" to listOf(mapOf("v" to "only", "l" to "Only option", "icon" to "flame"))
            )
        ) + (input["methods"] as List<*>)
        val recipe = normalize(input)
        assertNull("Method with < 2 options should be rejected", recipe.methods.find { it.id == "m2" })
    }

    @Test fun `normalize removes variation with invalid ingredient ref`() {
        val input = exampleInput().toMutableMap()
        @Suppress("UNCHECKED_CAST")
        val vars = (input["variations"] as List<Map<String, Any?>>).toMutableList()
        vars.add(mapOf("id" to "v99", "kind" to "add", "label" to "Ghost", "d" to "desc", "ing" to "nonexistent_ing"))
        input["variations"] = vars
        val recipe = normalize(input)
        assertNull("Variation with invalid ing ref should be removed", recipe.variations.find { it.id == "v99" })
    }

    @Test fun `normalize strips body link for unknown ingredient`() {
        val input = exampleInput().toMutableMap()
        @Suppress("UNCHECKED_CAST")
        val steps = (input["steps"] as List<Map<String, Any?>>).toMutableList()
        steps.add(mapOf("id" to "px", "ph" to "prep", "t" to "Unknown link", "d" to 60, "b" to "Add {ghost:something unknown}."))
        input["steps"] = steps
        val recipe = normalize(input)
        val step = recipe.steps.find { it.id == "px" }
        assertNotNull(step)
        assertEquals("Add something unknown.", step?.body)
    }

    @Test fun `normalize uses 'Mains' for unknown category`() {
        val input = exampleInput().toMutableMap()
        input["category"] = "Unknown Category"
        val recipe = normalize(input)
        assertEquals("Mains", recipe.category)
    }
}
