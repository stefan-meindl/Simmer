package com.simmer.app.domain

import kotlinx.coroutines.flow.Flow

/** Progress of the Reader model download, as [RecipeReader.download] reports it. */
sealed interface DownloadProgress {
    /** [percent] is null when the engine cannot tell (Gemini Nano). [waiting] is true while the task waits for a connection. */
    data class Running(val percent: Int?, val waiting: Boolean) : DownloadProgress
    data object Complete : DownloadProgress
    data class Failed(val reason: DownloadFailure) : DownloadProgress
}

/** An on-device AI engine that turns recipe text into structured output. */
interface RecipeReader {
    suspend fun status(): ReaderStatus

    /** Starts (or resumes) the model download and ends with [DownloadProgress.Complete] or [DownloadProgress.Failed]. */
    fun download(): Flow<DownloadProgress>

    /** Streams the reply tokens; the model is loaded on first use. */
    suspend fun generate(system: String, user: String, maxTokens: Int): Flow<String>
}
