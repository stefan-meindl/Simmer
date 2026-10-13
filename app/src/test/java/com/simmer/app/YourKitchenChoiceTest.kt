package com.simmer.app

import com.simmer.app.domain.APPLIANCE_CATALOGUE
import com.simmer.app.domain.DEFAULT_APPLIANCES
import com.simmer.app.domain.KitchenChoice
import com.simmer.app.domain.kitchenKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Seam: [KitchenChoice], what the user owns and the appliances they added, plus [kitchenKeys], the checklist order. */
class YourKitchenChoiceTest {

    @Test fun `catalogue has the 28 appliances of the spec`() {
        assertEquals(28, APPLIANCE_CATALOGUE.size)
        assertEquals(28, APPLIANCE_CATALOGUE.toSet().size)
    }

    @Test fun `first run ticks hob, oven and microwave`() {
        assertEquals(setOf("hob", "oven", "microwave"), DEFAULT_APPLIANCES)
        assertEquals(3, KitchenChoice.initial.selected.size)
    }

    @Test fun `toggling ticks then unticks an appliance`() {
        val ticked = KitchenChoice(emptySet(), emptySet()).toggle("wok")
        assertEquals(setOf("wok"), ticked.selected)
        assertEquals(1, ticked.selected.size)
        assertTrue(ticked.toggle("wok").selected.isEmpty())
    }

    @Test fun `adding your own selects it and remembers the name`() {
        val c = KitchenChoice(setOf("hob"), emptySet()).addCustom("  Thermomix ")
        assertEquals(setOf("hob", "c:Thermomix"), c.selected)
        assertEquals(setOf("Thermomix"), c.custom)
    }

    @Test fun `an added appliance stays in the list when unchecked`() {
        val c = KitchenChoice(emptySet(), emptySet()).addCustom("Bamix").toggle("c:Bamix")
        assertTrue(c.selected.isEmpty())
        assertEquals(setOf("Bamix"), c.custom)
        assertEquals(APPLIANCE_CATALOGUE + "c:Bamix", kitchenKeys(c.custom))
    }

    @Test fun `custom appliances follow the catalogue alphabetically`() {
        assertEquals(APPLIANCE_CATALOGUE + listOf("c:Bamix", "c:Thermomix"), kitchenKeys(setOf("Thermomix", "Bamix")))
    }
}
