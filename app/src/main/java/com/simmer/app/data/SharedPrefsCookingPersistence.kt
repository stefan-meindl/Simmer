package com.simmer.app.data

import android.content.Context
import com.simmer.app.domain.CookingPersistence
import com.simmer.app.domain.CookingSnapshot
import com.simmer.app.domain.PlannedChoice
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Cooking Progress in SharedPreferences. Read synchronously on creation and written on every
 * change (like the StepTimers), so it is there before the first screen and survives process death.
 */
class SharedPrefsCookingPersistence(context: Context) : CookingPersistence {
    private val prefs = context.getSharedPreferences("cooking_progress", Context.MODE_PRIVATE)

    @Serializable
    private class Saved(
        val recipeId: String,
        val done: List<String> = emptyList(),
        val servings: Int? = null,
        val selection: String? = null,
    )

    override fun load(): CookingSnapshot {
        val json = prefs.getString(KEY, null) ?: return CookingSnapshot()
        return runCatching {
            val all = Json.decodeFromString<List<Saved>>(json)
            CookingSnapshot(
                done = all.filter { it.done.isNotEmpty() }.associate { it.recipeId to it.done.toSet() },
                choices = all.mapNotNull { s ->
                    if (s.servings == null || s.selection == null) null
                    else s.recipeId to PlannedChoice(s.servings, decodeSelection(s.selection))
                }.toMap(),
            )
        }.getOrDefault(CookingSnapshot())
    }

    override fun save(snapshot: CookingSnapshot) {
        val saved = (snapshot.done.keys + snapshot.choices.keys).map { id ->
            val choice = snapshot.choices[id]
            Saved(
                recipeId = id,
                done = snapshot.done[id].orEmpty().toList(),
                servings = choice?.servings,
                selection = choice?.let { encodeSelection(it.selection) },
            )
        }
        prefs.edit().putString(KEY, Json.encodeToString(saved)).apply()
    }

    private companion object {
        const val KEY = "cooking"
    }
}
