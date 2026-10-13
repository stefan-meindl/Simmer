package com.simmer.app.pipeline

import com.simmer.app.domain.DownloadProgress
import com.simmer.app.domain.ReaderStatus
import com.simmer.app.domain.RecipeReader
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/**
 * THROWAWAY (issue 17 manual test, never merge). Stands in for the Gemma model: each pass takes
 * [PASS_MS] and returns canned JSON. Text containing "FAIL" gets garbage, so the error stage shows.
 * Text containing "BROKEN" gets one invalid reply per pass first, so the repair retry runs.
 */
class FakeScriptedReader : RecipeReader {
    private var brokenOnce = true

    override suspend fun status() = ReaderStatus.Ready
    override fun download(): Flow<DownloadProgress> = flowOf(DownloadProgress.Complete)

    override suspend fun generate(system: String, user: String, maxTokens: Int): Flow<String> = flow {
        delay(PASS_MS)
        val repair = system.startsWith("Return only valid JSON")
        val fail = user.contains("FAIL")
        val broken = user.contains("BROKEN") && !repair && brokenOnce.also { brokenOnce = !it }
        emit(
            when {
                fail || broken -> "I could not do that {"
                system.startsWith("You read pasted") || (repair && system.contains("\"ingredients\"")) -> PASS1
                system.startsWith("You split") || (repair && system.contains("\"steps\"")) -> PASS2
                system.startsWith("You link") || (repair && system.contains("\"needs\"")) -> """{"needs":{"c1":["p1"],"c2":["p2"]}}"""
                else -> """{"timers":{"c1":1500,"c2":900},"notes":["Roast time was a range; used the middle."]}"""
            },
        )
    }

    private companion object {
        const val PASS_MS = 6_000L
        const val PASS1 = """{"ok":true,"title":"Fake roast chicken","summary":"A scripted recipe for the manual test.","category":"Mains","tags":["One-pot"],"time":60,"difficulty":"Easy","base":4,"icon":"flame","ingredients":[{"name":"Chicken thighs","q":8,"u":"","aisle":"Meat"},{"name":"Baby potatoes","q":750,"u":"g","aisle":"Produce"},{"name":"Lemon","q":1,"u":"","aisle":"Produce"},{"name":"Chilli flakes","q":1,"u":"pinch","aisle":"Spices","if":"add:v1"}],"variations":[{"id":"v1","kind":"add","label":"Chilli flakes","d":"A gentle heat.","ing":"i4"}]}"""
        const val PASS2 = """{"methods":[],"notes":["Scripted by the fake reader."],"steps":[{"ph":"prep","t":"Halve the potatoes","b":"Halve the baby potatoes."},{"ph":"prep","t":"Cut the lemon","b":"Cut the lemon into quarters."},{"ph":"cook","t":"Roast the potatoes","b":"Roast the baby potatoes with the chicken thighs."},{"ph":"cook","t":"Finish","b":"Squeeze the lemon over the chicken thighs."}]}"""
    }
}
