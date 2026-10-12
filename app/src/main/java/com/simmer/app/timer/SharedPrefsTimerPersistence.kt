package com.simmer.app.timer

import android.content.Context
import com.simmer.app.domain.TimerPersistence
import com.simmer.app.domain.TimerState
import com.simmer.app.domain.TimerStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Timers in SharedPreferences. Read synchronously on creation and written on every change, because
 * the store is created inside the foreground service as well as the UI.
 */
class SharedPrefsTimerPersistence(context: Context) : TimerPersistence {
    private val prefs = context.getSharedPreferences("step_timers", Context.MODE_PRIVATE)

    @Serializable
    private class Saved(
        val key: String,
        val title: String,
        val durationSecs: Int,
        val status: String,
        val remainingMs: Long,
        val endAtMs: Long,
    )

    override fun load(): Map<String, TimerState> {
        val json = prefs.getString(KEY, null) ?: return emptyMap()
        return runCatching {
            Json.decodeFromString<List<Saved>>(json).associate {
                it.key to TimerState(
                    durationSecs = it.durationSecs,
                    status = TimerStatus.valueOf(it.status),
                    remainingMs = it.remainingMs,
                    endAtMs = it.endAtMs,
                    title = it.title,
                )
            }
        }.getOrDefault(emptyMap())
    }

    override fun save(timers: Map<String, TimerState>) {
        val saved = timers.map { (key, t) ->
            Saved(key, t.title, t.durationSecs, t.status.name, t.remainingMs, t.endAtMs)
        }
        prefs.edit().putString(KEY, Json.encodeToString(saved)).apply()
    }

    private companion object {
        const val KEY = "timers"
    }
}
