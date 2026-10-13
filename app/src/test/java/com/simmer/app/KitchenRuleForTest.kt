package com.simmer.app

import com.simmer.app.domain.KitchenMode
import com.simmer.app.domain.kitchenRuleFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Seam: [kitchenRuleFor], the user's kitchen settings as the prompt clause. */
class KitchenRuleForTest {

    @Test fun `Free for all adds no clause`() {
        assertEquals("", kitchenRuleFor(setOf("oven"), KitchenMode.Free))
    }

    @Test fun `owned appliances are listed by English name in catalogue order, custom ones last`() {
        val rule = kitchenRuleFor(setOf("c:Tandoor", "airfryer", "hob"), KitchenMode.Medium)

        assertTrue(rule.contains("The cook owns these appliances: Hob, Air fryer, Tandoor."))
        assertTrue(!rule.contains("Be strict"))
    }

    @Test fun `Strict is stricter and no appliances reads none`() {
        val rule = kitchenRuleFor(emptySet(), KitchenMode.Strict)

        assertTrue(rule.contains("appliances: none."))
        assertTrue(rule.contains("Be strict"))
    }
}
