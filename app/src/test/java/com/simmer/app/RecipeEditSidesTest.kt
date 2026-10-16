package com.simmer.app

import com.simmer.app.domain.ConditionWords
import com.simmer.app.domain.RecipeDraft
import com.simmer.app.domain.model.*
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Seam: [RecipeDraft], the Edit recipe form, for the sides of a recipe. */
class RecipeEditSidesTest {
    private val rice = Side(
        id = "rice", label = "Rice", d = "Soaks up the sauce.",
        ingredients = listOf(
            Ingredient("i1", "Rice", 300.0, "g"),
            Ingredient("i2", "Salt", 1.0, "tsp"),
        ),
        methods = listOf(
            Method("m1", "Rice", "How?", listOf(MethodOption("pot", "Pot"), MethodOption("micro", "Microwave"))),
        ),
        steps = listOf(
            Step("p1", "prep", "Rinse", 0, "Rinse the {i1:Rice}."),
            Step("c1", "cook", "Cook", 600, "Cook the {i1:Rice} with {i2:Salt}.", listOf("p1"), "m1=pot"),
            Step("c2", "cook", "Microwave", 600, "Microwave the {i1:Rice}.", listOf("p1"), "m1=micro"),
        ),
    )
    private val main = BuiltInRecipes.chilli().copy(sides = listOf(rice))
    private fun draft() = RecipeDraft.from(main)

    @Test fun `edit and save keeps sides`() {
        val saved = draft().copy(title = "New title").toRecipe()
        assertEquals(listOf(rice), saved.sides)
    }

    @Test fun `draft keys are unique across main and sides`() {
        val d = draft()
        val keys = d.ingredients.map { it.key } + d.steps.map { it.key } +
            d.sides.flatMap { s -> s.ingredients.map { it.key } + s.steps.map { it.key } }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test fun `draft with sides survives serialization`() {
        val d = draft()
        val back = Json.decodeFromString(RecipeDraft.serializer(), Json.encodeToString(RecipeDraft.serializer(), d))
        assertEquals(d, back)
    }

    @Test fun `a draft saved before sides decodes with none`() {
        val json = Json.encodeToString(RecipeDraft.serializer(), RecipeDraft.from(BuiltInRecipes.chilli()))
        assertTrue(Json.decodeFromString(RecipeDraft.serializer(), json).sides.isEmpty())
    }

    @Test fun `renaming a side keeps the rest`() {
        val side = draft().renameSide("rice", " Jasmine rice ").toRecipe().sides.single()
        assertEquals("Jasmine rice", side.label)
        assertEquals(rice.ingredients, side.ingredients)
        assertEquals(rice.methods, side.methods)
    }

    @Test fun `editing a side ingredient changes only that ingredient`() {
        val d = draft()
        val key = d.sides.single().ingredients.first { it.id == "i1" }.key
        val saved = d.updateIngredient(key) { it.copy(name = "Basmati", qty = "250") }.toRecipe()
        val edited = saved.sides.single().ingredients.first { it.id == "i1" }
        assertEquals("Basmati", edited.name)
        assertEquals(250.0, edited.quantity, 0.0)
        assertEquals(main.ingredients, saved.ingredients)
    }

    @Test fun `removing a side ingredient unlinks it from side steps only`() {
        val d = draft()
        val key = d.sides.single().ingredients.first { it.id == "i2" }.key
        val side = d.removeIngredient(key).toRecipe().sides.single()
        assertEquals(listOf("i1"), side.ingredients.map { it.id })
        assertFalse(side.steps.first { it.id == "c1" }.body.contains("{i2:"))
    }

    @Test fun `side step text relinks only against the side's ingredients`() {
        val d = draft()
        val key = d.sides.single().steps.first { it.id == "p1" }.key
        val side = d.updateStep(key) { it.copy(text = "Rinse the rice, then the beef.") }.toRecipe().sides.single()
        assertEquals("Rinse the {i1:rice}, then the beef.", side.steps.first { it.id == "p1" }.body)
    }

    @Test fun `deleting a step that uses a method option keeps the other option and the methods`() {
        val d = draft()
        val key = d.sides.single().steps.first { it.id == "c1" }.key
        val side = d.removeStep(key).toRecipe().sides.single()
        assertEquals(listOf("p1", "c2"), side.steps.map { it.id })
        assertEquals(rice.methods, side.methods)
    }

    @Test fun `deleting a step drops needs on it`() {
        val d = draft()
        val key = d.sides.single().steps.first { it.id == "p1" }.key
        val side = d.removeStep(key).toRecipe().sides.single()
        assertTrue(side.steps.all { it.needs.isEmpty() })
    }

    @Test fun `deleting a side removes it`() {
        assertTrue(draft().removeSide("rice").toRecipe().sides.isEmpty())
    }

    @Test fun `a side left without ingredients or without steps is dropped`() {
        val d = draft()
        val noIngs = d.sides.single().ingredients.fold(d) { acc, i -> acc.removeIngredient(i.key) }
        assertTrue(noIngs.toRecipe().sides.isEmpty())
        val noSteps = d.sides.single().steps.fold(d) { acc, s -> acc.removeStep(s.key) }
        assertTrue(noSteps.toRecipe().sides.isEmpty())
    }

    @Test fun `an invalid amount in a side blocks save`() {
        val d = draft()
        val key = d.sides.single().ingredients.first().key
        assertTrue(d.canSave)
        assertFalse(d.updateIngredient(key) { it.copy(qty = "abc") }.canSave)
    }

    @Test fun `a side step moves within its own phase`() {
        val d = draft()
        val c1 = d.sides.single().steps.first { it.id == "c1" }.key
        assertTrue(d.canMove(c1, 1))
        assertFalse(d.canMove(c1, -1))
        val side = d.moveStep(c1, 1).toRecipe().sides.single()
        assertEquals(listOf("p1", "c2", "c1"), side.steps.map { it.id })
    }

    @Test fun `a side step condition reads against the side's methods`() {
        val words = ConditionWords("not %s", "%s added", "no %s")
        assertEquals("Microwave", draft().conditionLabel("m1=micro", words, sideId = "rice"))
    }

    @Test fun `linked names of a side step come from the side's ingredients`() {
        val d = draft()
        val key = d.sides.single().steps.first { it.id == "c1" }.key
        assertEquals(listOf("Rice", "Salt"), d.linkedNames(key))
    }
}
