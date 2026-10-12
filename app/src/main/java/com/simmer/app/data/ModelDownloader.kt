package com.simmer.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * One response of the model server. [resumed] is true when it continues at the requested offset
 * (HTTP 206); false when it sends the whole file from byte 0. [remaining] is the number of bytes
 * [input] should deliver.
 */
class ModelStream(val resumed: Boolean, val remaining: Long, val input: InputStream) : Closeable {
    override fun close() = input.close()
}

/** Where the model bytes come from. Throws [IOException] when the connection fails. */
fun interface ModelSource {
    fun open(offset: Long): ModelStream
}

sealed interface DownloadOutcome {
    /** The verified file is in place. */
    data object Completed : DownloadOutcome

    /** The bytes did not match the expected SHA-256; all files were deleted. */
    data object ChecksumMismatch : DownloadOutcome

    /** Free space was short before the download, or the disk filled up while writing. The partial file is kept. */
    data object NotEnoughStorage : DownloadOutcome

    /** The server refused for good (HTTP 4xx other than 408 and 429); retrying would not help. */
    data class ServerError(val code: Int) : DownloadOutcome

    /** The connection failed. The partial file is kept so the next attempt resumes at its last byte. */
    data class Interrupted(val cause: IOException) : DownloadOutcome
}

/**
 * Downloads one file into [dir] resumably: bytes go to `<fileName>.part`, which is checked against
 * [sha256] and only then renamed to [fileName]. So [fileName] existing means a verified model.
 */
class ModelDownloader(
    private val dir: File,
    private val fileName: String,
    private val sha256: String,
    private val source: ModelSource,
    private val freeBytes: () -> Long,
    private val requiredFreeBytes: Long,
) {
    private val target get() = File(dir, fileName)
    private val part get() = File(dir, "$fileName.part")

    private companion object {
        /** HTTP 408 and 429 are worth retrying; so is every 5xx. */
        val RETRYABLE_CODES = setOf(408, 429)
    }

    fun isInstalled(): Boolean = target.isFile

    /** Deletes the model and any partial download. */
    fun remove() {
        target.delete()
        part.delete()
    }

    /** Bytes of the partial download so far (0 when there is none). */
    fun partialBytes(): Long = if (part.isFile) part.length() else 0L

    /** [onProgress] (suspending, so a worker can publish it) gets whole percents 0..100, each at most once and never going down. */
    suspend fun download(onProgress: suspend (Int) -> Unit): DownloadOutcome = withContext(Dispatchers.IO) {
        if (isInstalled()) return@withContext DownloadOutcome.Completed
        dir.mkdirs()
        val have = if (part.isFile) part.length() else 0L
        if (freeBytes() + have < requiredFreeBytes) return@withContext DownloadOutcome.NotEnoughStorage

        try {
            fetch(have, onProgress)
        } catch (e: HttpStatusException) {
            return@withContext if (e.code in RETRYABLE_CODES || e.code >= 500) DownloadOutcome.Interrupted(e) else DownloadOutcome.ServerError(e.code)
        } catch (e: IOException) {
            return@withContext if (e.isDiskFull()) DownloadOutcome.NotEnoughStorage else DownloadOutcome.Interrupted(e)
        }

        onProgress(100)
        val matches = try {
            sha256Of(part).equals(sha256, ignoreCase = true)
        } catch (e: FileNotFoundException) {
            // The partial file vanished while hashing: the model was removed, so this run is cancelled.
            throw CancellationException("The partial download was removed")
        }
        if (matches) {
            target.delete()
            if (!part.renameTo(target)) {
                return@withContext DownloadOutcome.Interrupted(IOException("Could not move the model into place"))
            }
            DownloadOutcome.Completed
        } else {
            remove()
            DownloadOutcome.ChecksumMismatch
        }
    }

    private suspend fun fetch(offset: Long, onProgress: suspend (Int) -> Unit) {
        source.open(offset).use { stream ->
            val start = if (stream.resumed) offset else 0L
            val total = start + stream.remaining
            RandomAccessFile(part, "rw").use { file ->
                file.setLength(start)
                file.seek(start)
                val buffer = ByteArray(64 * 1024)
                var written = start
                var lastPercent = -1
                while (true) {
                    coroutineContext.ensureActive()
                    val n = stream.input.read(buffer)
                    if (n < 0) break
                    file.write(buffer, 0, n)
                    written += n
                    val percent = if (total > 0) (written * 100 / total).toInt().coerceAtMost(99) else 0
                    if (percent > lastPercent) {
                        lastPercent = percent
                        onProgress(percent)
                    }
                }
                if (written < total) throw IOException("Connection closed after $written of $total bytes")
            }
        }
    }

    private fun IOException.isDiskFull() = message?.contains("No space left", ignoreCase = true) == true ||
        message?.contains("ENOSPC") == true

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
