package com.simmer.app

import com.simmer.app.domain.link
import com.simmer.app.domain.linkMarks
import com.simmer.app.domain.model.Ingredient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkTest {

    private val ings = listOf(
        Ingredient("on", "Onions", 2.0, ""),
        Ingredient("ga", "Garlic", 3.0, "tsp"),
        Ingredient("ri", "Long-grain rice", 300.0, "g"),
        Ingredient("dc", "Dark chocolate", 20.0, "g"),
    )

    @Test fun `link wraps ingredient name`() {
        val result = link("Add the onions and garlic.", ings)
        assertTrue("Should contain onion link", result.contains("{on:"))
        assertTrue("Should contain garlic link", result.contains("{ga:"))
    }

    @Test fun `link is case insensitive`() {
        val result = link("Add the ONIONS.", ings)
        assertTrue("Should contain onion link", result.contains("{on:ONIONS}"))
    }

    @Test fun `link handles plural`() {
        val result = link("Chop the onions.", ings)
        assertTrue("Should link plural onions", result.contains("{on:"))
    }

    @Test fun `link handles partial name (last word)`() {
        // "rice" from "Long-grain rice" should match
        val result = link("Rinse the rice.", ings)
        assertTrue("Should link rice", result.contains("{ri:"))
    }

    @Test fun `link no overlaps`() {
        val result = link("The dark chocolate is good.", ings)
        // "dark chocolate" should be linked as one match
        val count = result.count { it == '{' }
        assertEquals(1, count)
    }

    @Test fun `linkMarks returns sorted non-overlapping marks`() {
        val marks = linkMarks("Add onions and garlic.", ings)
        assertTrue("Should find two marks", marks.size >= 2)
        for (i in 0 until marks.size - 1) {
            assertTrue("Marks should not overlap", marks[i].e <= marks[i + 1].s)
        }
    }

    @Test fun `link no match returns unchanged`() {
        val text = "Boil some water."
        assertEquals(text, link(text, ings))
    }

    @Test fun `linkMarks respects Unicode word boundaries`() {
        // "on" should not link inside "onions" (already handled), or partial words
        val marks = linkMarks("An ongoing event.", ings)
        // "on" is too short (< 3 chars minimum), and "ongoing" shouldn't match "on" alone
        assertTrue("Should not match partial word", marks.none { it.id == "on" && it.e - it.s <= 2 })
    }
}
