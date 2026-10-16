package com.simmer.app

import com.simmer.app.domain.DownloadProgress
import com.simmer.app.domain.PipelineResult
import com.simmer.app.domain.PipelineStage
import com.simmer.app.domain.RecipePipeline
import com.simmer.app.domain.RecipeReader
import com.simmer.app.domain.ReaderStatus
import com.simmer.app.domain.ImportRequest
import com.simmer.app.domain.ImportStyle
import com.simmer.app.domain.KitchenMode
import com.simmer.app.domain.PassTimeoutException
import com.simmer.app.domain.REPAIR_BROKEN_CHARS
import com.simmer.app.domain.REPAIR_TEXT_CHARS
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private data class Call(val system: String, val user: String, val maxTokens: Int)

/** Replies in order; one reply is split into several tokens like a stream. */
private class ScriptedReader(private val replies: List<String>) : RecipeReader {
    val calls = mutableListOf<Call>()
    override suspend fun status() = ReaderStatus.Ready
    override fun download(): Flow<DownloadProgress> = flowOf(DownloadProgress.Complete)
    override suspend fun generate(system: String, user: String, maxTokens: Int): Flow<String> {
        calls += Call(system, user, maxTokens)
        val reply = replies[calls.size - 1]
        return flowOf(*reply.chunked(7).toTypedArray())
    }
}

private val PASS1 = """
{"ok":true,"title":"Lemon chicken","summary":"Crisp chicken with potatoes.","category":"Mains","tags":["One-pot"],
 "time":60,"difficulty":"Easy","base":4,"icon":"flame",
 "ingredients":[
  {"id":"x","name":"Chicken thighs","q":8,"u":"","aisle":"Meat"},
  {"id":"x","name":"Baby potatoes","q":750,"u":"g","aisle":"Produce"},
  {"id":"x","name":"Chilli flakes","q":1,"u":"pinch","aisle":"Spices","if":"add:v1"}],
 "variations":[{"id":"v1","kind":"add","label":"Chilli flakes","d":"A gentle heat.","ing":"i3"}]}
"""

private val PASS2 = """
{"methods":[],"steps":[
 {"ph":"prep","t":"Halve the potatoes","b":"Halve the baby potatoes."},
 {"ph":"cook","t":"Roast","b":"Roast the chicken thighs with the potatoes."}]}
"""

private val PASS3 = """{"needs":{"c1":["p1","p9"]}}"""
private val PASS4 = """{"timers":{"c1":2280},"notes":["Roast time was 35-40 min; used 38."]}"""

private const val NOTE = "This recipe is long. Only the first part was read."

private fun run(
    reader: RecipeReader,
    text: String = "Lemon chicken. 8 chicken thighs. Roast it.",
    appliances: Set<String> = emptySet(),
    mode: KitchenMode = KitchenMode.Free,
    lang: String = "en",
    stages: MutableList<PipelineStage> = mutableListOf(),
    style: ImportStyle = ImportStyle.Faithful,
) = runBlocking { RecipePipeline(reader).run(ImportRequest(text, lang, appliances, mode, style), "u1", NOTE) { stages += it } }

/** Seam: [RecipePipeline.run] against a scripted [RecipeReader]. */
class RecipePipelineTest {

    @Test fun `Faithful asks for tips and detail but no suggestions and keeps the token budget`() {
        val reader = ScriptedReader(listOf(PASS1, PASS2, PASS3, PASS4))

        run(reader, style = ImportStyle.Faithful)

        assertTrue(reader.calls[0].system.contains("\"tips\""))
        assertTrue(reader.calls[1].system.contains("Expand vague steps"))
        assertFalse(reader.calls.any { it.system.contains("suggest") })
        assertEquals(2_000, reader.calls[0].maxTokens)
    }

    @Test fun `Liberty asks for suggestions and methods and raises the budget of the big passes`() {
        val reader = ScriptedReader(listOf(PASS1, PASS2, PASS3, PASS4))

        run(reader, style = ImportStyle.Liberty)

        assertTrue(reader.calls[0].system.contains("\"suggest\""))
        assertTrue(reader.calls[1].system.contains("even if the text does not list it"))
        assertEquals(3_000, reader.calls[0].maxTokens)
        assertEquals(3_000, reader.calls[1].maxTokens)
        assertEquals(1_200, reader.calls[2].maxTokens)
    }

