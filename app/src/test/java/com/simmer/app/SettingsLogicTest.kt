package com.simmer.app

import com.simmer.app.domain.KitchenMode
import com.simmer.app.domain.fmtQty
import com.simmer.app.domain.model.Ingredient
import com.simmer.app.domain.settingsPreview
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsLogicTest {

    // --- Decimal comma in German ---

    @Test fun `spice grams use a decimal comma in German`() {
        val cin = Ingredient("cn", "Ground cinnamon", 0.5, "tsp", aisle = "Spices")
        assertEquals("1,5 g", fmtQty(0.5, "tsp", cin, "metric", "grams", "de"))
    }

    @Test fun `whole spice grams have no separator in German`() {
        val salt = Ingredient("sl", "Salt", 1.0, "tsp", aisle = "Spices")
        assertEquals("6 g", fmtQty(1.0, "tsp", salt, "metric", "grams", "de"))
    }

    @Test fun `kilograms use a decimal comma in German`() {
        assertEquals("1,5 kg", fmtQty(1500.0, "g", null, "metric", "spoons", "de"))
    }

    @Test fun `English keeps the decimal point`() {
        val cin = Ingredient("cn", "Ground cinnamon", 0.5, "tsp", aisle = "Spices")
        assertEquals("1.5 g", fmtQty(0.5, "tsp", cin, "metric", "grams", "en"))
    }

    // --- Preview panel ---

    private val chilli = BuiltInRecipes.chilli()

    @Test fun `preview metric spoons lists the rows for 4 servings`() {
        val preview = settingsPreview(chilli, "metric", "spoons", "en")
        assertEquals(
            listOf("Beef mince" to "500 g", "Stock" to "300 ml", "Ground cumin" to "2 tsp", "Dried oregano" to "1 tsp"),
            preview.ingredients.map { it.name to it.amount },
        )
        assertEquals("150°C", preview.ovenTemperature)
    }

    @Test fun `preview imperial grams converts amounts and oven temperature`() {
        val preview = settingsPreview(chilli, "imperial", "grams", "en")
        assertEquals(
            listOf("Beef mince" to "1 lb", "Stock" to "1¼ cups", "Ground cumin" to "5 g", "Dried oregano" to "1 g"),
            preview.ingredients.map { it.name to it.amount },
        )
        assertEquals("300°F", preview.ovenTemperature)
    }

    // --- Kitchen mode ---

    @Test fun `kitchen mode defaults to medium and unknown values fall back`() {
        assertEquals(KitchenMode.Medium, KitchenMode.fromKey(null))
        assertEquals(KitchenMode.Medium, KitchenMode.fromKey("bogus"))
        assertEquals(KitchenMode.Strict, KitchenMode.fromKey("strict"))
        assertEquals(KitchenMode.Free, KitchenMode.fromKey("free"))
        assertEquals(listOf("strict", "medium", "free"), KitchenMode.entries.map { it.key })
    }
}
