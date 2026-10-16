package com.simmer.app.pipeline

import android.content.Context
import com.simmer.app.data.ImportPersistence
import com.simmer.app.data.PersistedImport
import com.simmer.app.domain.ImportRequest
import com.simmer.app.domain.ImportStyle
import com.simmer.app.domain.KitchenMode

/** Keeps the running or finished conversion in SharedPreferences, so it survives process death. */
class SharedPrefsImportPersistence(context: Context) : ImportPersistence {
    private val prefs = context.getSharedPreferences("import_job", Context.MODE_PRIVATE)

    override fun load(): PersistedImport? = when (prefs.getString(KEY_KIND, null)) {
        KIND_RUNNING -> PersistedImport.Running(
            request = ImportRequest(
                text = prefs.getString(KEY_TEXT, "").orEmpty(),
                lang = prefs.getString(KEY_LANG, "en").orEmpty(),
                appliances = prefs.getStringSet(KEY_APPLIANCES, emptySet()).orEmpty().toSet(),
                kitchenMode = KitchenMode.fromKey(prefs.getString(KEY_MODE, null)),
                style = ImportStyle.fromKey(prefs.getString(KEY_STYLE, null)),
            ),
            attempts = prefs.getInt(KEY_ATTEMPTS, 0),
        )
        KIND_READY -> prefs.getString(KEY_RECIPE, null)?.let { PersistedImport.Ready(it) }
        else -> null
    }

    override fun save(value: PersistedImport?) {
        val edit = prefs.edit().clear()
        when (value) {
            null -> Unit
            is PersistedImport.Running -> edit
                .putString(KEY_KIND, KIND_RUNNING)
                .putString(KEY_TEXT, value.request.text)
                .putString(KEY_LANG, value.request.lang)
                .putStringSet(KEY_APPLIANCES, value.request.appliances)
                .putString(KEY_MODE, value.request.kitchenMode.key)
                .putString(KEY_STYLE, value.request.style.key)
                .putInt(KEY_ATTEMPTS, value.attempts)
            is PersistedImport.Ready -> edit.putString(KEY_KIND, KIND_READY).putString(KEY_RECIPE, value.recipeJson)
        }
        // Commit, not apply: a process kill right after must find the record.
        edit.commit()
    }

    private companion object {
        const val KEY_KIND = "kind"
        const val KIND_RUNNING = "running"
        const val KIND_READY = "ready"
        const val KEY_TEXT = "text"
        const val KEY_APPLIANCES = "appliances"
        const val KEY_MODE = "mode"
        const val KEY_STYLE = "style"
        const val KEY_LANG = "lang"
        const val KEY_ATTEMPTS = "attempts"
        const val KEY_RECIPE = "recipe"
    }
}
