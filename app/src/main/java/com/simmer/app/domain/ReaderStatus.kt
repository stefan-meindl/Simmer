package com.simmer.app.domain

/** The Gemma 4 E2B model file the Recipe reader runs on, as published on Hugging Face. */
object ReaderModel {
    const val NAME = "Gemma 4 E2B"
    const val FILE_NAME = "gemma-4-E2B-it-web.litertlm"

    /** Pinned to a commit of litert-community/gemma-4-E2B-it-litert-lm so [SHA256] always matches the bytes. */
    const val URL =
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/" +
            "b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1/gemma-4-E2B-it-web.litertlm"

    /** The file's LFS object id (X-Linked-ETag on Hugging Face). */
    const val SHA256 = "3a08e8d94e23b814ae5414469c370c503813949acb8ceaa17e4ebf8a35af35b5"

    /** Size in bytes, used for the percentage when the server does not report one. */
    const val SIZE_BYTES = 2_008_432_640L
}

/** Free space the download needs before it starts (model plus working room). */
const val READER_REQUIRED_FREE_BYTES = 3_500_000_000L

/** Why a download ended without a usable model. */
enum class DownloadFailure { ChecksumMismatch, NotEnoughStorage }

/** What WorkManager says about the download task, as far as the status cares. */
sealed interface DownloadWork {
    data object Idle : DownloadWork

    /** Enqueued but not running: waiting for Wi-Fi / network, or between retries. */
    data class Queued(val percent: Int) : DownloadWork
    data class Running(val percent: Int) : DownloadWork
    data class Failed(val reason: DownloadFailure) : DownloadWork
}

/** Which state the Settings "Recipe reader" section shows. */
sealed interface ReaderStatus {
    data object NotDownloaded : ReaderStatus

    /** [waiting] is true while the task waits for a connection (Wi-Fi when "Wi-Fi only" is on). */
    data class Downloading(val percent: Int, val waiting: Boolean) : ReaderStatus
    data object Ready : ReaderStatus
    data object NotEnoughStorage : ReaderStatus
    data object NotSupported : ReaderStatus
    data class Failed(val reason: DownloadFailure) : ReaderStatus
}

/**
 * Derives the [ReaderStatus]. [installed] means the verified model file exists. A download in
 * progress never turns into "not enough storage" just because its own partial file used the space.
 */
fun readerStatus(supported: Boolean, installed: Boolean, work: DownloadWork, freeBytes: Long): ReaderStatus = when {
    !supported -> ReaderStatus.NotSupported
    installed -> ReaderStatus.Ready
    work is DownloadWork.Running -> ReaderStatus.Downloading(work.percent, waiting = false)
    work is DownloadWork.Queued -> ReaderStatus.Downloading(work.percent, waiting = true)
    work is DownloadWork.Failed -> ReaderStatus.Failed(work.reason)
    freeBytes < READER_REQUIRED_FREE_BYTES -> ReaderStatus.NotEnoughStorage
    else -> ReaderStatus.NotDownloaded
}
