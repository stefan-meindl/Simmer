package com.simmer.app

import com.simmer.app.data.AddRecipeSession
import com.simmer.app.data.RecipeDraftStore
import com.simmer.app.data.RecipeWriter
import com.simmer.app.domain.EXAMPLE_TEXT
import com.simmer.app.domain.KitchenMode
import com.simmer.app.domain.ReplyError
import com.simmer.app.domain.ReplyResult
import com.simmer.app.domain.model.Recipe
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class RecordingRecipes : RecipeWriter {
    val saved = mutableListOf<Recipe>()
    override suspend fun get(id: String): Recipe? = saved.lastOrNull { it.id == id }
    override suspend fun save(recipe: Recipe) { saved += recipe }
    override suspend fun delete(id: String) = Unit
    override suspend fun restoreBuiltIn(id: String) = Unit
}

/** Seam: [AddRecipeSession], Convert, discard and Save recipe against the draft store and recipe storage. */
class AddRecipeSessionTest {
    private val recipes = RecordingRecipes()
    private val drafts = RecipeDraftStore()
    private val session = AddRecipeSession(recipes, drafts)

    @Test fun `converting the example puts a new recipe in the draft store`() {
        assertTrue(session.convert(EXAMPLE_TEXT, "u-new"))

        assertEquals("u-new", drafts.current.value!!.id)
        assertTrue(recipes.saved.isEmpty())
    }

    @Test fun `converting other text leaves no draft`() {
        assertFalse(session.convert("Pancakes: flour, milk, egg. Whisk and fry.", "u-new"))

        assertNull(drafts.current.value)
    }

    @Test fun `a valid chatbot reply puts a new recipe in the draft store`() {
        val reply = """```json
            {"title": "Toast", "ingredients": [{"name": "Bread", "q": 2}], "steps": [{"b": "Toast it.", "ph": "cook"}]}
            ```"""

        assertNull(session.addFromReply(reply, "u-new", emptySet(), KitchenMode.Free))

        assertEquals("Toast", drafts.current.value!!.title)
        assertEquals("u-new", drafts.current.value!!.id)
        assertTrue(recipes.saved.isEmpty())
    }

    @Test fun `an unusable chatbot reply leaves no draft and says why`() {
        assertEquals(ReplyResult.Failed(ReplyError.NoIngredient), session.addFromReply("""{"title": "Toast"}""", "u-new", emptySet(), KitchenMode.Free))

        assertNull(drafts.current.value)
    }

    @Test fun `a corrected reply after a failed one puts the recipe in the draft store`() {
        assertEquals(ReplyResult.Failed(ReplyError.CutOff, "0"), session.addFromReply("""{"title": "Toast", "ingredi""", "u-new", emptySet(), KitchenMode.Free))

        val fixed = """{"title": "Toast", "ingredients": [{"name": "Bread", "q": 2}], "steps": [{"b": "Toast it.", "ph": "cook"}]}"""
        assertNull(session.addFromReply(fixed, "u-new", emptySet(), KitchenMode.Free))

        assertEquals("Toast", drafts.current.value!!.title)
    }

    @Test fun `discard drops the draft`() {
        session.convert(EXAMPLE_TEXT, "u-new")

        session.discard()

        assertNull(drafts.current.value)
    }

    @Test fun `converting again replaces the earlier draft`() {
        session.convert(EXAMPLE_TEXT, "u-1")
        session.convert(EXAMPLE_TEXT, "u-2")

        assertEquals("u-2", drafts.current.value!!.id)
    }

    @Test fun `Save stores the draft as a user recipe and clears it`() = runBlocking {
        session.convert(EXAMPLE_TEXT, "u-new")

        val saved = session.save()!!

        assertEquals("u-new", saved.id)
        assertEquals(listOf(saved), recipes.saved)
        assertTrue(saved.mine)
        assertFalse(saved.edited)
        assertNull(drafts.current.value)
    }

    @Test fun `Save keeps edits made to the draft`() = runBlocking {
        session.convert(EXAMPLE_TEXT, "u-new")
        drafts.set(drafts.current.value!!.copy(title = "Sunday chicken"))

        assertEquals("Sunday chicken", session.save()!!.title)
        assertEquals("Sunday chicken", recipes.saved.single().title)
    }

    @Test fun `Save from a shared Reel keeps its link as the source url`() = runBlocking {
        session.convert(EXAMPLE_TEXT, "u-new")

        val saved = session.save(sourceUrl = "https://www.instagram.com/reel/CHOJBJ6pcSx/")!!

        assertEquals("https://www.instagram.com/reel/CHOJBJ6pcSx/", saved.sourceUrl)
        assertEquals(saved, recipes.saved.single())
    }

    @Test fun `Save without a Reel leaves the draft's own source url`() = runBlocking {
        session.convert(EXAMPLE_TEXT, "u-new")
        drafts.set(drafts.current.value!!.copy(sourceUrl = "https://example.com/r"))

        assertEquals("https://example.com/r", session.save()!!.sourceUrl)
    }

    @Test fun `Save with no draft stores nothing`() = runBlocking {
        assertNull(session.save())
        assertTrue(recipes.saved.isEmpty())
    }
}
