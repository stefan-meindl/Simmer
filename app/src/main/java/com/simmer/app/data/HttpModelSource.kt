package com.simmer.app.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** [ModelSource] over HTTPS with `Range` requests; redirects (Hugging Face sends one to its CDN) are followed. */
class HttpModelSource(private val url: String) : ModelSource {

    override fun open(offset: Long): ModelStream {
        var connection = connect(offset)
        var code = connection.responseCode
        if (code == HttpURLConnection.HTTP_BAD_REQUEST + 16) { // 416: the partial file is not usable, start over
            connection.disconnect()
            connection = connect(0)
            code = connection.responseCode
        }
        return when (code) {
            HttpURLConnection.HTTP_PARTIAL -> ModelStream(true, connection.contentLengthLong, connection.inputStream)
            HttpURLConnection.HTTP_OK -> ModelStream(false, connection.contentLengthLong, connection.inputStream)
            else -> {
                connection.disconnect()
                throw IOException("Unexpected HTTP $code")
            }
        }
    }

    private fun connect(offset: Long): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        if (offset > 0) connection.setRequestProperty("Range", "bytes=$offset-")
        return connection
    }
}
