package com.simmer.app

import com.simmer.app.domain.ImportStyle
import com.simmer.app.domain.KitchenMode
import com.simmer.app.domain.ReaderPromptTexts
import com.simmer.app.domain.readerPrompt
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plain stand-in texts, so these tests check the builder and not the wording. */
private val TEXTS = ReaderPromptTexts(
    intro = "INTRO",
    rules = "RULES",
    enrichment = "ENRICH",
    faithful = "STYLE-FAITHFUL",
    liberty = "STYLE-LIBERTY",
    schemaHeading = "SCHEMA-HEADING",
    exampleHeading = "EXAMPLE-HEADING",
    kitchenAny = "KITCHEN-ANY",
    kitchenOnly = "KITCHEN-ONLY %1\$s",
    kitchenNone = "NOBODY",
    kitchenStrict = "RULE-STRICT",
    kitchenMedium = "RULE-MEDIUM",
    recipeTextHeading = "RECIPE-TEXT",
    noRecipeText = "NO-TEXT",
)

private fun prompt(text: String = "", names: List<String> = listOf("Hob", "Oven"), mode: KitchenMode = KitchenMode.Medium, lang: String = "en", style: ImportStyle = ImportStyle.Faithful) =
    readerPrompt(TEXTS, text, names, mode, lang, style)

/** Seam: [readerPrompt], the Reader prompt for the chatbot path, from localised texts, the recipe text and the Kitchen. */
class ReaderPromptTest {

    @Test fun `Strict lists the appliances and adds the strict rule`() {
        val p = prompt(mode = KitchenMode.Strict)

        assertTrue(p.contains("KITCHEN-ONLY Hob, Oven"))
        assertTrue(p.contains("RULE-STRICT"))
        assertFalse(p.contains("RULE-MEDIUM"))
        assertFalse(p.contains("KITCHEN-ANY"))
    }

    @Test fun `Medium lists the appliances and adds the medium rule`() {
        val p = prompt(mode = KitchenMode.Medium)

        assertTrue(p.contains("KITCHEN-ONLY Hob, Oven"))
        assertTrue(p.contains("RULE-MEDIUM"))
        assertFalse(p.contains("RULE-STRICT"))
    }

    @Test fun `Free for all says any appliance and has no appliance rule`() {
        val p = prompt(mode = KitchenMode.Free)

        assertTrue(p.contains("KITCHEN-ANY"))
        assertFalse(p.contains("KITCHEN-ONLY"))
        assertFalse(p.contains("RULE-STRICT"))
        assertFalse(p.contains("RULE-MEDIUM"))
    }

    @Test fun `Faithful adds the shared rules and the faithful block only`() {
        val p = prompt(style = ImportStyle.Faithful)

        assertTrue(p.contains("ENRICH") && p.contains("STYLE-FAITHFUL"))
        assertFalse(p.contains("STYLE-LIBERTY"))
    }

    @Test fun `Liberty adds the shared rules and the liberty block only`() {
        val p = prompt(style = ImportStyle.Liberty)

        assertTrue(p.contains("ENRICH") && p.contains("STYLE-LIBERTY"))
        assertFalse(p.contains("STYLE-FAITHFUL"))
    }

    @Test fun `recipe text is included under its heading`() {
        val p = prompt(text = "Pancakes: flour, milk, egg.")

        assertTrue(p.contains("RECIPE-TEXT\nPancakes: flour, milk, egg."))
        assertFalse(p.contains("NO-TEXT"))
    }

    @Test fun `recipe text is trimmed`() {
        assertTrue(prompt(text = "  \n Soup \n\n").endsWith("RECIPE-TEXT\nSoup"))
    }

    @Test fun `an empty or blank field gives a complete prompt that points at the chatbot`() {
        listOf("", "   \n ").forEach { blank ->
            val p = prompt(text = blank)

            assertTrue(p.contains("NO-TEXT"))
            assertFalse(p.contains("RECIPE-TEXT"))
            assertTrue(p.contains("INTRO") && p.contains("RULES") && p.contains("KITCHEN-ONLY"))
        }
    }

    @Test fun `the prompt holds the schema and the example in the prompt language`() {
        val en = prompt(lang = "en")
        val de = prompt(lang = "de")

        assertTrue(en.contains("SCHEMA-HEADING\n{\"title\":"))
        assertTrue(en.contains("EXAMPLE-HEADING\n{\"title\":\"Sheet-pan lemon chicken\""))
        assertTrue(de.contains("EXAMPLE-HEADING\n{\"title\":\"Blech-Zitronenhähnchen\""))
    }

    @Test fun `no owned appliance reads none`() {
        assertTrue(prompt(names = emptyList(), mode = KitchenMode.Strict).contains("KITCHEN-ONLY NOBODY"))
    }
}
