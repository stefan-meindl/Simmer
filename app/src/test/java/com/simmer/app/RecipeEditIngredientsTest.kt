package com.simmer.app

import com.simmer.app.domain.ConditionWords
import com.simmer.app.domain.RecipeDraft
import com.simmer.app.domain.model.Recipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Seam: [RecipeDraft], the Edit recipe form state, from a Recipe to a saved Recipe. */
class RecipeEditIngredientsTest {

    private val chilli = BuiltInRecipes.chilli()
    private fun draft(r: Recipe = chilli) = RecipeDraft.from(r)
    private fun RecipeDraft.ingKey(id: String) = ingredients.first { it.id == id }.key

    @Test fun `an untouched draft saves back to the same recipe`() {
        assertEquals(chilli, draft().toRecipe())
    }

    @Test fun `details are saved, servings clamp to 1 to 12 and time to at least 1`() {
        val r = draft().copy(title = "  Hot chilli ", summary = " Spicy ", servings = "40", time = "0", difficulty = "Hard", category = "Soups").toRecipe()
        assertEquals("Hot chilli", r.title)
        assertEquals("Spicy", r.summary)
        assertEquals(12, r.base)
        assertEquals(1, r.time)
        assertEquals("Hard", r.difficulty)
        assertEquals("Soups", r.category)
    }

    @Test fun `non numeric servings and time keep the original values`() {
        val r = draft().copy(servings = "abc", time = "").toRecipe()
        assertEquals(chilli.base, r.base)
        assertEquals(chilli.time, r.time)
    }

    @Test fun `save is disabled without a title, a named ingredient or a step with text`() {
        assertTrue(draft().canSave)
        assertFalse(draft().copy(title = "  ").canSave)
        assertFalse(draft().let { d -> d.copy(ingredients = d.ingredients.map { it.copy(name = " ") }) }.canSave)
        assertFalse(draft().let { d -> d.copy(steps = d.steps.map { it.copy(text = "") }) }.canSave)
    }

    @Test fun `amount accepts decimals, fractions and glyphs`() {
        fun qty(text: String): Double {
            val d = draft()
            return d.updateIngredient(d.ingKey("cu")) { it.copy(qty = text) }.toRecipe().ingredients.first { it.id == "cu" }.quantity
        }
        assertEquals(1.5, qty("1.5"), 0.001)
        assertEquals(1.5, qty("1,5"), 0.001)
        assertEquals(0.5, qty("1/2"), 0.001)
        assertEquals(1.5, qty("1 1/2"), 0.001)
        assertEquals(0.5, qty("½"), 0.001)
        assertEquals(0.25, qty("¼"), 0.001)
        assertEquals(0.75, qty("¾"), 0.001)
    }

    @Test fun `unreadable amount keeps the previous quantity`() {
        val d = draft()
        val r = d.updateIngredient(d.ingKey("cu")) { it.copy(qty = "lots") }.toRecipe()
        assertEquals(2.0, r.ingredients.first { it.id == "cu" }.quantity, 0.001)
    }

    @Test fun `quantities show without a trailing zero`() {
        assertEquals("2", draft().ingredients.first { it.id == "cu" }.qty)
        assertEquals("500", draft().ingredients.first { it.id == "mb" }.qty)
    }

    @Test fun `editing an ingredient keeps its tips, condition and plural`() {
        val before = chilli.ingredients.first { it.id == "ga" }
        val d = draft()
        val after = d.updateIngredient(d.ingKey("ga")) { it.copy(name = "Garlic bulb", note = "minced") }
            .toRecipe().ingredients.first { it.id == "ga" }
        assertEquals("Garlic bulb", after.name)
        assertEquals("minced", after.note)
        assertEquals(before.uPlural, after.uPlural)
        assertEquals(before.tips, after.tips)
        assertEquals("mince=beef", draft().toRecipe().ingredients.first { it.id == "mb" }.ifCondition)
    }

    @Test fun `changing the unit drops the plural form`() {
        val d = draft()
        val after = d.updateIngredient(d.ingKey("ga")) { it.copy(unit = "head") }.toRecipe().ingredients.first { it.id == "ga" }
        assertEquals("head", after.unit)
        assertEquals("", after.uPlural)
    }

    @Test fun `added ingredient gets a fresh id and blank ones are dropped on save`() {
        val d = draft().addIngredient()
        val added = d.ingredients.last()
        assertEquals("", added.name)
        assertEquals("1", added.qty)
        assertEquals(chilli.ingredients.size + 1, d.ingredients.size)
        assertTrue(added.id !in chilli.ingredients.map { it.id })
        assertEquals(chilli.ingredients.size, d.toRecipe().ingredients.size)
        val named = d.updateIngredient(added.key) { it.copy(name = "Lime") }.toRecipe()
        assertEquals("Lime", named.ingredients.last().name)
    }

    @Test fun `added ids and keys stay unique after repeated adds`() {
        val d = draft().addIngredient().addIngredient()
        assertEquals(d.ingredients.size, d.ingredients.map { it.id }.toSet().size)
        assertEquals(d.ingredients.size, d.ingredients.map { it.key }.toSet().size)
    }

    @Test fun `removing an ingredient removes the add-in variation that used it`() {
        val d = draft()
        val r = d.removeIngredient(d.ingKey("be")).toRecipe()
        assertTrue(r.ingredients.none { it.id == "be" })
        assertTrue(r.variations.none { it.id == "beer" })
        assertTrue(r.variations.any { it.id == "choc" })
        assertTrue(r.variations.any { it.id == "mince" })
    }

    @Test fun `condition reads as the choice it depends on`() {
        val words = ConditionWords(not = "not %s", added = "%s added", notAdded = "%s not added")
        val d = draft()
        assertEquals("Plant-based", d.conditionLabel("mince=plant", words))
        assertEquals("not Beef", d.conditionLabel("mince!=beef", words))
        assertEquals("Dark beer added", d.conditionLabel("add:beer", words))
        assertEquals("Cinnamon not added", d.conditionLabel("!add:cin", words))
    }
}
