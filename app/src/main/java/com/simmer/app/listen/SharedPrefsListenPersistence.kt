package com.simmer.app.listen

import android.content.Context
import com.simmer.app.data.ListenPersistence
import com.simmer.app.data.PersistedListen

/** Keeps the Listen run's phase (never audio) in SharedPreferences, so it survives process death. */
class SharedPrefsListenPersistence(context: Context) : ListenPersistence {
    private val prefs = context.getSharedPreferences("listen_session", Context.MODE_PRIVATE)

    override fun load(): PersistedListen? = when (prefs.getString(KEY_KIND, null)) {
        KIND_CAPTURING -> PersistedListen.Capturing
        KIND_TRANSCRIBING -> PersistedListen.Transcribing
        KIND_DONE -> prefs.getString(KEY_TRANSCRIPT, null)?.let { PersistedListen.Done(it) }
        else -> null
    }

    override fun save(value: PersistedListen?) {
        val edit = prefs.edit().clear()
        when (value) {
            null -> Unit
            PersistedListen.Capturing -> edit.putString(KEY_KIND, KIND_CAPTURING)
            PersistedListen.Transcribing -> edit.putString(KEY_KIND, KIND_TRANSCRIBING)
            is PersistedListen.Done -> edit.putString(KEY_KIND, KIND_DONE).putString(KEY_TRANSCRIPT, value.transcript)
        }
        // apply() updates memory at once and writes in the background (flushed when the service stops or the app
        // pauses), so the main thread never waits for the disk.
        edit.apply()
    }

    private companion object {
        const val KEY_KIND = "kind"
        const val KIND_CAPTURING = "capturing"
        const val KIND_TRANSCRIBING = "transcribing"
        const val KIND_DONE = "done"
        const val KEY_TRANSCRIPT = "transcript"
    }
}
