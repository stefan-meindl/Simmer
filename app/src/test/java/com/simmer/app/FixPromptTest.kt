package com.simmer.app

import com.simmer.app.domain.FixPromptTexts
import com.simmer.app.domain.READER_PROMPT_SCHEMA
import com.simmer.app.domain.ReplyError
import com.simmer.app.domain.fixPrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The `fix_prompt_*` strings as shipped in `res/values` (English) or `res/values-de`, unescaped the way aapt does. */
private fun resourceStrings(dir: String): Map<String, String> {
    val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$dir/strings.xml"))
    val nodes = doc.getElementsByTagName("string")
    return (0 until nodes.length).map { nodes.item(it) }.associate {
        it.attributes.getNamedItem("name").nodeValue to
            Regex("\\\\(.)").replace(it.textContent) { m -> if (m.groupValues[1] == "n") "\n" else m.groupValues[1] }
    }
}

private val KEYS = mapOf(
    ReplyError.NoJson to "no_json",
    ReplyError.CutOff to "cut_off",
    ReplyError.NotValid to "not_valid",
    ReplyError.NoTitle to "no_title",
    ReplyError.NoIngredient to "no_ingredient",
    ReplyError.NoStep to "no_step",
)

private fun texts(lang: String): FixPromptTexts {
    val s = resourceStrings(if (lang == "de") "values-de" else "values")
    fun get(name: String) = s["fix_prompt_$name"] ?: error("fix_prompt_$name missing for $lang")
    return FixPromptTexts(get("instruction"), ReplyError.entries.associateWith { get("error_" + KEYS.getValue(it)) })
}

private fun fix(lang: String, error: ReplyError) = fixPrompt(texts(lang), error)

/** Seam: the Fix prompt as built from the shipped English and German string resources. */
class FixPromptTest {

    @Test fun `every Fix prompt names its error and asks for only valid JSON in the shape`() {
        listOf("en", "de").forEach { lang ->
            ReplyError.entries.forEach { error ->
                val p = fix(lang, error)

                assertTrue("$lang $error", p.contains(texts(lang).errors.getValue(error)))
                assertTrue("$lang $error", p.contains(READER_PROMPT_SCHEMA))
                assertTrue("$lang $error", p.contains(if (lang == "de") "Gib nur gültiges JSON" else "Return only valid JSON matching this shape"))
            }
        }
    }

    @Test fun `each failure kind gives a different Fix prompt`() {
        listOf("en", "de").forEach { lang ->
            assertEquals(ReplyError.entries.size, ReplyError.entries.map { fix(lang, it) }.toSet().size)
        }
    }

    @Test fun `the error names what is wrong`() {
        assertTrue(fix("en", ReplyError.NoJson).contains("no JSON"))
        assertTrue(fix("en", ReplyError.CutOff).contains("cut off"))
        assertTrue(fix("en", ReplyError.NoTitle).contains("\"title\""))
        assertTrue(fix("en", ReplyError.NoIngredient).contains("\"ingredients\""))
        assertTrue(fix("en", ReplyError.NoStep).contains("\"steps\""))
        assertTrue(fix("de", ReplyError.CutOff).contains("abgeschnitten"))
        assertTrue(fix("de", ReplyError.NoStep).contains("\"steps\""))
    }

    @Test fun `German Fix prompts are German and English ones are English`() {
        ReplyError.entries.forEach {
            assertFalse(fix("de", it).contains("Return only"))
            assertFalse(fix("en", it).contains("Gib nur"))
        }
    }

    @Test fun `every shipped string has an English and a German text`() {
        val en = resourceStrings("values").keys.filter { it.startsWith("fix_prompt_") }.toSet()
        val de = resourceStrings("values-de").keys.filter { it.startsWith("fix_prompt_") }.toSet()

        assertEquals(en, de)
        assertEquals(7, en.size)
    }
}
