package com.simmer.app

import com.simmer.app.domain.cleanTranscript
import com.simmer.app.domain.isSilent
import com.simmer.app.domain.withTranscript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenLogicTest {
    @Test fun `no samples or a flat line is silent`() {
        assertTrue(isSilent(FloatArray(0)))
        assertTrue(isSilent(FloatArray(16_000)))
        assertTrue(isSilent(FloatArray(16_000) { 0.001f }))
    }

    @Test fun `audible samples are not silent`() {
        assertFalse(isSilent(FloatArray(16_000) { if (it % 2 == 0) 0.2f else -0.2f }))
        assertFalse(isSilent(FloatArray(16_000).also { it[8_000] = 0.5f }))
    }

    @Test fun `whisper's bracketed non-speech tags are not words`() {
        assertEquals("", cleanTranscript(" [BLANK_AUDIO] "))
        assertEquals("Add two eggs.", cleanTranscript(" [MUSIC] Add two eggs. [BLANK_AUDIO]"))
        assertEquals("Mix well", cleanTranscript("Mix [pause] well"))
    }

    @Test fun `the transcript goes under its label after the existing text`() {
        assertEquals("Transcript\nadd eggs", withTranscript("", "add eggs", "Transcript"))
        assertEquals("Caption\nSoup\n\nTranscript\nadd eggs", withTranscript("Caption\nSoup\n", "add eggs", "Transcript"))
    }

    @Test fun `the same transcript is not added twice`() {
        val once = withTranscript("", "add eggs", "Transcript")
        assertEquals(once, withTranscript(once, "add eggs", "Transcript"))
    }
}
