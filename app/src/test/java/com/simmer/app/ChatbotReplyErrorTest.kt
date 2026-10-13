package com.simmer.app

import com.simmer.app.domain.KitchenMode
import com.simmer.app.domain.ReplyError
import com.simmer.app.domain.ReplyResult
import com.simmer.app.domain.readChatbotReply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ING = """[{"id": "i1", "name": "Flour", "q": 200, "u": "g"}]"""
private const val STEPS = """[{"id": "p1", "ph": "prep", "t": "Whisk", "b": "Whisk the {i1:flour}."}]"""

private fun read(text: String) = readChatbotReply(text, "u-new", emptySet(), KitchenMode.Free)

private fun errorOf(text: String): ReplyError = (read(text) as ReplyResult.Failed).error

/** Seam: [readChatbotReply], a pasted chatbot reply into a recipe or the kind of failure. */
class ChatbotReplyErrorTest {

    @Test fun `a usable reply gives the recipe`() {
        val result = read("""{"title": "Pancakes", "ingredients": $ING, "steps": $STEPS}""")

        assertEquals("Pancakes", (result as ReplyResult.Done).recipe.title)
    }

    @Test fun `text without a JSON object is no JSON`() {
        assertEquals(ReplyError.NoJson, errorOf("Sorry, I cannot help with that."))
        assertEquals(ReplyError.NoJson, errorOf(""))
    }

    @Test fun `JSON that never closes is cut off`() {
        assertEquals(
            ReplyError.CutOff,
            errorOf("""```json
            {"title": "Pancakes", "ingredients": $ING, "steps": [{"id": "p1", "ph": "prep", "t": "Whisk", "b": "Whi"""),
        )
    }

    @Test fun `a closed but broken object is not valid JSON`() {
        assertEquals(ReplyError.NotValid, errorOf("""{"title": "Pancakes", "ingredients": }"""))
    }

    @Test fun `a missing or blank title is named`() {
        assertEquals(ReplyError.NoTitle, errorOf("""{"ingredients": $ING, "steps": $STEPS}"""))
        assertEquals(ReplyError.NoTitle, errorOf("""{"title": "  ", "ingredients": $ING, "steps": $STEPS}"""))
    }

    @Test fun `no Ingredient is named`() {
        assertEquals(ReplyError.NoIngredient, errorOf("""{"title": "X", "ingredients": [], "steps": $STEPS}"""))
    }

    @Test fun `no Step is named`() {
        assertEquals(ReplyError.NoStep, errorOf("""{"title": "X", "ingredients": $ING, "steps": []}"""))
    }

    @Test fun `the first problem wins when several are missing`() {
        assertEquals(ReplyError.NoTitle, errorOf("""{"ingredients": [], "steps": []}"""))
        assertTrue(read("""{"title": "X"}""") is ReplyResult.Failed)
    }
}
