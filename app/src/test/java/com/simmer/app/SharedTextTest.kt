package com.simmer.app

import com.simmer.app.domain.SharedContent
import com.simmer.app.domain.readSharedText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Seam: [readSharedText], what Add recipe makes of the text another app shares to Simmer. */
class SharedTextTest {

    private val reel = SharedContent.Reel("https://www.instagram.com/reel/CHOJBJ6pcSx/")

    @Test fun `a reel link is recognised`() {
        assertEquals(reel, readSharedText("https://www.instagram.com/reel/CHOJBJ6pcSx/"))
    }

    @Test fun `igsh and utm parameters are stripped`() {
        assertEquals(reel, readSharedText("https://www.instagram.com/reel/CHOJBJ6pcSx/?igsh=MTIzNDU2&utm_source=ig_web_copy_link"))
    }

    @Test fun `a link without trailing slash or www is normalised`() {
        assertEquals(reel, readSharedText("https://instagram.com/reel/CHOJBJ6pcSx?igsh=abc"))
    }

    @Test fun `reels and user-prefixed paths become the reel path`() {
        assertEquals(reel, readSharedText("https://www.instagram.com/reels/CHOJBJ6pcSx/"))
        assertEquals(reel, readSharedText("https://www.instagram.com/pigout_nyc/reel/CHOJBJ6pcSx/"))
    }

    @Test fun `a post link is recognised and keeps its p path`() {
        assertEquals(
            SharedContent.Reel("https://www.instagram.com/p/Cr4D2VpuqsE/"),
            readSharedText("https://www.instagram.com/p/Cr4D2VpuqsE/?img_index=1"),
        )
    }

    @Test fun `a short leading phrase before the link is tolerated`() {
        assertEquals(reel, readSharedText("Look at this! https://www.instagram.com/reel/CHOJBJ6pcSx/?igsh=x"))
    }

    @Test fun `recipe notes that contain a link are shared text`() {
        val notes = "Pancakes: 2 eggs, 200 g flour, 300 ml milk. Whisk, rest 10 minutes, fry.\nhttps://www.instagram.com/reel/CHOJBJ6pcSx/"
        assertEquals(SharedContent.Text(notes), readSharedText(notes))
        val trailing = "https://www.instagram.com/reel/CHOJBJ6pcSx/\nso good, I add a pinch of salt"
        assertEquals(SharedContent.Text(trailing), readSharedText(trailing))
    }

    @Test fun `other text is shared text`() {
        assertEquals(SharedContent.Text("Pancakes: 2 eggs"), readSharedText("Pancakes: 2 eggs"))
    }

    @Test fun `links to other sites and other instagram pages are shared text`() {
        val other = "https://example.com/reel/CHOJBJ6pcSx/"
        assertEquals(SharedContent.Text(other), readSharedText(other))
        val profile = "https://www.instagram.com/pigout_nyc/"
        assertEquals(SharedContent.Text(profile), readSharedText(profile))
        val lookalike = "https://notinstagram.com/reel/CHOJBJ6pcSx/"
        assertEquals(SharedContent.Text(lookalike), readSharedText(lookalike))
    }

    @Test fun `nothing shared is nothing`() {
        assertNull(readSharedText(null))
        assertNull(readSharedText("   "))
    }
}
