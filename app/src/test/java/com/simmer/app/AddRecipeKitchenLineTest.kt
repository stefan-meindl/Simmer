package com.simmer.app

import com.simmer.app.domain.KitchenLine
import com.simmer.app.domain.KitchenMode
import com.simmer.app.domain.kitchenLine
import org.junit.Assert.assertEquals
import org.junit.Test

/** Seam: [kitchenLine], the "What happens next" line about the cook's appliances. */
class AddRecipeKitchenLineTest {

    @Test fun `strict and medium name the appliances in a stable order`() {
        val owned = setOf("oven", "hob", "microwave")

        assertEquals(KitchenLine.OnlyYours(listOf("hob", "microwave", "oven")), kitchenLine(owned, KitchenMode.Medium))
        assertEquals(KitchenLine.OnlyYours(listOf("hob", "microwave", "oven")), kitchenLine(owned, KitchenMode.Strict))
    }

    @Test fun `custom appliances are listed too`() {
        assertEquals(
            KitchenLine.OnlyYours(listOf("c:Thermomix", "oven")),
            kitchenLine(setOf("oven", "c:Thermomix"), KitchenMode.Medium),
        )
    }

    @Test fun `free for all allows any appliance whatever is owned`() {
        assertEquals(KitchenLine.AnyAppliance, kitchenLine(setOf("oven"), KitchenMode.Free))
    }

    @Test fun `no appliances selected is an empty list, not any appliance`() {
        assertEquals(KitchenLine.OnlyYours(emptyList()), kitchenLine(emptySet(), KitchenMode.Medium))
    }
}
