package com.simmer.app.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenTransitionTest {
    @Test
    fun `switching between tabs fades`() {
        assertEquals(ScreenTransition.Fade, screenTransition(from = ScreenKind.Tab, to = ScreenKind.Tab, reducedMotion = false))
    }

    @Test
    fun `leaving or entering a stack screen slides`() {
        assertEquals(ScreenTransition.Slide, screenTransition(from = ScreenKind.Tab, to = ScreenKind.Stack, reducedMotion = false))
        assertEquals(ScreenTransition.Slide, screenTransition(from = ScreenKind.Stack, to = ScreenKind.Tab, reducedMotion = false))
        assertEquals(ScreenTransition.Slide, screenTransition(from = ScreenKind.Stack, to = ScreenKind.Stack, reducedMotion = false))
    }

    @Test
    fun `reduced motion crossfades stack transitions`() {
        assertEquals(ScreenTransition.Fade, screenTransition(from = ScreenKind.Tab, to = ScreenKind.Stack, reducedMotion = true))
        assertEquals(ScreenTransition.Fade, screenTransition(from = ScreenKind.Stack, to = ScreenKind.Stack, reducedMotion = true))
    }
}
