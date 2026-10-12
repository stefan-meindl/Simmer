package com.simmer.app

import com.simmer.app.data.DownloadOutcome
import com.simmer.app.data.HttpStatusException
import com.simmer.app.data.ModelDownloader
import com.simmer.app.data.ModelSource
import com.simmer.app.data.ModelStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Seam: [ModelDownloader] with a fake [ModelSource] and a temporary directory. Covers resuming from
 * the last byte, checksum verification and the free-storage check without a network.
 */
class ModelDownloaderTest {

    @get:Rule val tmp = TemporaryFolder()

    private val content = ByteArray(10_000) { (it * 31 % 251).toByte() }

    // Computed with the JDK's own digest, independent of ModelDownloader's code path.
    private val contentSha = sha256Of(content)

    private fun sha256Of(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Serves [content]; the first [failures] opens die after [failAfter] bytes with an IOException. */
    private inner class FakeSource(
        val data: ByteArray = content,
        var failures: Int = 0,
        val failAfter: Int = 3_000,
        val honoursRange: Boolean = true,
        val truncateTo: Int? = null,
    ) : ModelSource {
        val offsets = mutableListOf<Long>()

        override fun open(offset: Long): ModelStream {
            offsets += offset
            val start = if (honoursRange) offset.toInt() else 0
            val end = truncateTo ?: data.size
            val body = data.copyOfRange(start, end.coerceAtLeast(start))
            val fails = failures > 0
            if (fails) failures--
            val input: InputStream = if (fails) FailingStream(body, failAfter) else ByteArrayInputStream(body)
            return ModelStream(resumed = honoursRange && offset > 0, remaining = (data.size - start).toLong(), input = input)
        }
    }

    private class FailingStream(private val body: ByteArray, private val failAfter: Int) : InputStream() {
        private var pos = 0
        override fun read(): Int {
            if (pos >= failAfter) throw IOException("connection reset")
            return if (pos < body.size) body[pos++].toInt() and 0xff else -1
        }
    }

    private fun downloader(
        source: ModelSource,
        sha: String = contentSha,
        free: Long = 1_000_000,
        required: Long = 20_000,
    ) = ModelDownloader(tmp.root, "model.bin", sha, source, freeBytes = { free }, requiredFreeBytes = required)

    private val finalFile get() = File(tmp.root, "model.bin")
    private val partFile get() = File(tmp.root, "model.bin.part")

    @Test fun `downloads the file, verifies it and reports growing percentages up to 100`() = runBlocking {
        val seen = mutableListOf<Int>()
        val outcome = downloader(FakeSource()).download { seen += it }

        assertEquals(DownloadOutcome.Completed, outcome)
        assertArrayEquals(content, finalFile.readBytes())
        assertFalse(partFile.exists())
        assertEquals(100, seen.last())
        assertEquals(seen.sorted(), seen)
    }

    @Test fun `a dropped connection keeps the partial file and the next attempt resumes from its last byte`() = runBlocking {
        val source = FakeSource(failures = 1, failAfter = 3_000)
        val dl = downloader(source)

        val first = dl.download { }
        assertTrue(first is DownloadOutcome.Interrupted)
        assertEquals(3_000L, partFile.length())
        assertFalse(finalFile.exists())

        val second = dl.download { }
        assertEquals(DownloadOutcome.Completed, second)
        assertEquals(listOf(0L, 3_000L), source.offsets)
        assertArrayEquals(content, finalFile.readBytes())
    }

    @Test fun `when the server ignores the range request the download restarts cleanly`() = runBlocking {
        partFile.writeBytes(content.copyOfRange(0, 4_000))
        val source = FakeSource(honoursRange = false)

        assertEquals(DownloadOutcome.Completed, downloader(source).download { })
        assertEquals(listOf(4_000L), source.offsets)
        assertArrayEquals(content, finalFile.readBytes())
    }

    @Test fun `a wrong checksum deletes the file and reports the mismatch`() = runBlocking {
        val outcome = downloader(FakeSource(), sha = "0".repeat(64)).download { }

        assertEquals(DownloadOutcome.ChecksumMismatch, outcome)
        assertFalse(finalFile.exists())
        assertFalse(partFile.exists())
    }

    @Test fun `a stream that ends early is an interruption, not a finished file`() = runBlocking {
        val source = FakeSource(truncateTo = 6_000)
        val outcome = downloader(source).download { }

        assertTrue(outcome is DownloadOutcome.Interrupted)
        assertFalse(finalFile.exists())
        assertEquals(6_000L, partFile.length())
    }

    @Test fun `without enough free space nothing is requested`() = runBlocking {
        val source = FakeSource()
        val outcome = downloader(source, free = 19_999, required = 20_000).download { }

        assertEquals(DownloadOutcome.NotEnoughStorage, outcome)
        assertTrue(source.offsets.isEmpty())
    }

    @Test fun `bytes already downloaded count towards the free space needed`() = runBlocking {
        partFile.writeBytes(content.copyOfRange(0, 5_000))
        val source = FakeSource()

        assertEquals(DownloadOutcome.Completed, downloader(source, free = 15_000, required = 20_000).download { })
    }

    @Test fun `an already installed model is not downloaded again`() = runBlocking {
        finalFile.writeBytes(content)
        val source = FakeSource()

        assertEquals(DownloadOutcome.Completed, downloader(source).download { })
        assertTrue(source.offsets.isEmpty())
    }

    private class FailingOpen(private val error: IOException) : ModelSource {
        var opens = 0
        override fun open(offset: Long): ModelStream {
            opens++
            throw error
        }
    }

    @Test fun `a permanent HTTP error fails instead of retrying`() = runBlocking {
        for (code in listOf(400, 401, 403, 404)) {
            assertEquals(DownloadOutcome.ServerError(code), downloader(FailingOpen(HttpStatusException(code))).download { })
        }
    }

    @Test fun `server errors, request timeout and rate limiting are retried`() = runBlocking {
        for (code in listOf(408, 429, 500, 503)) {
            assertTrue(downloader(FailingOpen(HttpStatusException(code))).download { } is DownloadOutcome.Interrupted)
        }
    }

    @Test fun `a full disk while writing reports not enough storage and keeps the partial file`() = runBlocking {
        val source = ModelSource { _ ->
            ModelStream(false, content.size.toLong(), object : InputStream() {
                private var pos = 0
                override fun read(): Int {
                    if (pos >= 2_000) throw IOException("write failed: ENOSPC (No space left on device)")
                    return content[pos++].toInt() and 0xff
                }
            })
        }

        assertEquals(DownloadOutcome.NotEnoughStorage, downloader(source).download { })
        assertFalse(finalFile.exists())
    }

    @Test fun `a partial file removed while hashing counts as cancellation`() {
        val dl = downloader(FakeSource())
        var cancelled = false
        try {
            runBlocking { dl.download { if (it == 100) partFile.delete() } }
        } catch (e: kotlinx.coroutines.CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
    }

    @Test fun `remove deletes the model and any partial download`() {
        finalFile.writeBytes(content)
        partFile.writeBytes(content)
        val dl = downloader(FakeSource())

        assertTrue(dl.isInstalled())
        dl.remove()

        assertFalse(dl.isInstalled())
        assertFalse(partFile.exists())
    }
}
