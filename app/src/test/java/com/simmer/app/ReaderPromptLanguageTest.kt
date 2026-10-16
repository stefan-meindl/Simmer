package com.simmer.app

import com.simmer.app.domain.KitchenMode
import com.simmer.app.domain.READER_PROMPT_SCHEMA
import com.simmer.app.domain.ReaderPromptTexts
import com.simmer.app.domain.mapsIn
import com.simmer.app.domain.ReplyResult
import com.simmer.app.domain.readChatbotReply
import com.simmer.app.domain.parseModelJson
import com.simmer.app.domain.readerPrompt
import com.simmer.app.domain.readerPromptExample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The `reader_prompt_*` strings as shipped in `res/values` (English) or `res/values-de`, unescaped the way aapt does. */
private fun resourceStrings(dir: String): Map<String, String> {
    val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$dir/strings.xml"))
    val nodes = doc.getElementsByTagName("string")
    return (0 until nodes.length).map { nodes.item(it) }
        .associate { it.attributes.getNamedItem("name").nodeValue to unescape(it.textContent) }
}

private fun unescape(raw: String): String = Regex("\\\\(.)").replace(raw) {
    when (val c = it.groupValues[1]) {
        "n" -> "\n"
        else -> c
    }
}

private fun texts(lang: String): ReaderPromptTexts {
    val s = resourceStrings(if (lang == "de") "values-de" else "values")
    fun get(name: String) = s["reader_prompt_$name"] ?: error("reader_prompt_$name missing for $lang")
    return ReaderPromptTexts(
        intro = get("intro"),
        rules = get("rules"),
        enrichment = get("enrichment"),
        faithful = get("faithful"),
        liberty = get("liberty"),
        schemaHeading = get("schema_heading"),
        exampleHeading = get("example_heading"),
        kitchenAny = get("kitchen_any"),
        kitchenOnly = get("kitchen_only"),
        kitchenNone = get("kitchen_none"),
        kitchenStrict = get("kitchen_strict"),
        kitchenMedium = get("kitchen_medium"),
        recipeTextHeading = get("recipe_text_heading"),
        noRecipeText = get("no_recipe_text"),
    )
}

private fun prompt(lang: String, mode: KitchenMode, text: String = "", names: List<String> = listOf("Hob", "Oven")) =
    readerPrompt(texts(lang), text, names, mode, lang)

private fun readRecipe(reply: String, id: String, appliances: Set<String>, mode: KitchenMode) =
    (readChatbotReply(reply, id, appliances, mode) as? ReplyResult.Done)?.recipe

/** Seam: the Reader prompt as built from the shipped English and German string resources. */
class ReaderPromptLanguageTest {

    @Test fun `English prompt is English for every Kitchen mode`() {
        KitchenMode.entries.forEach { mode ->
            val p = prompt("en", mode)

            assertTrue(p.contains("You turn a recipe into JSON"))
            assertFalse(p.contains("Rezept"))
        }
    }

    @Test fun `German prompt is German for every Kitchen mode`() {
        KitchenMode.entries.forEach { mode ->
            val p = prompt("de", mode)

            assertTrue(p.contains("Rezept in JSON"))
            assertFalse(p.contains("You turn a recipe"))
        }
    }

    @Test fun `every prompt holds the schema, the example and the Kitchen line`() {
        listOf("en", "de").forEach { lang ->
            KitchenMode.entries.forEach { mode ->
                val p = prompt(lang, mode)

                assertTrue(p.contains(READER_PROMPT_SCHEMA))
                assertTrue(p.contains(readerPromptExample(lang)))
                val kitchenLine = p.lines().first { it.startsWith(if (lang == "de") "Küche:" else "Kitchen:") }
                if (mode == KitchenMode.Free) {
                    assertTrue(kitchenLine, kitchenLine.contains(if (lang == "de") "beliebig" else "any appliance"))
                } else {
                    assertTrue(kitchenLine, kitchenLine.contains("Hob, Oven"))
                }
            }
        }
    }

    @Test fun `the appliance rule differs for Strict and Medium and is absent for Free for all`() {
        assertTrue(prompt("en", KitchenMode.Strict).contains("Be strict"))
        assertFalse(prompt("en", KitchenMode.Medium).contains("Be strict"))
        assertTrue(prompt("en", KitchenMode.Medium).contains("Common small tools"))
        assertFalse(prompt("en", KitchenMode.Free).contains("Never offer"))
        assertTrue(prompt("de", KitchenMode.Strict).contains("Sei streng"))
        assertFalse(prompt("de", KitchenMode.Medium).contains("Sei streng"))
        assertTrue(prompt("de", KitchenMode.Medium).contains("Kleine Hilfsmittel"))
        assertFalse(prompt("de", KitchenMode.Free).contains("Biete nie"))
    }

    @Test fun `recipe text is included and an empty field still gives a prompt`() {
        listOf("en", "de").forEach { lang ->
            assertTrue(prompt("$lang", KitchenMode.Medium, text = "Pancakes: flour, milk.").endsWith("Pancakes: flour, milk."))
            val empty = prompt(lang, KitchenMode.Medium)
            assertTrue(empty.contains(READER_PROMPT_SCHEMA))
            assertTrue(empty.contains(if (lang == "de") "Foto oder einen Link" else "photo or a link"))
        }
    }

    @Test fun `the schema in the prompt is valid JSON with English keys`() {
        val schema = parseModelJson(READER_PROMPT_SCHEMA)!!

        assertTrue(schema.keys.containsAll(listOf("title", "ingredients", "methods", "variations", "steps", "notes")))
    }

