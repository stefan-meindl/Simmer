package com.simmer.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simmer.app.domain.DownloadProgress
import com.simmer.app.domain.ImportRequest
import com.simmer.app.domain.KitchenMode
import com.simmer.app.domain.MAX_RECIPE_CHARS
import com.simmer.app.domain.PipelineResult
import com.simmer.app.domain.ReaderStatus
import com.simmer.app.domain.RecipePipeline
import com.simmer.app.domain.RecipeReader
import com.simmer.app.domain.applianceOfOption
import com.simmer.app.domain.trimRecipeText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Pipeline's regex logic on the device's ICU engine (the JVM test uses java.util.regex):
 * [trimRecipeText] over every fixture, the `{id:text}` link handling and the appliance matching.
 */
@RunWith(AndroidJUnit4::class)
class PipelineRegexAndroidTest {

    private val assets = InstrumentationRegistry.getInstrumentation().context.assets
    private val fixtures = assets.list("fixtures").orEmpty().filter { it.endsWith(".txt") }.sorted()

    private fun read(name: String) = assets.open("fixtures/$name").bufferedReader().use { it.readText() }

    @Test fun everyFixtureTrimsWholeAndKeepsItsSections() {
        assertTrue(fixtures.size >= 20)
        fixtures.forEach { name ->
            val out = trimRecipeText(read(name))
            assertFalse("$name truncated", out.truncated)
            assertTrue("$name too long", out.text.length <= MAX_RECIPE_CHARS)
            assertTrue("$name lost its ingredients", Regex("(?i)ingredients|zutaten|\\d").containsMatchIn(out.text))
        }
    }

    @Test fun blogFixturesLoseStoryAndComments() {
        fixtures.filter { "blog" in it }.forEach { name ->
            val raw = read(name)
            val out = trimRecipeText(raw).text
            assertTrue("$name not shorter", out.length < raw.length)
            assertFalse("$name keeps comments", Regex("(?m)^(Comments|Kommentare|Bewertungen)").containsMatchIn(out))
        }
    }

    @Test fun applianceNamesMatchOnWordBoundaries() {
        assertEquals("steamoven", applianceOfOption("steam", "Steam oven"))
        assertEquals("oven", applianceOfOption("oven", "Oven"))
        assertEquals(null, applianceOfOption("stovetopper", "Microwavey"))
    }

    @Test fun existingLinkMarkupInAModelStepBodyIsNotLinkedTwice() = runBlocking {
        val replies = listOf(
            """{"ok":true,"title":"T","ingredients":[{"name":"Chicken thighs","q":8,"u":""}]}""",
            """{"methods":[],"steps":[{"ph":"cook","t":"Roast","b":"Roast the {i1:chicken thighs} until golden."}]}""",
            """{"timers":{},"notes":[]}""",
        )
        var n = 0
        val reader = object : RecipeReader {
            override suspend fun status() = ReaderStatus.Ready
            override fun download(): Flow<DownloadProgress> = flowOf(DownloadProgress.Complete)
            override suspend fun generate(system: String, user: String, maxTokens: Int) = flowOf(replies[n++])
        }

        val done = RecipePipeline(reader).run(ImportRequest("Roast chicken thighs for dinner tonight", "en", emptySet(), KitchenMode.Free), "u1", "n") {}
            as PipelineResult.Done

        assertEquals("Roast the {i1:chicken thighs} until golden.", done.recipe.steps.single().body)
    }
}
