package com.simmer.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.simmer.app.R
import com.simmer.app.domain.DownloadFailure
import com.simmer.app.domain.READER_REQUIRED_FREE_BYTES
import com.simmer.app.domain.ReaderModel
import com.simmer.app.domain.ReaderStatus
import com.simmer.app.domain.formatGigabytes

@Composable
fun readerModelSize(): String =
    formatGigabytes(ReaderModel.SIZE_BYTES, LocalConfiguration.current.locales[0])

@Composable
fun readerFreeSpaceNeeded(): String =
    formatGigabytes(READER_REQUIRED_FREE_BYTES, LocalConfiguration.current.locales[0])

/** The state line under "On this phone": one string per [ReaderStatus]. */
@Composable
fun readerStateText(status: ReaderStatus): String = when (status) {
    ReaderStatus.NotDownloaded -> stringResource(R.string.settings_reader_state, readerModelSize())
    is ReaderStatus.Downloading -> stringResource(
        if (status.waiting) R.string.settings_reader_state_waiting else R.string.settings_reader_state_downloading,
        status.percent,
    )
    ReaderStatus.Ready -> stringResource(R.string.settings_reader_state_ready)
    ReaderStatus.NotEnoughStorage -> stringResource(R.string.settings_reader_state_no_space, readerFreeSpaceNeeded())
    ReaderStatus.NotSupported -> stringResource(R.string.settings_reader_state_unsupported)
    is ReaderStatus.Failed -> when (status.reason) {
        DownloadFailure.ChecksumMismatch -> stringResource(R.string.settings_reader_state_corrupt)
        DownloadFailure.NotEnoughStorage -> stringResource(R.string.settings_reader_state_no_space, readerFreeSpaceNeeded())
        DownloadFailure.ServerError -> stringResource(R.string.settings_reader_state_server)
        DownloadFailure.Unknown -> stringResource(R.string.settings_reader_state_failed)
    }
}
