package com.simmer.app.reader

import com.simmer.app.domain.RecipeReader

enum class ReaderEngine { Nano, Gemma }

/**
 * Picks the [RecipeReader] at runtime: Gemini Nano when ML Kit reports it Available, Downloadable or
 * Downloading, otherwise (or when the check fails) Gemma. [nano] is null when the build has no Nano reader.
 */
class ReaderSelector(
    private val availability: NanoAvailabilityCheck,
    private val nano: RecipeReader?,
    private val gemma: RecipeReader,
) {
    suspend fun engine(): ReaderEngine {
        if (nano == null) return ReaderEngine.Gemma
        return when (attempt { availability.check() }.getOrDefault(NanoAvailability.Unavailable)) {
            NanoAvailability.Available, NanoAvailability.Downloadable, NanoAvailability.Downloading -> ReaderEngine.Nano
            NanoAvailability.Unavailable -> ReaderEngine.Gemma
        }
    }

    suspend fun current(): RecipeReader = if (engine() == ReaderEngine.Nano) nano!! else gemma
}
