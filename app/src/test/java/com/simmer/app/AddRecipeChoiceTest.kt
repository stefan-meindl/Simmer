package com.simmer.app

import com.simmer.app.domain.AddStep
import com.simmer.app.domain.addStepFromKey
import com.simmer.app.domain.back
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Seam: [AddStep], the first-step choice of Add recipe, its Back rule and the key kept in saved state. */
class AddRecipeChoiceTest {

    @Test fun `Add recipe starts on the choice step when nothing was saved`() {
        assertEquals(AddStep.Choice, addStepFromKey(null))
    }

    @Test fun `an unknown saved key falls back to the choice step`() {
        assertEquals(AddStep.Choice, addStepFromKey("garbage"))
    }

    @Test fun `every step survives a round trip through its saved key`() {
        AddStep.entries.forEach { assertEquals(it, addStepFromKey(it.key)) }
    }

    @Test fun `Back from the phone path returns to the choice step`() {
        assertEquals(AddStep.Choice, AddStep.Phone.back())
    }

    @Test fun `Back from the chatbot path returns to the choice step`() {
        assertEquals(AddStep.Choice, AddStep.Chatbot.back())
    }

    @Test fun `Back from the choice step leaves Add recipe`() {
        assertNull(AddStep.Choice.back())
    }
}
