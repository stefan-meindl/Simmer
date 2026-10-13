package com.simmer.app

import com.simmer.app.domain.MAX_RECIPE_CHARS
import com.simmer.app.domain.trimRecipeText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Seam: [trimRecipeText], what pass 1 gets to read. */
class TrimRecipeTextTest {

    @Test fun `repeated blank lines and spaces collapse`() {
        val out = trimRecipeText("Soup\n\n\n\nIngredients\n- 1   onion\t\t\n\n\n\nMethod\nBoil.")

        assertEquals("Soup\n\nIngredients\n- 1 onion\n\nMethod\nBoil.", out.text)
        assertFalse(out.truncated)
    }

    @Test fun `a long blog preamble before the ingredients is dropped but title and servings stay`() {
        val story = "Last summer my grandmother and I spent every Sunday in her kitchen talking about nothing. ".repeat(8)
        val out = trimRecipeText("Grandma's Soup\n\n$story\n\nServes 4\n\nIngredients\n- 1 onion\n\nMethod\nBoil the onion.")

        assertTrue(out.text.startsWith("Grandma's Soup"))
        assertTrue(out.text.contains("Serves 4"))
        assertTrue(out.text.contains("Ingredients\n- 1 onion"))
        assertFalse(out.text.contains("grandmother"))
    }

    @Test fun `a short header before the ingredients is kept`() {
        val out = trimRecipeText("Pancakes\nMakes 8, 20 min\n\nIngredients\n- 2 eggs\n\nMethod\nWhisk and fry.")

        assertTrue(out.text.contains("Makes 8, 20 min"))
    }

    @Test fun `German headings are recognised too`() {
        val story = "Meine Oma hat dieses Rezept schon immer am Sonntag gemacht und wir lieben es sehr. ".repeat(8)
        val out = trimRecipeText("Kartoffelsuppe\n\n$story\n\nZutaten\n- 1 Zwiebel\n\nZubereitung\nKochen.")

        assertFalse(out.text.contains("Oma"))
        assertTrue(out.text.contains("Zutaten\n- 1 Zwiebel"))
    }

    @Test fun `comments and what follows are cut`() {
        val out = trimRecipeText("Soup\nIngredients\n- 1 onion\nMethod\nBoil.\n\nComments (12)\nSarah: loved it!\nTom: too salty")

        assertEquals("Soup\nIngredients\n- 1 onion\nMethod\nBoil.", out.text)
    }

    @Test fun `German comments heading is cut`() {
        val out = trimRecipeText("Suppe\nZutaten\n- 1 Zwiebel\nZubereitung\nKochen.\nKommentare\nLecker!")

        assertFalse(out.text.contains("Lecker"))
    }

    @Test fun `text without sections is only whitespace-cleaned`() {
        val out = trimRecipeText("  Mix 200g flour with 2 eggs and fry in butter.  ")

        assertEquals("Mix 200g flour with 2 eggs and fry in butter.", out.text)
    }

    @Test fun `text over the cap is cut at a line break and flagged`() {
        val line = "Add 1 cup of something nice and stir well for a while.\n"
        val out = trimRecipeText("Ingredients\n- x\nMethod\n" + line.repeat(300))

        assertTrue(out.truncated)
        assertTrue(out.text.length <= MAX_RECIPE_CHARS)
        assertTrue(out.text.endsWith("while."))
    }

    @Test fun `text at the cap is not flagged`() {
        val out = trimRecipeText("a".repeat(MAX_RECIPE_CHARS))

        assertFalse(out.truncated)
        assertEquals(MAX_RECIPE_CHARS, out.text.length)
    }
}
