package com.simmer.app

import com.simmer.app.domain.KitchenMode
import com.simmer.app.domain.autofillReply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val BARE = """{"title": "Pancakes", "base": 4,
  "ingredients": [{"id": "i1", "name": "Flour", "q": 200, "u": "g"}],
  "steps": [{"id": "c1", "ph": "cook", "t": "Fry", "b": "Fry until golden."}]}"""

private fun fill(clip: String?, handled: String = "") =
    autofillReply(clip, handled, appliances = emptySet(), kitchenMode = KitchenMode.Free)

/** Seam: [autofillReply], the clipboard text on return from the chatbot into the reply to prefill (or null to leave the field alone). */
class ClipboardAutofillTest {
    @Test fun `bare JSON as Gemini's Copy button leaves it is prefilled as is`() {
        assertEquals(BARE, fill(BARE))
    }

    @Test fun `a fenced reply with chatter is prefilled whole`() {
        val clip = "Here you go:\n```json\n$BARE\n```\nEnjoy!"
        assertEquals(clip, fill(clip))
    }

    @Test fun `an empty or missing clipboard changes nothing`() {
        assertNull(fill(null))
        assertNull(fill("   "))
    }

    @Test fun `unrelated text changes nothing`() {
        assertNull(fill("Pick up milk and eggs"))
    }

    @Test fun `JSON that is not a usable recipe changes nothing`() {
        assertNull(fill("""{"title": "Empty", "ingredients": [], "steps": []}"""))
        assertNull(fill(BARE.take(60)))
    }

    @Test fun `a clipboard already prefilled is not applied again`() {
        assertNull(fill(BARE, handled = BARE))
    }
}
