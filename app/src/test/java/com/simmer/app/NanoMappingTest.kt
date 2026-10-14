package com.simmer.app

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.simmer.app.domain.DownloadFailure
import com.simmer.app.domain.DownloadProgress
import com.simmer.app.reader.NanoAvailability
import com.simmer.app.reader.NanoBusyException
import com.simmer.app.reader.asNanoFailure
import com.simmer.app.reader.downloadProgressOf
import com.simmer.app.reader.nanoAvailabilityOf
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class NanoMappingTest {
    @Test
    fun featureStatusMapsToAvailability() {
        assertEquals(NanoAvailability.Available, nanoAvailabilityOf(FeatureStatus.AVAILABLE))
        assertEquals(NanoAvailability.Downloadable, nanoAvailabilityOf(FeatureStatus.DOWNLOADABLE))
        assertEquals(NanoAvailability.Downloading, nanoAvailabilityOf(FeatureStatus.DOWNLOADING))
        assertEquals(NanoAvailability.Unavailable, nanoAvailabilityOf(FeatureStatus.UNAVAILABLE))
        assertEquals(NanoAvailability.Unavailable, nanoAvailabilityOf(-1))
    }

    @Test
    fun downloadStatusMapsToProgressWithoutAPercentage() {
        val running = DownloadProgress.Running(percent = null, waiting = false)
        assertEquals(running, downloadProgressOf(DownloadStatus.DownloadStarted(1_000)))
        assertEquals(running, downloadProgressOf(DownloadStatus.DownloadProgress(500)))
        assertEquals(DownloadProgress.Complete, downloadProgressOf(DownloadStatus.DownloadCompleted))
        val failed = DownloadStatus.DownloadFailed(GenAiException(RuntimeException("x"), GenAiException.ErrorCode.NEEDS_SYSTEM_UPDATE))
        assertEquals(DownloadProgress.Failed(DownloadFailure.Unknown), downloadProgressOf(failed))
    }

    @Test
    fun busyBecomesNanoBusyWithTheRetryHint() {
        val hinted = GenAiException(RuntimeException(), GenAiException.ErrorCode.BUSY, Duration.ofMillis(3_000)).asNanoFailure()
        assertEquals(3_000L, (hinted as NanoBusyException).retryAfterMs)
        val unhinted = GenAiException(RuntimeException(), GenAiException.ErrorCode.BUSY).asNanoFailure()
        assertNull((unhinted as NanoBusyException).retryAfterMs)
    }

    @Test
    fun otherErrorsPassThrough() {
        val other = GenAiException(RuntimeException(), GenAiException.ErrorCode.NEEDS_SYSTEM_UPDATE)
        assertSame(other, other.asNanoFailure())
        val plain = IllegalStateException("boom")
        assertSame(plain, plain.asNanoFailure())
    }
}
