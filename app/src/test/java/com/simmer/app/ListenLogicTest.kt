package com.simmer.app

import com.simmer.app.domain.cleanTranscript
import com.simmer.app.domain.isSilent
import com.simmer.app.domain.listenClock
import com.simmer.app.domain.megabytes
import com.simmer.app.domain.withCaption
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
    }

    @Test fun `one click in a second of nothing is still silent`() {
        assertTrue(isSilent(FloatArray(16_000).also { it[8_000] = 1f }))
    }

    @Test fun `quiet but steady speech is not silent`() {
        assertFalse(isSilent(FloatArray(16_000) { if (it % 2 == 0) 0.03f else -0.03f }))
    }

    @Test fun `the capture timer shows minutes and seconds`() {
        assertEquals("0:00", listenClock(0))
        assertEquals("0:07", listenClock(7))
        assertEquals("2:30", listenClock(150))
        assertEquals("3:00", listenClock(180))
    }

    @Test fun `megabytes rounds to the nearest whole megabyte`() {
        assertEquals(60L, megabytes(59_707_625L))
        assertEquals(1L, megabytes(1_400_000L))
        assertEquals(2L, megabytes(1_500_000L))
    }

    @Test fun `whisper's bracketed non-speech tags are not words`() {
        assertEquals("", cleanTranscript(" [BLANK_AUDIO] "))
        assertEquals("Add two eggs.", cleanTranscript(" [MUSIC] Add two eggs. [BLANK_AUDIO]"))
        assertEquals("Mix well", cleanTranscript("Mix [pause] well"))
    }

    @Test fun `the transcript goes under its label after the existing text`() {
        assertEquals("Transcript\nadd eggs", withCaption("", "add eggs", "Transcript"))
        assertEquals("Caption\nSoup\n\nTranscript\nadd eggs", withCaption("Caption\nSoup\n", "add eggs", "Transcript"))
    }

    @Test fun `the same transcript is not added twice`() {
        val once = withCaption("", "add eggs", "Transcript")
        assertEquals(once, withCaption(once, "add eggs", "Transcript"))
    }
}