    @Test fun `four passes build a normalized recipe and report each stage in order`() {
        val reader = ScriptedReader(listOf(PASS1, PASS2, PASS3, PASS4))
        val stages = mutableListOf<PipelineStage>()

        val done = run(reader, stages = stages) as PipelineResult.Done
        val recipe = done.recipe

        assertEquals(listOf(PipelineStage.Ingredients, PipelineStage.Splitting, PipelineStage.Linking, PipelineStage.Timers), stages)
        assertEquals(4, reader.calls.size)
        assertEquals(0, done.repairs)
        assertEquals("u1", recipe.id)
        assertTrue(recipe.mine)
        assertEquals(listOf("i1", "i2", "i3"), recipe.ingredients.map { it.id })
        assertEquals("add:v1", recipe.ingredients[2].ifCondition)
        assertEquals(listOf("v1"), recipe.variations.map { it.id })
        assertEquals(listOf("p1", "c1"), recipe.steps.map { it.id })
        // Pass 3: links come from link(), not the model; needs keep only real prep ids.
        assertEquals("Halve the {i2:baby potatoes}.", recipe.steps[0].body)
        assertEquals("Roast the {i1:chicken thighs} with the {i2:potatoes}.", recipe.steps[1].body)
        assertEquals(listOf("p1"), recipe.steps[1].needs)
        // Pass 4: a timer where the model gave one, none where it did not.
        assertEquals(0, recipe.steps[0].durationSecs)
        assertEquals(2280, recipe.steps[1].durationSecs)
        assertEquals(listOf("Roast time was 35-40 min; used 38."), recipe.notes)
    }

    @Test fun `every pass asks for between 1200 and 2000 tokens`() {
        val reader = ScriptedReader(listOf(PASS1, "oops", PASS2, PASS3, PASS4))

        run(reader)

        assertTrue(reader.calls.all { it.maxTokens in 1200..2000 })
    }

    @Test fun `later passes get the recipe text and what earlier passes produced`() {
        val reader = ScriptedReader(listOf(PASS1, PASS2, PASS3, PASS4))

        run(reader, text = "Lemon chicken. 8 chicken thighs. Roast it.")

        assertTrue(reader.calls[0].user.contains("8 chicken thighs"))
        assertTrue(reader.calls[1].user.contains("Chicken thighs"))
        assertTrue(reader.calls[1].user.contains("Roast it."))
        assertTrue(reader.calls[3].user.contains("Roast the {i1:chicken thighs}"))
    }

    @Test fun `the kitchen rule goes to the methods pass and German to every pass`() {
        val reader = ScriptedReader(listOf(PASS1, PASS2, PASS3, PASS4))

        run(reader, appliances = setOf("oven"), mode = KitchenMode.Medium, lang = "de")

        assertTrue(reader.calls[1].system.contains("The cook owns these appliances: Oven."))
        assertTrue(reader.calls.all { it.system.contains("German") })
    }

    @Test fun `invalid JSON gets one repair prompt carrying the broken output`() {
        val broken = """{"methods":[],"steps":[{"ph":"prep","t":"Halve","b":"Halve the baby pot"""
        val reader = ScriptedReader(listOf(PASS1, broken, PASS2, PASS3, PASS4))

        val done = run(reader) as PipelineResult.Done

        assertEquals(5, reader.calls.size)
        assertEquals(1, done.repairs)
        assertTrue(reader.calls[2].system.contains("Return only valid JSON matching this shape"))
        assertTrue(reader.calls[2].user.contains(broken))
    }

    @Test fun `a second invalid reply fails at that stage`() {
        val reader = ScriptedReader(listOf(PASS1, "no json", "still no json"))

        val result = run(reader)

        assertEquals(PipelineResult.Failed(PipelineStage.Splitting), result)
        assertEquals(3, reader.calls.size)
    }

    @Test fun `a reply that is JSON but has no steps counts as invalid`() {
        val reader = ScriptedReader(listOf(PASS1, """{"methods":[],"steps":[]}""", """{"steps":[]}"""))

        assertEquals(PipelineResult.Failed(PipelineStage.Splitting), run(reader))
    }

    @Test fun `a text that is not a recipe fails at once without a repair`() {
        val reader = ScriptedReader(listOf("""{"ok":false,"reason":"A shopping list."}"""))

        assertEquals(PipelineResult.Failed(PipelineStage.Ingredients), run(reader))
        assertEquals(1, reader.calls.size)
    }

    @Test fun `without prep steps the needs pass is skipped`() {
        val cookOnly = """{"methods":[],"steps":[{"ph":"cook","t":"Roast","b":"Roast the chicken thighs."}]}"""
        val reader = ScriptedReader(listOf(PASS1, cookOnly, PASS4))

        val done = run(reader) as PipelineResult.Done

        assertEquals(3, reader.calls.size)
        assertEquals(listOf("c1"), done.recipe.steps.map { it.id })
    }

    @Test fun `text cut by trimming adds the long-recipe note first`() {
        val reader = ScriptedReader(listOf(PASS1, PASS2, PASS3, PASS4))
        val long = "Lemon chicken\nIngredients\n- 8 chicken thighs\nMethod\n" + "Roast it for a good while.\n".repeat(400)

        val done = run(reader, text = long) as PipelineResult.Done

        assertEquals(NOTE, done.recipe.notes.first())
        assertTrue(reader.calls[0].user.length < 7_000)
    }

