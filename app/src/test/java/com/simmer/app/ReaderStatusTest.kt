package com.simmer.app

import com.simmer.app.domain.DownloadFailure
import com.simmer.app.domain.DownloadWork
import com.simmer.app.domain.READER_REQUIRED_FREE_BYTES
import com.simmer.app.domain.ReaderStatus
import com.simmer.app.domain.readerStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/** Seam: [readerStatus], the one place that decides which Recipe reader state Settings shows. */
class ReaderStatusTest {

    private val plenty = 10_000_000_000L

    private fun status(
        supported: Boolean = true,
        installed: Boolean = false,
        work: DownloadWork = DownloadWork.Idle,
        freeBytes: Long = plenty,
    ) = readerStatus(supported, installed, work, freeBytes)

    @Test fun `nothing downloaded with room to spare is Not downloaded`() {
        assertEquals(ReaderStatus.NotDownloaded, status())
    }

    @Test fun `an installed model is Ready`() {
        assertEquals(ReaderStatus.Ready, status(installed = true))
    }

    @Test fun `Ready does not need free storage`() {
        assertEquals(ReaderStatus.Ready, status(installed = true, freeBytes = 0))
    }

    @Test fun `exactly 3_5 GB free is enough, one byte less is not`() {
        assertEquals(3_500_000_000L, READER_REQUIRED_FREE_BYTES)
        assertEquals(ReaderStatus.NotDownloaded, status(freeBytes = 3_500_000_000L))
        assertEquals(ReaderStatus.NotEnoughStorage, status(freeBytes = 3_499_999_999L))
    }

    @Test fun `a running download shows its percentage`() {
        assertEquals(ReaderStatus.Downloading(43, waiting = false), status(work = DownloadWork.Running(43)))
    }

    @Test fun `a download that waits for the network is flagged as waiting`() {
        assertEquals(ReaderStatus.Downloading(12, waiting = true), status(work = DownloadWork.Queued(12)))
    }

    @Test fun `a download keeps showing progress while the disk fills up under it`() {
        assertEquals(ReaderStatus.Downloading(80, waiting = false), status(work = DownloadWork.Running(80), freeBytes = 1_000))
    }

    @Test fun `an unsupported phone says so before anything else`() {
        assertEquals(ReaderStatus.NotSupported, status(supported = false))
        assertEquals(ReaderStatus.NotSupported, status(supported = false, freeBytes = 0))
    }

    @Test fun `a failed download shows the reason`() {
        assertEquals(
            ReaderStatus.Failed(DownloadFailure.ChecksumMismatch),
            status(work = DownloadWork.Failed(DownloadFailure.ChecksumMismatch)),
        )
    }

    @Test fun `a failed download on a full disk still reports the failure`() {
        assertEquals(
            ReaderStatus.Failed(DownloadFailure.ChecksumMismatch),
            status(work = DownloadWork.Failed(DownloadFailure.ChecksumMismatch), freeBytes = 0),
        )
    }

    @Test fun `a finished model wins over stale work info`() {
        assertEquals(ReaderStatus.Ready, status(installed = true, work = DownloadWork.Failed(DownloadFailure.ChecksumMismatch)))
    }
}
