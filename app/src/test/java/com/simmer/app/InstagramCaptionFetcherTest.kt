package com.simmer.app

import com.simmer.app.data.InstagramCaptionFetcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

/** Seam: [InstagramCaptionFetcher], which asks oEmbed first and the embed page second, and never throws. */
class InstagramCaptionFetcherTest {

    private val reel = "https://www.instagram.com/reel/CHOJBJ6pcSx/"
    private val oembedUrl = "https://www.instagram.com/api/v1/oembed/?url=https%3A%2F%2Fwww.instagram.com%2Freel%2FCHOJBJ6pcSx%2F"
    private val embedUrl = "https://www.instagram.com/reel/CHOJBJ6pcSx/embed/captioned/"

    private fun fixture(name: String) = javaClass.getResourceAsStream("/instagram/$name")!!.bufferedReader().readText()

    private class FakeHttp(private val pages: Map<String, () -> String?>) : (String, String) -> String? {
        val requests = mutableListOf<Pair<String, String>>()
        override fun invoke(url: String, userAgent: String): String? {
            requests += url to userAgent
            return pages[url]?.invoke()
        }
    }

    @Test fun `the oembed caption is used and the embed page is not asked`() = runTest {
        val http = FakeHttp(mapOf(oembedUrl to { fixture("oembed-reel-grilled-cheese.json") }))

        val caption = InstagramCaptionFetcher(http).fetch(reel)

        assertEquals(true, caption!!.startsWith("What you know about love?"))
        assertEquals(listOf(oembedUrl), http.requests.map { it.first })
    }

    @Test fun `no oembed match falls back to the embed page`() = runTest {
        val http = FakeHttp(
            mapOf(
                oembedUrl to { fixture("oembed-no-media-match.txt") },
                embedUrl to { fixture("embed-reel-grilled-cheese.html") },
            ),
        )

        val caption = InstagramCaptionFetcher(http).fetch(reel)

        assertEquals(true, caption!!.startsWith("What you know about love?"))
        assertEquals(listOf(oembedUrl, embedUrl), http.requests.map { it.first })
    }

    @Test fun `a failing oembed request also falls back`() = runTest {
        val http = FakeHttp(
            mapOf(
                oembedUrl to { throw IOException("timeout") },
                embedUrl to { fixture("embed-post-chicken-curry.html") },
            ),
        )

        assertEquals(true, InstagramCaptionFetcher(http).fetch(reel)!!.startsWith("I won't be home"))
    }

    @Test fun `the requests use a non-browser user agent`() = runTest {
        val http = FakeHttp(emptyMap())

        InstagramCaptionFetcher(http).fetch(reel)

        assertEquals(true, http.requests.all { it.second.startsWith("okhttp/") })
    }

    @Test fun `when both fail there is no caption and nothing is thrown`() = runTest {
        val http = FakeHttp(mapOf(oembedUrl to { throw IOException("offline") }, embedUrl to { throw IOException("offline") }))

        assertNull(InstagramCaptionFetcher(http).fetch(reel))
    }
}
