package com.simmer.app

import com.simmer.app.domain.parseModelJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Seam: [parseModelJson], a model reply turned into the maps `normalize()` takes. */
class ModelJsonTest {

    @Test fun `a plain object becomes nested maps and lists`() {
        val json = parseModelJson("""{"title":"Soup","time":30,"q":1.5,"tags":["Quick"],"x":{"a":null}}""")!!

        assertEquals("Soup", json["title"])
        assertEquals(30, (json["time"] as Number).toInt())
        assertEquals(1.5, (json["q"] as Number).toDouble(), 0.0)
        assertEquals(listOf("Quick"), json["tags"])
        assertEquals(mapOf("a" to null), json["x"])
    }

    @Test fun `a code fence and chatter around the object are ignored`() {
        val json = parseModelJson("Sure! Here you go:\n```json\n{\"title\":\"Soup\"}\n```\nEnjoy.")!!

        assertEquals("Soup", json["title"])
    }

    @Test fun `braces inside strings do not end the object early`() {
        val json = parseModelJson("""{"b":"stir {i1:onion} well"}""")!!

        assertEquals("stir {i1:onion} well", json["b"])
    }

    @Test fun `a cut-off object is invalid`() {
        assertNull(parseModelJson("""{"title":"Soup","ingredients":[{"name":"on"""))
    }

    @Test fun `text without an object is invalid`() {
        assertNull(parseModelJson("I could not read that."))
        assertNull(parseModelJson(""))
        assertNull(parseModelJson("[1,2,3]"))
    }
}
