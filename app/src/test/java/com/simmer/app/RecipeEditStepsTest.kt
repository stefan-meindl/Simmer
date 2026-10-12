package com.simmer.app

import com.simmer.app.domain.RecipeDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Seam: [RecipeDraft] steps, from the Steps accordion to the saved Recipe. */
class RecipeEditStepsTest {

    private val chilli = BuiltInRecipes.chilli()
    private fun draft() = RecipeDraft.from(chilli)
    private fun RecipeDraft.stepKey(id: String) = steps.first { it.id == id }.key

    @Test fun `step text is shown as plain text without link markup`() {
        val text = draft().steps.first { it.id == "p-veg" }.text
        assertEquals("Finely chop the onions, dice the red pepper and crush the garlic. Keep them in separate piles.", text)
    }

    @Test fun `unchanged step text keeps the original markup`() {
        val d = draft()
        val original = chilli.steps.first { it.id == "p-veg" }.body
        val r = d.updateStep(d.stepKey("p-veg")) { it.copy(title = "Chop everything") }.toRecipe()
        assertEquals(original, r.steps.first { it.id == "p-veg" }.body)
        assertEquals("Chop everything", r.steps.first { it.id == "p-veg" }.title)
    }

    @Test fun `changed step text is linked again against the current ingredients`() {
        val d = draft()
        val r = d.updateStep(d.stepKey("p-veg")) { it.copy(text = "Slice the onions and the garlic.") }.toRecipe()
        assertEquals("Slice the {on:onions} and the {ga:garlic}.", r.steps.first { it.id == "p-veg" }.body)
    }

    @Test fun `linked line lists the ingredients found in the text as you type`() {
        val d = draft()
        val key = d.stepKey("p-veg")
        assertEquals(listOf("Onions", "Garlic"), d.updateStep(key) { it.copy(text = "Slice the onions and the garlic.") }.linkedNames(key))
        assertEquals(emptyList<String>(), d.updateStep(key) { it.copy(text = "Just wait.") }.linkedNames(key))
    }

    @Test fun `a deleted ingredient is no longer linked`() {
        val d = draft()
        val key = d.stepKey("p-veg")
        val gone = d.removeIngredient(d.ingredients.first { it.id == "ga" }.key)
        assertEquals(listOf("Onions"), gone.updateStep(key) { it.copy(text = "Slice the onions and the garlic.") }.linkedNames(key))
    }

    @Test fun `timer minutes accept decimals and become seconds`() {
        val d = draft()
        val key = d.stepKey("p-spice")
        assertEquals("2", d.steps.first { it.key == key }.minutes)
        fun secs(m: String) = d.updateStep(key) { it.copy(minutes = m) }.toRecipe().steps.first { it.id == "p-spice" }.durationSecs
        assertEquals(90, secs("1.5"))
        assertEquals(90, secs("1,5"))
        assertEquals(30, secs("0.1"))
        assertEquals(120, secs("oops"))
    }

    @Test fun `blank step title falls back to the start of the text`() {
        val d = draft()
        val key = d.stepKey("p-veg")
        val r = d.updateStep(key) { it.copy(title = " ", text = "Slice the onions. Then rest.") }.toRecipe()
        assertEquals("Slice the onions", r.steps.first { it.id == "p-veg" }.title)
    }

    @Test fun `added step lands in its phase and is dropped while its text is empty`() {
        val prepBefore = chilli.steps.count { it.phase == "prep" }
        val d = draft().addStep("prep")
        val added = d.steps.last()
        assertEquals("prep", added.phase)
        assertEquals(prepBefore + 1, d.stepsIn("prep").size)
        assertEquals(chilli.steps.size, d.toRecipe().steps.size)
        val r = d.updateStep(added.key) { it.copy(text = "Rinse the beans.") }.toRecipe()
        assertEquals(chilli.steps.size + 1, r.steps.size)
        assertTrue(r.steps.last().id !in chilli.steps.map { it.id })
    }

