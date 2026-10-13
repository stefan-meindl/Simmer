package com.simmer.app

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simmer.app.data.PrefsRepository
import com.simmer.app.data.ReaderController
import com.simmer.app.domain.PipelineResult
import com.simmer.app.domain.ReaderStatus
import com.simmer.app.domain.RecipePipeline
import com.simmer.app.reader.AndroidThermalMonitor
import com.simmer.app.reader.GemmaReader
import com.simmer.app.reader.LiteRtEngineFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The on-device measurement for the Pipeline: every fixture text in `androidTest/assets/fixtures`
 * through the real Gemma model. Needs the Reader model downloaded in the app under test (the test
 * is skipped without it). Targets: at least 90% of recipes reach `normalize()` without a repair retry,
 * each in under 3 minutes. The per-recipe table is logged under the tag `PipelineFixtures` and written
 * to `files/pipeline-fixtures.txt`. See `docs/manual-testing.md`.
 */
@RunWith(AndroidJUnit4::class)
class PipelineFixtureDeviceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private class Row(val name: String, val outcome: String, val repairs: Int, val seconds: Long)

    @Test
    fun fixturesConvertWithoutRepairInUnderThreeMinutes(): Unit = runBlocking {
        val controller = ReaderController(context, PrefsRepository(context))
        assumeTrue("Reader model not downloaded on this device", controller.status.first() == ReaderStatus.Ready)
        val reader = GemmaReader(
            source = controller,
            factory = LiteRtEngineFactory(context),
            thermal = AndroidThermalMonitor(context),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            engineDispatcher = Dispatchers.IO,
        )
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val names = assets.list("fixtures").orEmpty().filter { it.endsWith(".txt") }.sorted()
        assertTrue("fixtures missing", names.size >= 20)

        val rows = names.map { name ->
            val text = assets.open("fixtures/$name").bufferedReader().use { it.readText() }
            val lang = if (name.startsWith("de-")) "de" else "en"
            val start = System.nanoTime()
            val result = RecipePipeline(reader).run(text, kitchenRule = "", lang = lang, id = "fx") {}
            val seconds = (System.nanoTime() - start) / 1_000_000_000
            when (result) {
                is PipelineResult.Done -> Row(name, "ok", result.repairs, seconds)
                is PipelineResult.Failed -> Row(name, "failed at ${result.stage}", 0, seconds)
            }
        }

        val clean = rows.count { it.outcome == "ok" && it.repairs == 0 }
        val slow = rows.filter { it.seconds >= 180 }
        val report = buildString {
            rows.forEach { appendLine("${it.name}\t${it.outcome}\trepairs=${it.repairs}\t${it.seconds}s") }
            appendLine("clean (no repair): $clean/${rows.size} = ${100 * clean / rows.size}%")
            appendLine("slowest: ${rows.maxOf { it.seconds }}s; over 3 min: ${slow.size}")
        }
        Log.i("PipelineFixtures", report)
        File(context.filesDir, "pipeline-fixtures.txt").writeText(report)

        assertTrue("only $clean/${rows.size} converted without a repair retry\n$report", clean * 10 >= rows.size * 9)
        assertTrue("over 3 minutes: ${slow.map { it.name }}\n$report", slow.isEmpty())
    }
}
