package com.simmer.app

import com.simmer.app.data.captionFromEmbedHtml
import com.simmer.app.data.captionFromOembed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Seam: [captionFromOembed] and [captionFromEmbedHtml], over responses saved from Instagram on 2026-10-15. */
class CaptionParserTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/instagram/$name")) { "missing fixture $name" }
            .bufferedReader().readText()

    @Test fun `oembed title is the caption`() {
        assertEquals(
            "What you know about love? Love is this grilled cheese from @cheesegrille ❤️\n" +
                "👇🏼 Tag someone who NEEDS this!\n\n" +
                "#pigout_nyc #nyceats #newforkcity #eaterny #grilledcheese #nycfood #nyceeeeeats #forkyeah",
            captionFromOembed(fixture("oembed-reel-grilled-cheese.json")),
        )
    }

    @Test fun `a long oembed caption is kept whole`() {
        val caption = captionFromOembed(fixture("oembed-post-chicken-curry.json"))!!
        assertEquals(true, caption.startsWith("I won't be home in time to cook dinner today"))
        assertEquals(true, caption.contains("The ground masala went in next (1 tblspn poppy seeds"))
        assertEquals(true, caption.contains("\n\nWhole garam masala is made fragrant"))
    }

    @Test fun `a caption with a dash and curly apostrophe keeps them`() {
        val caption = captionFromOembed(fixture("oembed-post-dal.json"))!!
        assertEquals(true, caption.startsWith("Though it’s conventional to think of dals"))
    }

    @Test fun `no media match is no caption`() {
        assertNull(captionFromOembed(fixture("oembed-no-media-match.txt")))
    }

    @Test fun `oembed without a usable title is no caption`() {
        assertNull(captionFromOembed("""{"version":"1.0","title":"   "}"""))
        assertNull(captionFromOembed("""{"version":"1.0"}"""))
        assertNull(captionFromOembed("""{"title":null}"""))
        assertNull(captionFromOembed(""))
    }

    @Test fun `embed html caption drops the username, links and comment count`() {
        assertEquals(
            "What you know about love? Love is this grilled cheese from @cheesegrille ❤️\n" +
                "👇🏼 Tag someone who NEEDS this!\n\n" +
                "#pigout_nyc #nyceats #newforkcity #eaterny #grilledcheese #nycfood #nyceeeeeats #forkyeah",
            captionFromEmbedHtml(fixture("embed-reel-grilled-cheese.html")),
        )
    }

    @Test fun `embed html entities are decoded`() {
        val caption = captionFromEmbedHtml(fixture("embed-post-chicken-curry.html"))!!
        assertEquals(true, caption.startsWith("I won't be home in time to cook dinner today and so i cooked before breakfast instead."))
        assertEquals(true, caption.contains("\"white masala\""))
        assertEquals(false, caption.contains("&#039;"))
        assertEquals(false, caption.contains("<br"))
    }

    @Test fun `html without a caption is no caption`() {
        assertNull(captionFromEmbedHtml("<html><title>Instagram</title></html>"))
        assertNull(captionFromEmbedHtml(""))
    }
}
