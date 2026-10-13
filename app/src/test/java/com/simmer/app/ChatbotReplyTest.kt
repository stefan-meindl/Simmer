package com.simmer.app

import com.simmer.app.domain.KitchenMode
import com.simmer.app.domain.ReplyResult
import com.simmer.app.domain.readChatbotReply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun reply(
    title: String = "\"Pancakes\"",
    ingredients: String = """[{"id": "i1", "name": "Flour", "q": 200, "u": "g"}, {"id": "i2", "name": "Milk", "q": 300, "u": "ml"}]""",
    steps: String = """[
        {"id": "p1", "ph": "prep", "t": "Whisk", "b": "Whisk the {i1:flour} with the {i2:milk}."},
        {"id": "c1", "ph": "cook", "t": "Fry", "b": "Fry until golden.", "d": 300, "needs": ["p1"]}
    ]""",
): String = """{"title": $title, "summary": "Thin and easy.", "category": "Breakfast", "time": 20, "base": 4,
  "ingredients": $ingredients, "steps": $steps}"""

private val PANCAKES = reply()

private val CHICKEN = """{
  "title": "Chicken",
  "ingredients": [{"id": "i1", "name": "Chicken", "q": 8}],
  "methods": [{"id": "m1", "label": "The chicken", "q": "How?", "options": [
    {"v": "oven", "l": "Oven"}, {"v": "air", "l": "Air fryer"}, {"v": "hob", "l": "Hob"}]}],
  "steps": [
    {"id": "p1", "ph": "prep", "t": "Season", "b": "Season it."},
    {"id": "c1", "ph": "cook", "t": "Roast", "b": "Roast it.", "if": "m1=oven"},
    {"id": "c1", "ph": "cook", "t": "Air-fry", "b": "Air-fry it.", "if": "m1=air"},
    {"id": "c1", "ph": "cook", "t": "Pan-fry", "b": "Pan-fry it.", "if": "m1=hob"}
  ]
}"""

private fun parse(text: String, owned: Set<String> = emptySet(), mode: KitchenMode = KitchenMode.Free) =
    (readChatbotReply(text, id = "u-new", appliances = owned, kitchenMode = mode) as? ReplyResult.Done)?.recipe

/** Seam: [readChatbotReply], a pasted chatbot reply into a new User Recipe (or null when it cannot be one). */
class ChatbotReplyTest {

    @Test fun `plain JSON becomes a User Recipe with the given id`() {
        val recipe = parse(PANCAKES)!!

        assertEquals("u-new", recipe.id)
        assertTrue(recipe.mine)
        assertEquals("Pancakes", recipe.title)
        assertEquals(listOf("Flour", "Milk"), recipe.ingredients.map { it.name })
        assertEquals(listOf("p1", "c1"), recipe.steps.map { it.id })
        assertEquals(300, recipe.steps[1].durationSecs)
        assertEquals("Whisk the {i1:flour} with the {i2:milk}.", recipe.steps[0].body)
    }

    @Test fun `a code fence around the JSON is ignored`() {
        assertEquals("Pancakes", parse("```json\n$PANCAKES\n```")!!.title)
    }

    @Test fun `chatter before and after the JSON is ignored`() {
        assertEquals("Pancakes", parse("Sure! Here is your recipe:\n\n$PANCAKES\n\nEnjoy {your} meal!")!!.title)
    }

    @Test fun `text with no JSON gives no recipe`() {
        assertNull(parse("I could not find a recipe in that, sorry."))
    }

    @Test fun `cut-off JSON gives no recipe`() {
        assertNull(parse(PANCAKES.dropLast(40)))
    }

    @Test fun `a reply with no title gives no recipe`() {
        assertNull(parse(reply(title = "\"  \"")))
        assertNull(parse(reply(title = "null")))
    }

    @Test fun `a reply with no ingredient gives no recipe`() {
        assertNull(parse(reply(ingredients = "[]")))
    }

    @Test fun `a reply with no step gives no recipe`() {
        assertNull(parse(reply(steps = "[]")))
    }

    @Test fun `Free for all keeps every option`() {
        val recipe = parse(CHICKEN)!!

        assertEquals(3, recipe.methods.single().options.size)
        assertEquals(4, recipe.steps.size)
    }

    @Test fun `Strict drops options for missing appliances with their steps`() {
        val recipe = parse(CHICKEN, setOf("oven", "hob"), KitchenMode.Strict)!!

        assertEquals(listOf("Oven", "Hob"), recipe.methods.single().options.map { it.label })
        assertEquals(listOf("Season", "Roast", "Pan-fry"), recipe.steps.map { it.title })
    }

    @Test fun `Medium applies the same rule and a single option left is no choice`() {
        val recipe = parse(CHICKEN, setOf("oven"), KitchenMode.Medium)!!

        assertTrue(recipe.methods.isEmpty())
        assertEquals(listOf("Season", "Roast"), recipe.steps.map { it.title })
        assertNull(recipe.steps.last().ifCondition)
    }

    @Test fun `Medium drops options for missing appliances with their steps`() {
        val recipe = parse(CHICKEN, setOf("oven", "hob"), KitchenMode.Medium)!!

        assertEquals(listOf("Oven", "Hob"), recipe.methods.single().options.map { it.label })
        assertEquals(listOf("Season", "Roast", "Pan-fry"), recipe.steps.map { it.title })
    }

    @Test fun `a Method with options for no owned appliance stays as it is`() {
        val recipe = parse(CHICKEN, setOf("wok"), KitchenMode.Strict)!!

        assertEquals(3, recipe.methods.single().options.size)
        assertEquals(4, recipe.steps.size)
    }
}
