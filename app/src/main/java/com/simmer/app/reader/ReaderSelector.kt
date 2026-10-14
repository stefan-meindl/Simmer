package com.simmer.app.reader

import com.simmer.app.domain.RecipeReader

enum class ReaderEngine { Nano, Gemma }

/**
 * Picks the [RecipeReader] at runtime: Gemini Nano when ML Kit reports it Available or Downloadable,
 * otherwise (Unavailable, Downloading, or when the check fails) Gemma.
 */
class ReaderSelector(
    private val availability: NanoAvailabilityCheck,
    private val nano: RecipeReader,
    private val gemma: RecipeReader,
) {
    /** The chosen [engine] and its [reader]. */
    data class Selection(val engine: ReaderEngine, val reader: RecipeReader)

    suspend fun select(): Selection =
        when (attempt { availability.check() }.getOrDefault(NanoAvailability.Unavailable)) {
            NanoAvailability.Available, NanoAvailability.Downloadable -> Selection(ReaderEngine.Nano, nano)
            NanoAvailability.Downloading, NanoAvailability.Unavailable -> Selection(ReaderEngine.Gemma, gemma)
        }

    suspend fun engine(): ReaderEngine = select().engine

    suspend fun current(): RecipeReader = select().reader
}
