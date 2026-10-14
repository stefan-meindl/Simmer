package com.simmer.app.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenTransitionTest {
    @Test
    fun `switching between tabs fades`() {
        assertEquals(ScreenTransition.Fade, screenTransition(fromTab = true, toTab = true, reducedMotion = false))
    }

    @Test
    fun `leaving or entering a stack screen slides`() {
        assertEquals(ScreenTransition.Slide, screenTransition(fromTab = true, toTab = false, reducedMotion = false))
        assertEquals(ScreenTransition.Slide, screenTransition(fromTab = false, toTab = true, reducedMotion = false))
        assertEquals(ScreenTransition.Slide, screenTransition(fromTab = false, toTab = false, reducedMotion = false))
    }

    @Test
    fun `reduced motion crossfades stack transitions`() {
        assertEquals(ScreenTransition.Fade, screenTransition(fromTab = true, toTab = false, reducedMotion = true))
        assertEquals(ScreenTransition.Fade, screenTransition(fromTab = false, toTab = false, reducedMotion = true))
    }
}
