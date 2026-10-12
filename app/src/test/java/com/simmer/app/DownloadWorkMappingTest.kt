package com.simmer.app

import androidx.work.WorkInfo
import com.simmer.app.data.toDownloadWork
import com.simmer.app.domain.DownloadFailure
import com.simmer.app.domain.DownloadWork
import com.simmer.app.domain.formatGigabytes
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** Seam: [toDownloadWork], the WorkManager state to [DownloadWork] mapping, and the size text. */
class DownloadWorkMappingTest {

    @Test fun `states map to the download work the status reads`() {
        assertEquals(DownloadWork.Idle, toDownloadWork(null, 0, null))
        assertEquals(DownloadWork.Idle, toDownloadWork(WorkInfo.State.SUCCEEDED, 100, null))
        assertEquals(DownloadWork.Idle, toDownloadWork(WorkInfo.State.CANCELLED, 40, null))
        assertEquals(DownloadWork.Running(40), toDownloadWork(WorkInfo.State.RUNNING, 40, null))
        assertEquals(DownloadWork.Queued(40), toDownloadWork(WorkInfo.State.ENQUEUED, 40, null))
        assertEquals(DownloadWork.Queued(0), toDownloadWork(WorkInfo.State.BLOCKED, 0, null))
    }

    @Test fun `a failed task carries its stored reason`() {
        assertEquals(DownloadWork.Failed(DownloadFailure.ServerError), toDownloadWork(WorkInfo.State.FAILED, 0, "ServerError"))
        assertEquals(DownloadWork.Failed(DownloadFailure.NotEnoughStorage), toDownloadWork(WorkInfo.State.FAILED, 0, "NotEnoughStorage"))
    }

    @Test fun `an unknown or missing failure name is a generic failure, not a checksum mismatch`() {
        assertEquals(DownloadWork.Failed(DownloadFailure.Unknown), toDownloadWork(WorkInfo.State.FAILED, 0, "Gone"))
        assertEquals(DownloadWork.Failed(DownloadFailure.Unknown), toDownloadWork(WorkInfo.State.FAILED, 0, null))
    }

    @Test fun `sizes show one decimal in the locale`() {
        assertEquals("2.0 GB", formatGigabytes(2_008_432_640L, Locale.US))
        assertEquals("3,5 GB", formatGigabytes(3_500_000_000L, Locale.GERMANY))
    }
}
