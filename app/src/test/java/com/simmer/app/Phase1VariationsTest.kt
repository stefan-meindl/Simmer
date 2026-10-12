package com.simmer.app

import com.simmer.app.domain.addInAmount
import com.simmer.app.domain.defaults
import com.simmer.app.domain.isYourPick
import com.simmer.app.domain.model.Selection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase1VariationsTest {
    private val chilli = BuiltInRecipes.chilli()
    private fun ing(id: String) = chilli.ingredients.first { it.id == id }
    private fun variation(id: String) = chilli.variations.first { it.id == id }

    @Test fun `add-in amount is scaled to the serving count`() {
        // chilli is written for 4; 8 servings doubles 200 ml beer and 20 g chocolate
        assertEquals("+ 400 ml", addInAmount(chilli, variation("beer"), 8))
        assertEquals("+ 40 g", addInAmount(chilli, variation("choc"), 8))
        assertEquals("+ 200 ml", addInAmount(chilli, variation("beer"), 4))
    }

    @Test fun `ingredient of the default swap option is not a pick`() {
        assertFalse(isYourPick(chilli, ing("mb"), defaults(chilli)))
    }

    @Test fun `ingredient of a non-default swap option is a pick`() {
        val sel = Selection(choice = defaults(chilli).choice + ("mince" to "plant"))
        assertTrue(isYourPick(chilli, ing("mp"), sel))
    }

    @Test fun `ingredient of a toggled add-in is a pick`() {
        val sel = Selection(choice = defaults(chilli).choice, add = mapOf("beer" to true))
        assertTrue(isYourPick(chilli, ing("be"), sel))
    }

    @Test fun `unconditional ingredient is never a pick`() {
        assertFalse(isYourPick(chilli, ing("on"), defaults(chilli)))
    }

    @Test fun `ingredient conditioned on a method choice is not a pick`() {
        val withMethodCond = ing("wa").copy(ifCondition = "rice=microwave")
        val sel = Selection(choice = mapOf("rice" to "microwave"))
        assertFalse(isYourPick(chilli, withMethodCond, sel))
    }
}
