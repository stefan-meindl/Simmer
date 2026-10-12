package com.simmer.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simmer.app.domain.linkMarks
import com.simmer.app.domain.parseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on a device, where regexes go through Android's ICU engine. The JVM engine used by unit tests
 * accepts patterns ICU rejects (for example an unescaped closing brace), so a domain regex is only
 * proven on device. Loading `RecipeLogicKt` compiles every top-level regex.
 */
@RunWith(AndroidJUnit4::class)
class AndroidRegexTest {

    @Test
    fun domainRegexesCompileOnAndroid() {
        assertEquals(emptyList<Any>(), linkMarks("", emptyList()))
        parseBody("Halve the {c1:carrots}.", emptyMap(), "metric")
    }
}
