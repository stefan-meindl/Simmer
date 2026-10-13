package com.simmer.app

import com.simmer.app.domain.ApplianceRow
import com.simmer.app.domain.addOffer
import com.simmer.app.domain.matchingAppliances
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Seam: [matchingAppliances] and [addOffer], the Your kitchen search field. Rows here are the German UI. */
class YourKitchenSearchTest {

    private val rows = listOf(
        ApplianceRow("hob", label = "Herd", english = "Hob"),
        ApplianceRow("oven", label = "Backofen", english = "Oven"),
        ApplianceRow("stickblender", label = "Stabmixer", english = "Hand blender"),
        ApplianceRow("c:Thermomix", label = "Thermomix", english = "Thermomix"),
    )

    private fun keys(query: String) = matchingAppliances(rows, query).map { it.key }

    @Test fun `blank query lists everything`() {
        assertEquals(listOf("hob", "oven", "stickblender", "c:Thermomix"), keys("  "))
    }

    @Test fun `matches the current-language name ignoring case`() {
        assertEquals(listOf("oven"), keys("backo"))
    }

    @Test fun `matches the English name while the UI is German`() {
        assertEquals(listOf("stickblender"), keys("hand BLENDER"))
        assertEquals(listOf("hob"), keys("hob"))
    }

    @Test fun `no offer for a single character`() {
        assertNull(addOffer(rows, "x"))
        assertNull(addOffer(rows, " x "))
    }

    @Test fun `offers the trimmed query when nothing matches exactly`() {
        assertEquals("Bamix", addOffer(rows, "  Bamix "))
    }

    @Test fun `still offers when the query only partly matches an appliance`() {
        assertEquals("Back", addOffer(rows, "Back"))
    }

    @Test fun `no offer on an exact match in either language, ignoring case`() {
        assertNull(addOffer(rows, "herd"))
        assertNull(addOffer(rows, "OVEN"))
        assertNull(addOffer(rows, "thermomix"))
    }
}
