package com.simmer.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Where Add recipe gets the caption of a shared Reel. A seam: tests and the screen logic use a fake. */
interface CaptionFetcher {
    /** The caption of the post at [reelUrl], or null when there is none or it could not be fetched. Never throws. */
    suspend fun fetch(reelUrl: String): String?
}

/** One GET of [url] with the given User-Agent: the body of a 2xx answer, otherwise null or an exception. */
typealias HttpGet = (url: String, userAgent: String) -> String?

/**
 * Fetches the caption from Instagram's undocumented endpoints, no login: first
 * `/api/v1/oembed/` (the `title` field), then the `/embed/captioned/` page. Both answer only to
 * non-browser User-Agents; a browser one gets a JS shell without the caption (docs/research/instagram-caption-via-share.md).
 * Any failure is "no caption".
 */
class InstagramCaptionFetcher(private val get: HttpGet = ::httpGet) : CaptionFetcher {

    override suspend fun fetch(reelUrl: String): String? = withContext(Dispatchers.IO) {
        val oembed = "https://www.instagram.com/api/v1/oembed/?url=" + URLEncoder.encode(reelUrl, "UTF-8")
        attempt(oembed, ::captionFromOembed) ?: attempt(reelUrl.trimEnd('/') + "/embed/captioned/", ::captionFromEmbedHtml)
    }

    private fun attempt(url: String, parse: (String) -> String?): String? =
        try {
            get(url, USER_AGENT)?.let(parse)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    private companion object {
        const val USER_AGENT = "okhttp/4.12.0"
    }
}

private const val CONNECT_TIMEOUT_MS = 6_000
private const val READ_TIMEOUT_MS = 8_000

/** [HttpGet] over [HttpURLConnection]: short timeouts, redirects followed, null for any non-200 answer. */
internal fun httpGet(url: String, userAgent: String): String? {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", userAgent)
        if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
        val bytes = connection.inputStream.use { it.readBytes() }
        return String(bytes, Charsets.UTF_8)
    } finally {
        connection.disconnect()
    }
}
