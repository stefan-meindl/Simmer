package com.simmer.app

import com.simmer.app.domain.defaults
import com.simmer.app.domain.model.*
import com.simmer.app.domain.resolve
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for resolve() using the five built-in recipes from simmer-data.js.
 * We use simplified recipe representations that capture the key behaviors.
 */
class ResolveTest {

    // --- Chilli con carne ---

    private val chilli: Recipe by lazy { BuiltInRecipes.chilli() }

    @Test fun `chilli default selection resolves beef mince ingredients`() {
        val sel = defaults(chilli)
        val result = resolve(chilli, 4, sel)
        val beef = result.ingredients.find { it.id == "mb" }
        val plant = result.ingredients.find { it.id == "mp" }
        assertNotNull("Beef mince should be present", beef)
        assertNull("Plant-based mince should not be present", plant)
    }

    @Test fun `chilli plant selection resolves plant ingredients`() {
        val sel = defaults(chilli).copy(choice = mapOf("mince" to "plant", "chilli" to "pot", "rice" to "pot"))
        val result = resolve(chilli, 4, sel)
        val beef = result.ingredients.find { it.id == "mb" }
        val plant = result.ingredients.find { it.id == "mp" }
        assertNull("Beef mince should not be present", beef)
        assertNotNull("Plant-based mince should be present", plant)
    }

    @Test fun `chilli beer add-in reduces stock quantity`() {
        val sel = defaults(chilli).copy(add = mapOf("beer" to true))
        val result = resolve(chilli, 4, sel)
        val stock = result.ingredients.find { it.id == "st" }
        assertNotNull(stock)
        // alt: [['add:beer', 150]] - stock becomes 150 ml when beer is added
        assertEquals(150.0, stock!!.qty, 0.001)
    }

    @Test fun `chilli prep steps come before cook steps`() {
        val sel = defaults(chilli)
        val result = resolve(chilli, 4, sel)
        val phases = result.steps.map { it.ph }
        val lastPrep = phases.lastIndexOf("prep")
        val firstCook = phases.indexOfFirst { it == "cook" }
        if (lastPrep >= 0 && firstCook >= 0) {
            assertTrue("All prep steps before cook steps", lastPrep < firstCook)
        }
    }

    @Test fun `chilli prep steps labeled Prep n`() {
        val sel = defaults(chilli)
        val result = resolve(chilli, 4, sel)
        val prepSteps = result.steps.filter { it.ph == "prep" }
        prepSteps.forEachIndexed { idx, step ->
            assertEquals("Prep ${idx + 1}", step.label)
        }
    }

    @Test fun `chilli cook steps labeled Step n`() {
        val sel = defaults(chilli)
        val result = resolve(chilli, 4, sel)
        val cookSteps = result.steps.filter { it.ph == "cook" }
        cookSteps.forEachIndexed { idx, step ->
            assertEquals("Step ${idx + 1}", step.label)
        }
    }

    @Test fun `chilli servings scaling doubles quantities`() {
        val sel = defaults(chilli)
        val result4 = resolve(chilli, 4, sel)
        val result8 = resolve(chilli, 8, sel)
        val onion4 = result4.ingredients.find { it.id == "on" }!!.qty
        val onion8 = result8.ingredients.find { it.id == "on" }!!.qty
        assertEquals(onion4 * 2, onion8, 0.001)
    }

    @Test fun `chilli body segments parsed`() {
        val sel = defaults(chilli)
        val result = resolve(chilli, 4, sel)
        val step = result.steps.first()
        assertTrue("Step should have body segments", step.segs.isNotEmpty())
    }

    @Test fun `chilli choc add-in includes chocolate ingredient`() {
        val sel = defaults(chilli).copy(add = mapOf("choc" to true))
        val result = resolve(chilli, 4, sel)
        val choc = result.ingredients.find { it.id == "dc" }
        assertNotNull("Dark chocolate should be present when choc add-in enabled", choc)
    }

    // --- Thai green curry ---

    private val curry: Recipe by lazy { BuiltInRecipes.curry() }

    @Test fun `curry default resolves chicken`() {
        val sel = defaults(curry)
        val result = resolve(curry, 2, sel)
        assertNotNull(result.ingredients.find { it.id == "ck" })
        assertNull(result.ingredients.find { it.id == "tf" })
    }

    @Test fun `curry tofu selection resolves tofu`() {
        val sel = defaults(curry).copy(choice = defaults(curry).choice + mapOf("protein" to "tofu"))
        val result = resolve(curry, 2, sel)
        assertNull(result.ingredients.find { it.id == "ck" })
        assertNotNull(result.ingredients.find { it.id == "tf" })
    }

    @Test fun `curry bamboo add-in includes bamboo shoots`() {
        val sel = defaults(curry).copy(add = mapOf("bamboo" to true))
        val result = resolve(curry, 2, sel)
        assertNotNull(result.ingredients.find { it.id == "bs" })
    }

    // --- Tagliatelle ---

    private val tagliatelle: Recipe by lazy { BuiltInRecipes.tagliatelle() }

    @Test fun `tagliatelle fresh pasta alt quantity`() {
        val sel = defaults(tagliatelle).copy(choice = mapOf("pasta" to "fresh"))
        val result = resolve(tagliatelle, 2, sel)
        val pasta = result.ingredients.find { it.id == "t1" }
        assertNotNull(pasta)
        // alt: [['pasta=fresh', 250]] - fresh pasta is 250g
        assertEquals(250.0, pasta!!.qty, 0.001)
    }

    @Test fun `tagliatelle dried pasta default quantity`() {
        val sel = defaults(tagliatelle)
        val result = resolve(tagliatelle, 2, sel)
        val pasta = result.ingredients.find { it.id == "t1" }
        assertNotNull(pasta)
        assertEquals(200.0, pasta!!.qty, 0.001)
    }

    // --- Shakshuka ---

    private val shakshuka: Recipe by lazy { BuiltInRecipes.shakshuka() }

    @Test fun `shakshuka harissa add-in includes harissa`() {
        val sel = defaults(shakshuka).copy(add = mapOf("harissa" to true))
        val result = resolve(shakshuka, 2, sel)
        assertNotNull(result.ingredients.find { it.id == "hr" })
    }

    @Test fun `shakshuka without harissa excludes it`() {
        val sel = defaults(shakshuka)
        val result = resolve(shakshuka, 2, sel)
        assertNull(result.ingredients.find { it.id == "hr" })
    }

    // --- Roast carrots ---

    private val carrots: Recipe by lazy { BuiltInRecipes.carrots() }

    @Test fun `carrots oven selection shows oven step`() {
        val sel = defaults(carrots)  // first option is oven
        val result = resolve(carrots, 4, sel)
        assertTrue(result.steps.any { it.id == "c-roa" })
    }

    @Test fun `carrots air fryer selection shows air fry step`() {
        val sel = defaults(carrots).copy(choice = mapOf("roast" to "air"))
        val result = resolve(carrots, 4, sel)
        val roastStep = result.steps.find { it.id == "c-roa" }
        assertNotNull(roastStep)
        assertTrue("Should show air fry step body", roastStep!!.b.contains("Air-fry"))
    }
}
