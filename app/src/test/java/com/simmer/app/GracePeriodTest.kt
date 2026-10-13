package com.simmer.app

import com.simmer.app.domain.GracePeriod
import com.simmer.app.domain.GracePhase
import org.junit.Assert.assertEquals
import org.junit.Test

/** The grace period a freshly ticked Shopping Item keeps its place for, driven by a fake clock. */
class GracePeriodTest {
    private var now = 1_000_000L
    private val grace = GracePeriod(clock = { now })

    private fun after(ms: Long) {
        now += ms
        grace.advance()
    }

    @Test fun `a ticked item stays put for 3 seconds then fades then is gone`() {
        grace.tick("a", fadeMs = 200)
        assertEquals(mapOf("a" to GracePhase.Ticked), grace.phases.value)

        after(2_999)
        assertEquals(mapOf("a" to GracePhase.Ticked), grace.phases.value)

        after(1)
        assertEquals(mapOf("a" to GracePhase.Fading), grace.phases.value)

        after(199)
        assertEquals(mapOf("a" to GracePhase.Fading), grace.phases.value)

        after(1)
        assertEquals(emptyMap<String, GracePhase>(), grace.phases.value)
    }

    @Test fun `unticking during the grace period cancels the countdown`() {
        grace.tick("a", fadeMs = 200)
        after(1_000)

        grace.untick("a")
        assertEquals(emptyMap<String, GracePhase>(), grace.phases.value)

        after(5_000)
        assertEquals(emptyMap<String, GracePhase>(), grace.phases.value)
    }

    @Test fun `each item counts down 3 seconds from its own tick`() {
        grace.tick("a", fadeMs = 200)
        after(1_000)
        grace.tick("b", fadeMs = 200)
        after(1_000)
        grace.tick("c", fadeMs = 200)

        after(1_000) // a at 3s, b at 2s, c at 1s
        assertEquals(
            mapOf("a" to GracePhase.Fading, "b" to GracePhase.Ticked, "c" to GracePhase.Ticked),
            grace.phases.value,
        )

        after(200) // a gone (3.2s), b at 2.2s, c at 1.2s
        assertEquals(mapOf("b" to GracePhase.Ticked, "c" to GracePhase.Ticked), grace.phases.value)

        after(800) // b at 3s
        assertEquals(mapOf("b" to GracePhase.Fading, "c" to GracePhase.Ticked), grace.phases.value)
    }

    @Test fun `a re-tick after an untick starts a fresh 3 seconds`() {
        grace.tick("a", fadeMs = 200)
        after(2_500)
        grace.untick("a")
        grace.tick("a", fadeMs = 200)

        after(2_500)
        assertEquals(mapOf("a" to GracePhase.Ticked), grace.phases.value)
    }

    @Test fun `without animations there is no fade, the item goes after 3 seconds`() {
        grace.tick("a", fadeMs = 0)

        after(2_999)
        assertEquals(mapOf("a" to GracePhase.Ticked), grace.phases.value)

        after(1)
        assertEquals(emptyMap<String, GracePhase>(), grace.phases.value)
    }

    @Test fun `clearing releases every item at once`() {
        grace.tick("a", fadeMs = 200)
        grace.tick("b", fadeMs = 200)

        grace.clear()

        assertEquals(emptyMap<String, GracePhase>(), grace.phases.value)
    }
}
