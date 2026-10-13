package com.simmer.app

import com.simmer.app.domain.placeTooltip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** All values in px; the screen passes dp converted to px (margin 12dp, gap 10dp, arrow inset 20dp). */
class TipsTooltipPlacementTest {
    private fun place(
        anchorLeft: Int = 400, anchorTop: Int = 500, anchorRight: Int = 600, anchorBottom: Int = 560,
        width: Int = 300, height: Int = 200, windowWidth: Int = 1000, windowHeight: Int = 2000,
    ) = placeTooltip(
        anchorLeft, anchorTop, anchorRight, anchorBottom, width, height, windowWidth, windowHeight,
        margin = 12, gap = 10, arrowInset = 20,
    )

    @Test fun `sits below the word, centred on it, with the gap`() {
        val p = place()
        assertEquals(350, p.x)
        assertEquals(570, p.y)
        assertTrue(p.below)
        assertEquals(150, p.arrowX)
    }

    @Test fun `is clamped to the left margin and the arrow still points at the word`() {
        val p = place(anchorLeft = 0, anchorRight = 100)
        assertEquals(12, p.x)
        assertEquals(38, p.arrowX)
    }

    @Test fun `is clamped to the right margin`() {
        val p = place(anchorLeft = 900, anchorRight = 1000)
        assertEquals(1000 - 300 - 12, p.x)
        assertEquals(950 - 688, p.arrowX)
    }

    @Test fun `the arrow stays inside the tooltip corners`() {
        val p = place(anchorLeft = 0, anchorRight = 4)
        assertEquals(20, p.arrowX)
    }

    @Test fun `flips above when there is no room below`() {
        val p = place(anchorTop = 1800, anchorBottom = 1860)
        assertFalse(p.below)
        assertEquals(1800 - 10 - 200, p.y)
    }

    @Test fun `stays below when it fits neither way`() {
        val p = place(anchorTop = 100, anchorBottom = 160, height = 1990)
        assertTrue(p.below)
        assertEquals(170, p.y)
    }
}