    @Test fun `every shipped string has an English and a German text`() {
        val en = resourceStrings("values").keys.filter { it.startsWith("reader_prompt_") }.toSet()
        val de = resourceStrings("values-de").keys.filter { it.startsWith("reader_prompt_") }.toSet()

        assertEquals(en, de)
        assertTrue(en.size >= 11)
    }

    @Test fun `every icon the example uses is allowed by the rules`() {
        listOf("en", "de").forEach { lang ->
            val rules = texts(lang).rules
            val example = parseModelJson(readerPromptExample(lang))!!
            val methodIcons = mapsIn(mapsIn(example["methods"]).single()["options"]).map { it["icon"] as String }

            methodIcons.forEach { assertTrue("$lang $it", rules.contains(it)) }
            assertTrue(rules.contains(example["icon"] as String))
        }
    }

    @Test fun `the rules say fields can be left out and steps of different options share an id`() {
        assertTrue(texts("en").rules.contains("Leave out any field"))
        assertTrue(texts("en").rules.contains("share an id"))
        assertTrue(texts("de").rules.contains("weglassen"))
        assertTrue(texts("de").rules.contains("dieselbe ID"))
    }

    @Test fun `the example is said to show the shape only, so the Kitchen line decides the methods`() {
        assertTrue(texts("en").exampleHeading.contains("shape only"))
        assertTrue(texts("de").exampleHeading.contains("nur die Form"))
    }

    @Test fun `German rules ask for German text values`() {
        assertTrue(texts("de").rules.contains("Schreibe jeden Textwert auf Deutsch"))
    }

    @Test fun `a reply holding the prompt's example parses with the paste-a-reply parser`() {
        listOf("en", "de").forEach { lang ->
            val reply = "Sure, here you go:\n```json\n${readerPromptExample(lang)}\n```\nEnjoy!"

            val recipe = readRecipe(reply, "u-new", emptySet(), KitchenMode.Free)!!

            assertEquals(if (lang == "de") "Blech-Zitronenhähnchen" else "Sheet-pan lemon chicken", recipe.title)
            assertEquals(5, recipe.ingredients.size)
            assertEquals(listOf("add", "suggest"), recipe.variations.map { it.kind })
            assertEquals(1, recipe.ingredients.single { it.name.startsWith("Baby") || it.name == "Drillinge" }.tips.size)
            assertEquals(listOf("oven", "air"), recipe.methods.single().options.map { it.value })
            assertEquals(3, recipe.steps.size)
        }
    }

    @Test fun `the example follows the Kitchen rule like any reply`() {
        val oven = readRecipe(readerPromptExample("en"), "u-new", setOf("oven"), KitchenMode.Strict)!!
        val both = readRecipe(readerPromptExample("en"), "u-new", setOf("oven", "airfryer"), KitchenMode.Medium)!!

        assertTrue(oven.methods.isEmpty())
        assertEquals(2, both.methods.single().options.size)
    }

    @Test fun `text that is not the example gives no recipe`() {
        assertNull(readRecipe(READER_PROMPT_SCHEMA, "u-new", emptySet(), KitchenMode.Free))
        assertNotNull(readRecipe(readerPromptExample("de"), "u-new", emptySet(), KitchenMode.Free))
    }

    // --- Sides ---

    @Test fun `the schema has a sides array holding a whole side`() {
        val side = mapsIn(parseModelJson(READER_PROMPT_SCHEMA)!!["sides"]).single()

        assertTrue(side.keys.containsAll(listOf("id", "label", "d", "ingredients", "methods", "steps")))
    }

    @Test fun `Liberty offers sides when the recipe is a main, with the limits and rice-like methods`() {
        val en = texts("en").liberty
        val de = texts("de").liberty

        listOf(en, de).forEach { assertTrue(it, it.contains("\"sides\"") && it.contains("3") && it.contains("6")) }
        assertTrue(en, en.contains("only a main") && en.contains("rice cooker") && en.contains("microwave"))
        assertTrue(de, de.contains("nur aus einem Hauptgericht") && de.contains("Reiskocher") && de.contains("Mikrowelle"))
    }

    @Test fun `Faithful keeps only sides the source lists and never invents`() {
        assertTrue(texts("en").faithful.contains("\"sides\""))
        assertTrue(texts("en").faithful.contains("do not invent"))
        assertTrue(texts("de").faithful.contains("\"sides\""))
        assertTrue(texts("de").faithful.contains("erfinde keine"))
    }

    @Test fun `the serves well with notes line is gone everywhere`() {
        listOf("en", "de").forEach { lang ->
            val all = listOf(texts(lang).liberty, readerPromptExample(lang), prompt(lang, KitchenMode.Free)).joinToString("\n")

            assertFalse(lang, all.contains("Serves well", ignoreCase = true) || all.contains("Passt gut zu"))
        }
    }

    @Test fun `the example has a rice side with two methods that follows the Kitchen rule`() {
        listOf("en", "de").forEach { lang ->
            val free = readRecipe(readerPromptExample(lang), "u-new", emptySet(), KitchenMode.Free)!!
            val side = free.sides.single()

            assertEquals("rice", side.id)
            assertEquals(2, side.methods.single().options.size)
            assertTrue(side.steps.any { it.phase == "prep" } && side.steps.any { it.phase == "cook" })
            assertTrue(side.ingredients.size <= 6)

        }
        val strict = readRecipe(readerPromptExample("en"), "u-new", setOf("oven"), KitchenMode.Strict)!!.sides.single()
        assertTrue(strict.methods.isEmpty())
        assertEquals(listOf("Rinse the rice", "Simmer the rice"), strict.steps.map { it.title })
    }
}
