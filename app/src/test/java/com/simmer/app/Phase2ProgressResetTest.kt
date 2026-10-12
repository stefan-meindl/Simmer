package com.simmer.app

import com.simmer.app.domain.CookingProgressStore
import com.simmer.app.domain.Phase1State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase2ProgressResetTest {
    private val chilli = BuiltInRecipes.chilli()
    private val start = Phase1State.initial(chilli, planned = null)

    // --- which edits reset progress ------------------------------------------

    @Test fun `picking a different method changes the plan`() {
        assertTrue(start.changesCookingPlan(start.withSwap("chilli", "oven")))
    }

    @Test fun `picking a swap in phase 1 changes the plan`() {
        assertTrue(start.changesCookingPlan(start.withSwap("mince", "plant")))
    }

    @Test fun `toggling an add-in changes the plan`() {
        assertTrue(start.changesCookingPlan(start.withAddIn("choc", true)))
    }

    @Test fun `changing servings changes the plan`() {
        assertTrue(start.changesCookingPlan(start.withServings(start.servings + 1)))
    }

    @Test fun `re-picking the current option does not change the plan`() {
        val current = start.selection.choice.getValue("chilli")
        assertFalse(start.changesCookingPlan(start.withSwap("chilli", current)))
    }

    @Test fun `an add-in toggled on then off is the same plan`() {
        val back = start.withAddIn("choc", true).withAddIn("choc", false)
        assertFalse(start.changesCookingPlan(back))
    }

    // --- the progress store --------------------------------------------------

    @Test fun `marked steps are remembered per recipe`() {
        val store = CookingProgressStore()
        store.markDone("chilli", "p-veg")
        store.markDone("curry", "p-rice")
        assertEquals(setOf("p-veg"), store.doneSteps("chilli"))
        assertEquals(setOf("p-rice"), store.doneSteps("curry"))
    }

    @Test fun `reset clears only that recipe`() {
        val store = CookingProgressStore()
        store.markDone("chilli", "p-veg")
        store.markDone("curry", "p-rice")
        store.reset("chilli")
        assertEquals(emptySet<String>(), store.doneSteps("chilli"))
        assertEquals(setOf("p-rice"), store.doneSteps("curry"))
    }
}
