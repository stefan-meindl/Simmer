package com.simmer.app.reader

import com.simmer.app.domain.RecipeReader

/**
 * Picks the [RecipeReader] at runtime: Gemini Nano when ML Kit reports it Available or Downloadable,
 * otherwise (or when the check fails) Gemma.
 *
 * SEAM: [nano] is null until a GeminiNanoReader exists (not part of #16), so today every phone gets Gemma.
 */
class ReaderSelector(
    private val availability: NanoAvailabilityCheck,
    private val nano: RecipeReader?,
    private val gemma: RecipeReader,
) {
    suspend fun current(): RecipeReader {
        if (nano == null) return gemma
        val nanoAvailability = attempt { availability.check() }.getOrDefault(NanoAvailability.Unavailable)
        return when (nanoAvailability) {
            NanoAvailability.Available, NanoAvailability.Downloadable -> nano
            NanoAvailability.Unavailable -> gemma
        }
    }
}