    @Test fun `move up and down swap within the phase and stop at the ends`() {
        val d = draft()
        val prep = d.stepsIn("prep").map { it.id }
        val first = d.stepKey(prep.first())
        val last = d.stepKey(prep.last())
        assertFalse(d.canMove(first, -1))
        assertFalse(d.canMove(last, 1))
        assertTrue(d.canMove(first, 1))
        val moved = d.moveStep(first, 1)
        assertEquals(listOf(prep[1], prep[0]) + prep.drop(2), moved.stepsIn("prep").map { it.id })
        assertEquals(d.stepsIn("cook").map { it.id }, moved.stepsIn("cook").map { it.id })
    }

    @Test fun `move skips steps of the other phase`() {
        val d = draft().let { base ->
            // prep, cook, prep interleaved in the flat list
            val prep = base.stepsIn("prep")
            val cook = base.stepsIn("cook")
            base.copy(steps = listOf(prep[0], cook[0], prep[1]) + prep.drop(2) + cook.drop(1))
        }
        val a = d.stepsIn("prep")[0].key
        val moved = d.moveStep(a, 1)
        assertEquals(d.stepsIn("prep")[1].id, moved.stepsIn("prep")[0].id)
        assertEquals(d.stepsIn("cook").map { it.id }, moved.stepsIn("cook").map { it.id })
    }

    @Test fun `switching phase moves the step to the other group`() {
        val d = draft()
        val key = d.stepKey("p-veg")
        val moved = d.setPhase(key, "cook")
        assertTrue(moved.stepsIn("cook").any { it.id == "p-veg" })
        assertTrue(moved.stepsIn("prep").none { it.id == "p-veg" })
        assertEquals("cook", moved.toRecipe().steps.first { it.id == "p-veg" }.phase)
    }

    @Test fun `switching phase puts the step at the end of the other group`() {
        val d = draft()
        val moved = d.setPhase(d.stepKey("p-veg"), "cook")
        assertEquals("p-veg", moved.stepsIn("cook").last().id)
        assertEquals("p-veg", moved.toRecipe().steps.last().id)
        assertEquals(d, d.setPhase(d.stepKey("p-veg"), "prep"))
    }

    @Test fun `unchanged step relinks when one of its linked ingredients was deleted`() {
        val d = draft()
        val gone = d.removeIngredient(d.ingredients.first { it.id == "ga" }.key)
        val body = gone.toRecipe().steps.first { it.id == "p-veg" }.body
        assertEquals("Finely chop the {on:onions}, dice the {pe:red pepper} and crush the garlic. Keep them in separate piles.", body)
    }

    @Test fun `unchanged step relinks when a linked ingredient was renamed`() {
        val d = draft()
        val renamed = d.updateIngredient(d.ingredients.first { it.id == "ga" }.key) { it.copy(name = "Shallots") }
        val body = renamed.toRecipe().steps.first { it.id == "p-veg" }.body
        assertTrue(body, "{ga:" !in body)
    }

    @Test fun `unparsable timer blocks save and a valid one does not`() {
        val d = draft()
        val key = d.stepKey("p-spice")
        assertTrue(d.canSave)
        assertFalse(d.updateStep(key) { it.copy(minutes = "oops") }.canSave)
        assertFalse(d.updateStep(key) { it.copy(minutes = "") }.canSave)
        assertTrue(d.updateStep(key) { it.copy(minutes = "1,5") }.canSave)
    }

    @Test fun `an invalid timer on a textless step that is dropped does not block save`() {
        val d = draft().addStep("prep")
        assertTrue(d.updateStep(d.steps.last().key) { it.copy(minutes = "oops") }.canSave)
    }

    @Test fun `removing a step drops it and the needs that pointed at it`() {
        val d = draft()
        val needing = chilli.steps.first { "p-veg" in it.needs }
        val r = d.removeStep(d.stepKey("p-veg")).toRecipe()
        assertTrue(r.steps.none { it.id == "p-veg" })
        assertTrue(r.steps.first { it.id == needing.id }.needs.none { it == "p-veg" })
    }
}
