package com.simmer.app.reader

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.simmer.app.domain.DownloadFailure
import com.simmer.app.domain.DownloadProgress

/** ML Kit's `FeatureStatus` as [NanoAvailability]; anything unknown counts as Unavailable. */
internal fun nanoAvailabilityOf(featureStatus: Int): NanoAvailability = when (featureStatus) {
    FeatureStatus.AVAILABLE -> NanoAvailability.Available
    FeatureStatus.DOWNLOADABLE -> NanoAvailability.Downloadable
    FeatureStatus.DOWNLOADING -> NanoAvailability.Downloading
    else -> NanoAvailability.Unavailable
}

/** ML Kit reports bytes without a total, so a running download has no percentage (null). */
internal fun downloadProgressOf(status: DownloadStatus): DownloadProgress = when (status) {
    is DownloadStatus.DownloadFailed -> DownloadProgress.Failed(DownloadFailure.Unknown)
    DownloadStatus.DownloadCompleted -> DownloadProgress.Complete
    else -> DownloadProgress.Running(percent = null, waiting = false)
}

/** `ErrorCode.BUSY` (per-app quota) becomes [NanoBusyException], with the service's retry hint (null when absent or zero, which ML Kit uses for "no hint"); anything else is unchanged. */
internal fun Throwable.asNanoFailure(): Throwable =
    if (this is GenAiException && errorCode == GenAiException.ErrorCode.BUSY) {
        NanoBusyException(retryDelay?.toMillis()?.takeIf { it > 0 })
    } else {
        this
    }
