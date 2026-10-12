package com.simmer.app

import com.simmer.app.domain.parseQty
import org.junit.Assert.assertEquals
import org.junit.Test

class ParseQtyTest {

    @Test fun `decimal with dot`() = assertEquals(1.5, parseQty("1.5"), 0.001)
    @Test fun `decimal with comma`() = assertEquals(1.5, parseQty("1,5"), 0.001)
    @Test fun `slash fraction`() = assertEquals(0.5, parseQty("1/2"), 0.001)
    @Test fun `mixed number`() = assertEquals(1.5, parseQty("1 1/2"), 0.001)
    @Test fun `unicode half`() = assertEquals(0.5, parseQty("½"), 0.001)
    @Test fun `unicode quarter`() = assertEquals(0.25, parseQty("¼"), 0.001)
    @Test fun `unicode three quarters`() = assertEquals(0.75, parseQty("¾"), 0.001)
    @Test fun `whole number`() = assertEquals(3.0, parseQty("3"), 0.001)
    @Test fun `empty string`() = assertEquals(0.0, parseQty(""), 0.001)
    @Test fun `mixed with unicode`() = assertEquals(1.25, parseQty("1¼"), 0.001)
    @Test fun `two and a half`() = assertEquals(2.5, parseQty("2 1/2"), 0.001)
}
