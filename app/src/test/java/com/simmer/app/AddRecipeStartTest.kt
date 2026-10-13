package com.simmer.app

import com.simmer.app.domain.AddStep
import com.simmer.app.domain.addStepFromKey
import com.simmer.app.domain.back
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Seam: [AddStep], the start stage of Add recipe, its Back rule and the key kept in saved state. */
class AddRecipeStartTest {

    @Test fun `Add recipe starts on the start stage when nothing was saved`() {
        assertEquals(AddStep.Start, addStepFromKey(null))
    }

    @Test fun `an unknown saved key falls back to the start stage`() {
        assertEquals(AddStep.Start, addStepFromKey("garbage"))
    }

    @Test fun `every step survives a round trip through its saved key`() {
        AddStep.entries.forEach { assertEquals(it, addStepFromKey(it.key)) }
    }

    @Test fun `Back from the phone path returns to the start stage`() {
        assertEquals(AddStep.Start, AddStep.Phone.back())
    }

    @Test fun `Back from the chatbot path returns to the start stage`() {
        assertEquals(AddStep.Start, AddStep.Chatbot.back())
    }

    @Test fun `Back from the start stage leaves Add recipe`() {
        assertNull(AddStep.Start.back())
    }
}