    @Test fun `cancelling stops the run at the current pass`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val calls = mutableListOf<Int>()
        val hanging = object : RecipeReader {
            override suspend fun status() = ReaderStatus.Ready
            override fun download(): Flow<DownloadProgress> = flowOf(DownloadProgress.Complete)
            override suspend fun generate(system: String, user: String, maxTokens: Int): Flow<String> = flow {
                calls += 1
                started.complete(Unit)
                CompletableDeferred<Unit>().await()
            }
        }
        val job = async(start = CoroutineStart.UNDISPATCHED) { RecipePipeline(hanging).run(ImportRequest("text", "en", emptySet(), KitchenMode.Free), "u1", NOTE) {} }
        started.await()
        yield()

        job.cancel()

        val outcome = runCatching { job.await() }.exceptionOrNull()
        assertTrue(outcome is CancellationException)
        assertEquals(listOf(1), calls)
    }

    @Test fun `a repair prompt carries a bounded slice of the broken reply and the text`() {
        val hugeBroken = """{"methods":[],"steps":[""" + "x".repeat(20_000)
        val reader = ScriptedReader(listOf(PASS1, hugeBroken, PASS2, PASS3, PASS4))

        run(reader, text = "Lemon chicken\nIngredients\n- 8 chicken thighs\nMethod\n" + "Roast it for a good while.\n".repeat(200))

        val repair = reader.calls[2].user
        assertTrue(repair.length < REPAIR_BROKEN_CHARS + REPAIR_TEXT_CHARS + 100)
        assertTrue(repair.contains(hugeBroken.take(REPAIR_BROKEN_CHARS)))
    }

    @Test fun `a step with an unknown phase makes the reply invalid and triggers the repair`() {
        val badPhase = """{"methods":[],"steps":[{"ph":"serve","t":"Plate","b":"Plate the chicken thighs."}]}"""
        val reader = ScriptedReader(listOf(PASS1, badPhase, PASS2, PASS3, PASS4))

        val done = run(reader) as PipelineResult.Done

        assertEquals(1, done.repairs)
        assertEquals(listOf("p1", "c1"), done.recipe.steps.map { it.id })
    }

    @Test fun `timer seconds are rounded not truncated`() {
        val reader = ScriptedReader(listOf(PASS1, PASS2, PASS3, """{"timers":{"c1":899.6},"notes":[]}"""))

        val done = run(reader) as PipelineResult.Done

        assertEquals(900, done.recipe.steps[1].durationSecs)
    }

    @Test fun `methods for appliances the cook lacks are filtered in code, not only asked for in the prompt`() {
        val withMethods = """{"methods":[{"id":"m1","label":"The chicken","q":"How?","options":[
            {"v":"oven","l":"Oven"},{"v":"air","l":"Air fryer"},{"v":"hob","l":"Hob"}]}],
            "steps":[{"ph":"prep","t":"Halve","b":"Halve the baby potatoes."},
            {"ph":"cook","t":"Roast","b":"Roast the chicken thighs.","if":"m1=oven"},
            {"ph":"cook","t":"Air-fry","b":"Air-fry the chicken thighs.","if":"m1=air"},
            {"ph":"cook","t":"Pan-fry","b":"Pan-fry the chicken thighs.","if":"m1=hob"}]}"""
        val reader = ScriptedReader(listOf(PASS1, withMethods, PASS3, PASS4))

        val done = run(reader, appliances = setOf("oven", "hob"), mode = KitchenMode.Strict) as PipelineResult.Done

        assertEquals(listOf("oven", "hob"), done.recipe.methods.single().options.map { it.value })
        assertEquals(listOf("Roast", "Pan-fry"), done.recipe.steps.filter { it.phase == "cook" }.map { it.title })
    }

    @Test fun `Free for all keeps every method option`() {
        val withMethods = """{"methods":[{"id":"m1","label":"The chicken","q":"How?","options":[
            {"v":"oven","l":"Oven"},{"v":"air","l":"Air fryer"}]}],
            "steps":[{"ph":"cook","t":"Roast","b":"Roast it.","if":"m1=oven"},{"ph":"cook","t":"Air-fry","b":"Air-fry it.","if":"m1=air"}]}"""
        val reader = ScriptedReader(listOf(PASS1, withMethods, PASS4))

        val done = run(reader, appliances = setOf("hob"), mode = KitchenMode.Free) as PipelineResult.Done

        assertEquals(2, done.recipe.methods.single().options.size)
    }

    @Test fun `a model that stalls fails the pass instead of waiting for ever`() {
        val stalling = object : RecipeReader {
            override suspend fun status() = ReaderStatus.Ready
            override fun download(): Flow<DownloadProgress> = flowOf(DownloadProgress.Complete)
            override suspend fun generate(system: String, user: String, maxTokens: Int): Flow<String> = flow {
                CompletableDeferred<Unit>().await()
            }
        }

        val outcome = runCatching {
            runBlocking { RecipePipeline(stalling, passTimeoutMs = 50).run(ImportRequest("text", "en", emptySet(), KitchenMode.Free), "u1", NOTE) {} }
        }.exceptionOrNull()

        assertTrue(outcome is PassTimeoutException)
        assertEquals(PipelineStage.Ingredients, (outcome as PassTimeoutException).stage)
    }
}
