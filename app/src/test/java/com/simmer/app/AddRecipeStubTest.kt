package com.simmer.app

import com.simmer.app.domain.EXAMPLE_TEXT
import com.simmer.app.domain.ReaderAction
import com.simmer.app.domain.canConvert
import com.simmer.app.domain.readerAction
import com.simmer.app.domain.stubConvert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Seam: the paste stage's decisions, [canConvert], [readerAction] and [stubConvert]. */
class AddRecipeStubTest {

    @Test fun `convert needs at least 30 characters of text`() {
        assertFalse(canConvert(""))
        assertFalse(canConvert("a".repeat(29)))
        assertTrue(canConvert("a".repeat(30)))
    }

    @Test fun `surrounding spaces do not count towards the 30 characters`() {
        assertFalse(canConvert("   " + "a".repeat(27) + "   "))
    }

    @Test fun `the example text is converted by the stub`() {
        val recipe = stubConvert(EXAMPLE_TEXT, id = "u1")!!

        assertEquals("u1", recipe.id)
        assertTrue(recipe.mine)
        assertEquals("Sheet-pan lemon chicken", recipe.title)
        assertEquals("Mains", recipe.category)
        assertEquals(60, recipe.time)
        assertEquals(4, recipe.base)
        assertEquals(9, recipe.ingredients.size)
    }

    @Test fun `the example text still converts with spaces or newlines around it`() {
        assertEquals("u1", stubConvert("\n  $EXAMPLE_TEXT \n", id = "u1")!!.id)
    }

    @Test fun `any other text is not converted by the stub`() {
        assertNull(stubConvert(EXAMPLE_TEXT + " and more", id = "u1"))
        assertNull(stubConvert("Pancakes: flour, milk, egg. Whisk and fry in butter.", id = "u1"))
    }

    @Test fun `the example text offers Convert and other text offers Download`() {
        assertEquals(ReaderAction.Convert, readerAction(EXAMPLE_TEXT))
        assertEquals(ReaderAction.Download, readerAction("Pancakes: flour, milk, egg. Whisk and fry."))
    }

    @Test fun `the example result keeps its notes methods and add-in`() {
        val recipe = stubConvert(EXAMPLE_TEXT, id = "u1")!!

        assertEquals(3, recipe.notes.size)
        assertEquals(listOf("oven", "air"), recipe.methods.single().options.map { it.value })
        assertEquals("v1", recipe.variations.single().id)
        assertEquals("add:v1", recipe.ingredients.single { it.id == "i9" }.ifCondition)
    }
}
