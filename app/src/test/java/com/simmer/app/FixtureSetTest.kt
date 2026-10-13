package com.simmer.app

import com.simmer.app.domain.canConvert
import com.simmer.app.domain.trimRecipeText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Seam: the fixture recipe texts (shared with the on-device `PipelineFixtureDeviceTest`) through
 * [trimRecipeText]. The model passes are not run here; that needs the Reader model on a phone.
 */
class FixtureSetTest {
    private val dir = File("src/androidTest/assets/fixtures")
    private val fixtures = dir.listFiles { f -> f.extension == "txt" }.orEmpty().sortedBy { it.name }

    @Test fun `there are at least 20 fixtures in English and German`() {
        assertTrue("found ${fixtures.size}", fixtures.size >= 20)
        assertTrue(fixtures.count { it.name.startsWith("en-") } >= 8)
        assertTrue(fixtures.count { it.name.startsWith("de-") } >= 8)
    }

    @Test fun `the set covers blog, terse, imperial, optional and appliance texts`() {
        val names = fixtures.joinToString(" ") { it.name }
        listOf("blog", "terse", "imperial", "optional", "appliance", "air-fryer", "sous-vide", "slow-cooker").forEach {
            assertTrue("no fixture named for $it", names.contains(it))
        }
    }

    @Test fun `every fixture survives trimming whole and long enough to convert`() {
        fixtures.forEach { f ->
            val out = trimRecipeText(f.readText())
            assertTrue("${f.name} too short", canConvert(out.text))
            assertFalse("${f.name} truncated", out.truncated)
        }
    }

    @Test fun `blog fixtures lose their story and comments but keep ingredients and method`() {
        fixtures.filter { "blog" in it.name }.forEach { f ->
            val raw = f.readText()
            val out = trimRecipeText(raw).text
            assertTrue("${f.name} not shorter", out.length < raw.length)
            assertFalse("${f.name} keeps comments", Regex("(?m)^(Comments|Kommentare|Bewertungen)").containsMatchIn(out))
            assertTrue("${f.name} lost its ingredients", Regex("(?m)^(Ingredients|Zutaten)").containsMatchIn(out))
            assertTrue("${f.name} lost its method", Regex("(?m)^(Method|Instructions|Zubereitung)").containsMatchIn(out))
        }
    }
}
