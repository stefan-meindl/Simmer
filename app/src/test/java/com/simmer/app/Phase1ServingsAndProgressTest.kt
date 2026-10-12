package com.simmer.app

import com.simmer.app.domain.SegmentState
import com.simmer.app.domain.progressSegments
import com.simmer.app.domain.stepServings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase1ServingsAndProgressTest {

    @Test fun `servings increase and decrease by one`() {
        assertEquals(5, stepServings(4, +1))
        assertEquals(3, stepServings(4, -1))
    }

    @Test fun `servings never drop below 1`() {
        assertEquals(1, stepServings(1, -1))
    }

    @Test fun `servings never exceed 12`() {
        assertEquals(12, stepServings(12, +1))
    }

    @Test fun `phase 1 is current with two future segments that are not tappable`() {
        val s = progressSegments(current = 1)
        assertEquals(listOf(SegmentState.Current, SegmentState.Todo, SegmentState.Todo), s.map { it.state })
        assertTrue(s.none { it.tappable })
    }

    @Test fun `earlier segments are done and tappable`() {
        val s = progressSegments(current = 3)
        assertEquals(listOf(SegmentState.Done, SegmentState.Done, SegmentState.Current), s.map { it.state })
        assertEquals(listOf(true, true, false), s.map { it.tappable })
        assertEquals(listOf(1, 2, 3), s.map { it.phase })
        assertFalse(s[2].tappable)
    }
}
