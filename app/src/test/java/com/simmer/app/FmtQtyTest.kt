package com.simmer.app

import com.simmer.app.domain.fmtQty
import com.simmer.app.domain.model.Ingredient
import org.junit.Assert.assertEquals
import org.junit.Test

class FmtQtyTest {

    // --- Metric ---

    @Test fun `metric grams rounds to step`() = assertEquals("500 g", fmtQty(500.0, "g", null, "metric", "spoons"))
    @Test fun `metric grams large becomes kg`() = assertEquals("1 kg", fmtQty(1000.0, "g", null, "metric", "spoons"))
    @Test fun `metric grams 1500 becomes 1 point 5 kg`() = assertEquals("1.5 kg", fmtQty(1500.0, "g", null, "metric", "spoons"))
    @Test fun `metric ml rounds to step`() = assertEquals("300 ml", fmtQty(300.0, "ml", null, "metric", "spoons"))
    @Test fun `metric ml large becomes l`() = assertEquals("1 l", fmtQty(1000.0, "ml", null, "metric", "spoons"))
    @Test fun `metric small grams rounds up to step`() = assertEquals("20 g", fmtQty(20.0, "g", null, "metric", "spoons"))
    @Test fun `metric small quantity rounds to 5`() = assertEquals("5 g", fmtQty(3.0, "g", null, "metric", "spoons"))

    // --- Non-unit (countable) ---

    @Test fun `no unit whole`() = assertEquals("2", fmtQty(2.0, "", null, "metric", "spoons"))
    @Test fun `no unit fraction`() = assertEquals("½", fmtQty(0.5, "", null, "metric", "spoons"))
    @Test fun `no unit three quarters`() = assertEquals("¾", fmtQty(0.75, "", null, "metric", "spoons"))

    // --- With unit string ---

    @Test fun `tbsp unit`() = assertEquals("2 tbsp", fmtQty(2.0, "tbsp", null, "metric", "spoons"))
    @Test fun `tsp half`() = assertEquals("½ tsp", fmtQty(0.5, "tsp", null, "metric", "spoons"))

    // --- Imperial grams ---

    @Test fun `imperial grams shows oz`() {
        val r = fmtQty(100.0, "g", null, "imperial", "spoons")
        assert(r.endsWith("oz")) { "Expected oz but got $r" }
    }

    @Test fun `imperial grams large shows lb`() {
        val r = fmtQty(500.0, "g", null, "imperial", "spoons")
        assert(r.endsWith("lb")) { "Expected lb but got $r" }
    }

    @Test fun `imperial ml tsp small`() {
        val r = fmtQty(5.0, "ml", null, "imperial", "spoons")
        assert(r.contains("tsp")) { "Expected tsp but got $r" }
    }

    @Test fun `imperial ml cups large`() {
        val r = fmtQty(240.0, "ml", null, "imperial", "spoons")
        assert(r.contains("cup")) { "Expected cup but got $r" }
    }

    // --- Spices in grams mode ---

    @Test fun `spices grams mode tsp oregano`() {
        val oregano = Ingredient("or", "Dried oregano", 1.0, "tsp", aisle = "Spices")
        assertEquals("1 g", fmtQty(1.0, "tsp", oregano, "metric", "grams"))
    }

    @Test fun `spices grams mode tsp salt`() {
        val salt = Ingredient("sl", "Salt", 1.0, "tsp", aisle = "Spices")
        assertEquals("6 g", fmtQty(1.0, "tsp", salt, "metric", "grams"))
    }

    @Test fun `spices grams mode tbsp cumin`() {
        val cumin = Ingredient("cu", "Ground cumin", 1.0, "tbsp", aisle = "Spices")
        // cumin matches default 2.5 g/tsp; 1 tbsp = 3 tsp => 7.5 g
        assertEquals("7.5 g", fmtQty(1.0, "tbsp", cumin, "metric", "grams"))
    }

    @Test fun `spices mode not applied to non-spice ingredient`() {
        val pasta = Ingredient("p", "Pasta", 200.0, "g", aisle = "Pantry")
        assertEquals("200 g", fmtQty(200.0, "g", pasta, "metric", "grams"))
    }

    @Test fun `spices grams mode tsp cinnamon`() {
        val cin = Ingredient("cn", "Ground cinnamon", 0.5, "tsp", aisle = "Spices")
        // cinnamon = 2.6 g/tsp; 0.5 * 2.6 = 1.3 g -> round to 0.5 step => 1.5 g
        assertEquals("1.5 g", fmtQty(0.5, "tsp", cin, "metric", "grams"))
    }
}
