package com.simmer.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.simmer.app.R
import com.simmer.app.domain.DownloadFailure
import com.simmer.app.domain.READER_REQUIRED_FREE_BYTES
import com.simmer.app.domain.ReaderModel
import com.simmer.app.domain.ReaderStatus
import com.simmer.app.domain.formatGigabytes
import com.simmer.app.reader.ReaderEngine

@Composable
fun readerModelSize(): String =
    formatGigabytes(ReaderModel.SIZE_BYTES, LocalConfiguration.current.locales[0])

@Composable
fun readerFreeSpaceNeeded(): String =
    formatGigabytes(READER_REQUIRED_FREE_BYTES, LocalConfiguration.current.locales[0])

/**
 * Everything in Settings that differs per [ReaderEngine], in one place. Gemini Nano is the system's
 * model: nothing to cancel, remove or limit to Wi-Fi, and no size to announce.
 */
class ReaderEngineUi(
    @StringRes val whereRes: Int,
    @StringRes val notDownloadedRes: Int,
    /** Cancel, Remove and "Download on Wi-Fi only" apply only to a model file Simmer manages. */
    val ownsModelFile: Boolean,
)

fun ReaderEngine.ui(): ReaderEngineUi = when (this) {
    ReaderEngine.Gemma -> ReaderEngineUi(R.string.settings_reader_where, R.string.settings_reader_state, ownsModelFile = true)
    ReaderEngine.Nano -> ReaderEngineUi(R.string.settings_reader_where_nano, R.string.settings_reader_state_nano, ownsModelFile = false)
}

/** The state line under "On this phone": one string per [ReaderStatus]. */
@Composable
fun readerStateText(status: ReaderStatus, engine: ReaderEngine): String = when (status) {
    // The Nano string has no size placeholder; the extra argument is ignored.
    ReaderStatus.NotDownloaded -> stringResource(engine.ui().notDownloadedRes, readerModelSize())
    is ReaderStatus.Downloading -> when {
        status.percent == null -> stringResource(R.string.settings_reader_state_downloading_unknown)
        status.waiting -> stringResource(R.string.settings_reader_state_waiting, status.percent)
        else -> stringResource(R.string.settings_reader_state_downloading, status.percent)
    }
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
