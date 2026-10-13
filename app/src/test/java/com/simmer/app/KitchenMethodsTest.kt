package com.simmer.app

import com.simmer.app.domain.applianceOfOption
import com.simmer.app.domain.filterMethodsByKitchen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private fun option(v: String, l: String) = mapOf("v" to v, "l" to l)
private fun method(vararg options: Map<String, String>) = mapOf("id" to "m1", "label" to "The chicken", "options" to options.toList())
private fun step(id: String, cond: String? = null): Map<String, Any?> =
    if (cond == null) mapOf("id" to id) else mapOf("id" to id, "if" to cond)

/** Seam: [filterMethodsByKitchen] and [applianceOfOption], the kitchen rule applied to a pass 2 reply. */
class KitchenMethodsTest {
    private val three = method(option("oven", "Oven"), option("air", "Air fryer"), option("hob", "Hob"))
    private val steps = listOf(
        step("p1"), step("c1", "m1=oven"), step("c1", "m1=air"), step("c1", "m1=hob"), step("c2"),
    )

    @Test fun `options are matched to catalogue appliances by longest name`() {
        assertEquals("oven", applianceOfOption("oven", "Oven"))
        assertEquals("steamoven", applianceOfOption("steam", "Steam oven"))
        assertEquals("airfryer", applianceOfOption("air", "Air fryer"))
        assertEquals("hob", applianceOfOption("pan", "On the stovetop"))
        assertNull(applianceOfOption("quick", "Quick version"))
    }

    @Test fun `Free for all changes nothing`() {
        val (methods, kept) = filterMethodsByKitchen(listOf(three), steps, owned = null)

        assertEquals(listOf(three), methods)
        assertEquals(steps, kept)
    }

    @Test fun `an option for an appliance the cook lacks goes with its steps`() {
        val (methods, kept) = filterMethodsByKitchen(listOf(three), steps, owned = setOf("oven", "hob"))

        val options = (methods.single()["options"] as List<*>).map { (it as Map<*, *>)["v"] }
        assertEquals(listOf("oven", "hob"), options)
        assertEquals(listOf("p1", "c1", "c1", "c2"), kept.map { it["id"] })
        assertEquals(listOf(null, "m1=oven", "m1=hob", null), kept.map { it["if"] })
    }

    @Test fun `a method left with one option is no choice and its steps lose the condition`() {
        val two = method(option("oven", "Oven"), option("air", "Air fryer"))
        val (methods, kept) = filterMethodsByKitchen(listOf(two), listOf(step("c1", "m1=oven"), step("c1", "m1=air"), step("c2")), setOf("oven"))

        assertEquals(emptyList<Any>(), methods)
        assertEquals(listOf("c1", "c2"), kept.map { it["id"] })
        assertEquals(listOf(null, null), kept.map { it["if"] })
    }

    @Test fun `a method whose options are all missing is left alone`() {
        val two = method(option("oven", "Oven"), option("air", "Air fryer"))
        val (methods, kept) = filterMethodsByKitchen(listOf(two), steps, owned = setOf("hob"))

        assertEquals(listOf(two), methods)
        assertEquals(steps, kept)
    }

    @Test fun `options naming no catalogue appliance stay`() {
        val custom = method(option("tandoor", "Tandoor"), option("hob", "Hob"), option("oven", "Oven"))
        val (methods, _) = filterMethodsByKitchen(listOf(custom), emptyList(), owned = setOf("hob", "c:Tandoor"))

        assertEquals(listOf("tandoor", "hob"), (methods.single()["options"] as List<*>).map { (it as Map<*, *>)["v"] })
    }
}
