package com.simmer.app

import com.simmer.app.domain.Phase1State
import com.simmer.app.domain.cookingView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase3CookingViewTest {
    private val state = Phase1State.initial(BuiltInRecipes.chilli(), planned = null)
    private val steps = state.resolved().steps

    @Test fun `nothing done starts at the first prep step`() {
        val view = cookingView(steps, emptySet())
        assertEquals(0, view.doneCount)
        assertEquals(steps.size, view.total)
        assertEquals(0f, view.fraction, 0f)
        assertEquals("p-veg", view.activeId)
        assertFalse(view.allDone)
    }

    @Test fun `the active step is the first unfinished one in display order`() {
        val view = cookingView(steps, setOf("p-veg", "p-spice"))
        assertEquals(2, view.doneCount)
        assertEquals("p-beans", view.activeId)
        assertEquals(2f / steps.size, view.fraction, 0.0001f)
    }

    @Test fun `finishing a later step leaves the first unfinished one active`() {
        val view = cookingView(steps, setOf("p-spice"))
        assertEquals("p-veg", view.activeId)
    }

    @Test fun `a cook step lists unfinished prep steps it needs as warnings`() {
        val soften = cookingView(steps, emptySet()).steps.first { it.step.id == "c-soft" }
        assertEquals(listOf("Prep 1"), soften.pendingNeeds.map { it.label })
        assertEquals("Chop the vegetables", soften.pendingNeeds.single().title)
        assertTrue(soften.doneNeeds.isEmpty())
    }

    @Test fun `a finished prep step turns the warning into a done line`() {
        val soften = cookingView(steps, setOf("p-veg")).steps.first { it.step.id == "c-soft" }
        assertTrue(soften.pendingNeeds.isEmpty())
        assertEquals(listOf("Prep 1"), soften.doneNeeds.map { it.label })
    }

    @Test fun `all steps done means success and no active step`() {
        val view = cookingView(steps, steps.map { it.id }.toSet())
        assertTrue(view.allDone)
        assertNull(view.activeId)
        assertEquals(1f, view.fraction, 0f)
    }

    @Test fun `done ids that are not in the plan are ignored`() {
        val view = cookingView(steps, setOf("gone"))
        assertEquals(0, view.doneCount)
    }
}
