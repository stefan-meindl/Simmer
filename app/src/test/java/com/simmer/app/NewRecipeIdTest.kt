package com.simmer.app

import com.simmer.app.domain.newRecipeId
import org.junit.Assert.assertEquals
import org.junit.Test

/** Seam: [newRecipeId], the one place a User Recipe's id is made. */
class NewRecipeIdTest {
    @Test fun `an id is u and the time in base 36`() {
        assertEquals("u" + "zz".toLong(36).toString(36), newRecipeId(now = "zz".toLong(36)))
        assertEquals("u1", newRecipeId(now = 1))
    }
}
