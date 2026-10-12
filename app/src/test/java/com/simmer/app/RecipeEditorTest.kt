package com.simmer.app

import com.simmer.app.data.RecipeDraftStore
import com.simmer.app.data.RecipeEditor
import com.simmer.app.data.RecipeWriter
import com.simmer.app.data.ShoppingRepository
import com.simmer.app.domain.PlannedChoice
import com.simmer.app.domain.ShoppingEntry
import com.simmer.app.domain.model.Recipe
import com.simmer.app.domain.model.Selection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeRecipes(vararg initial: Recipe) : RecipeWriter {
    val rows = initial.associateBy { it.id }.toMutableMap()
    private val originals = initial.associateBy { it.id }
    override suspend fun get(id: String): Recipe? = rows[id]
    override suspend fun save(recipe: Recipe) { rows[recipe.id] = recipe }
    override suspend fun delete(id: String) { rows.remove(id) }
    override suspend fun restoreBuiltIn(id: String) { rows[id] = originals.getValue(id).copy(edited = false) }
}

/** Seam: [RecipeEditor], what Save, Restore original and Delete recipe do to stored state. */
class RecipeEditorTest {
    private val chilli = BuiltInRecipes.chilli()
    private val mine = BuiltInRecipes.curry().copy(id = "mine-1", mine = true)
    private val recipes = FakeRecipes(chilli, mine)
    private val shopping = ShoppingRepository(FakeShoppingDao())
    private val drafts = RecipeDraftStore()
    private val editor = RecipeEditor(recipes, shopping, drafts)

    private val choice = PlannedChoice(4, Selection())
    private fun entry(id: String) = ShoppingEntry(id, id, "1", "Produce", "x")

    @Test fun `saving an edited built-in marks it edited`() = runBlocking {
        editor.save(editor.open("chilli")!!.copy(title = "Hot chilli"))

        val saved = recipes.rows.getValue("chilli")
        assertEquals("Hot chilli", saved.title)
        assertTrue(saved.edited)
        assertFalse(saved.mine)
    }

    @Test fun `saving a user recipe replaces it in place without the edited mark`() = runBlocking {
        editor.save(editor.open("mine-1")!!.copy(title = "Better curry"))

        assertEquals("Better curry", recipes.rows.getValue("mine-1").title)
        assertFalse(recipes.rows.getValue("mine-1").edited)
        assertEquals(2, recipes.rows.size)
    }

    @Test fun `restore original removes the override`() = runBlocking {
        editor.save(editor.open("chilli")!!.copy(title = "Hot chilli"))

        editor.restoreOriginal("chilli")

        assertEquals("Chilli con carne", recipes.rows.getValue("chilli").title)
        assertFalse(recipes.rows.getValue("chilli").edited)
    }

    @Test fun `restore is offered for edited built-ins and delete for user recipes only`() = runBlocking {
        assertFalse(editor.open("chilli")!!.canRestore)
        editor.save(editor.open("chilli")!!)
        assertTrue(editor.open("chilli")!!.canRestore)
        assertFalse(editor.open("chilli")!!.canDelete)
        assertTrue(editor.open("mine-1")!!.canDelete)
        assertFalse(editor.open("mine-1")!!.canRestore)
    }

    @Test fun `a built-in recipe cannot be deleted`() = runBlocking {
        editor.delete("chilli")
        assertTrue(recipes.rows.containsKey("chilli"))
    }

    @Test fun `deleting a user recipe removes it, its Planned state and its shopping items`() = runBlocking {
        shopping.addRecipeToList("mine-1", listOf(entry("a")), choice)
        shopping.addRecipeToList("chilli", listOf(entry("b")), choice)

        editor.delete("mine-1")

        assertNull(recipes.rows["mine-1"])
        assertNull(shopping.getPlanned("mine-1"))
        assertEquals(listOf("chilli-b"), shopping.items.first().map { it.id })
        assertEquals(setOf("chilli"), shopping.planned.first().keys)
    }

    @Test fun `unknown recipe opens as nothing`() = runBlocking {
        assertNull(editor.open("nope"))
    }

    @Test fun `the Add recipe draft is edited in the draft store, not in stored recipes`() = runBlocking {
        drafts.set(BuiltInRecipes.shakshuka().copy(id = "draft", mine = true))
        val form = editor.open(null)!!
        assertFalse(form.canDelete)

        editor.save(form.copy(title = "Shak 2"))

        assertEquals("Shak 2", drafts.current.value!!.title)
        assertFalse(recipes.rows.containsKey("draft"))
    }

    @Test fun `no draft in the store opens as nothing`() = runBlocking {
        assertNull(editor.open(null))
    }
}
