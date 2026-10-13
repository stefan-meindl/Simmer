package com.simmer.app

import com.simmer.app.domain.BODY_TIP_MARK
import com.simmer.app.domain.Phase1State
import com.simmer.app.domain.layoutBody
import com.simmer.app.domain.model.BodySegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StepBodyLayoutTest {
    private val state = Phase1State.initial(BuiltInRecipes.chilli(), planned = null)
    private val byId = state.resolved().ingredients.associateBy { it.id }

    private fun link(id: String, text: String) = BodySegment.IngredientLinkSegment(id, text)

    @Test fun `a link shows its amount in parentheses after the word`() {
        val body = layoutBody(listOf(BodySegment.TextSegment("Crush "), link("ga", "garlic")), byId)
        assertTrue(body.text, body.text.startsWith("Crush garlic (3 cloves)"))
    }

    @Test fun `the underline covers the word and its amount`() {
        val body = layoutBody(listOf(BodySegment.TextSegment("Crush "), link("ga", "garlic")), byId)
        val l = body.links.single()
        assertEquals("garlic (3 cloves)", body.text.substring(l.start, l.underlineEnd))
    }

    @Test fun `a blank amount shows the word only`() {
        val blank = byId.getValue("ga").copy(amount = "", tips = emptyList())
        val body = layoutBody(listOf(link("ga", "garlic")), mapOf("ga" to blank))
        assertEquals("garlic", body.text)
        assertEquals(6, body.links.single().underlineEnd)
    }

    @Test fun `only a link with tips gets the tip mark and is tappable`() {
        val tipped = byId.getValue("ga").copy(tips = listOf("Smash with the flat of a knife."))
        val plain = byId.getValue("on").copy(tips = emptyList())
        val body = layoutBody(
            listOf(link("ga", "garlic"), BodySegment.TextSegment(" and "), link("on", "onions")),
            mapOf("ga" to tipped, "on" to plain),
        )
        val (g, o) = body.links
        assertTrue(g.tappable)
        assertFalse(o.tappable)
        assertEquals(BODY_TIP_MARK, body.text[g.end - 1])
        assertEquals(g.underlineEnd + 2, g.end)
        assertEquals(o.underlineEnd, o.end)
        assertFalse(body.text.substring(o.start, o.end).contains(BODY_TIP_MARK))
    }

    @Test fun `amounts follow servings and units`() {
        val imperial = state.copy(servings = 8).resolved(units = "imperial").ingredients.associateBy { it.id }
        val body = layoutBody(listOf(link("ri", "rice")), imperial)
        assertTrue(body.text, body.text.startsWith("rice (1¼ lb)"))
    }

    @Test fun `a link to an unknown ingredient stays plain text`() {
        val body = layoutBody(listOf(link("zz", "salt")), byId)
        assertEquals("salt", body.text)
        assertTrue(body.links.isEmpty())
    }
}
