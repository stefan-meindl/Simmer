package com.simmer.app.data

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.simmer.app.domain.DEFAULT_APPLIANCES
import com.simmer.app.domain.KitchenChoice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "simmer_prefs")

/**
 * Repository for user preferences stored in DataStore.
 * Covers all settings from the design: lang, units, spices, appliances,
 * kitchen (appliances and the ones the user added), kitchenMode, theme, and readerWifiOnly.
 */
@Singleton
class PrefsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val store: DataStore<Preferences> = context.dataStore

    companion object {
        val KEY_LANG = stringPreferencesKey("lang")
        val KEY_UNITS = stringPreferencesKey("units")
        val KEY_SPICES = stringPreferencesKey("spices")
        val KEY_APPLIANCES = stringSetPreferencesKey("appliances")
        val KEY_CUSTOM_APPS = stringSetPreferencesKey("custom_apps")
        val KEY_KITCHEN_MODE = stringPreferencesKey("kitchen_mode")
        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_SHARE_ENGINE = stringPreferencesKey("share_engine")
        val KEY_IMPORT_STYLE = stringPreferencesKey("import_style")
        val KEY_READER_WIFI_ONLY = booleanPreferencesKey("reader_wifi_only")
    }

    /** "de" when the device language is German, else "en"; used until the user picks a language. */
    private fun deviceLang(): String =
        if (context.resources.configuration.locales[0].language == "de") "de" else "en"

    /** Language: "en" | "de". Defaults to the device language. */
    val lang: Flow<String> = store.data.map { it[KEY_LANG] ?: deviceLang() }

    /** Units: "metric" | "imperial". Defaults to "metric". */
    val units: Flow<String> = store.data.map { it[KEY_UNITS] ?: "metric" }

    /** Spices display: "spoons" | "grams". Defaults to "spoons". */
    val spices: Flow<String> = store.data.map { it[KEY_SPICES] ?: "spoons" }

    /** The owned appliances together with the ones the user added (shown in Your kitchen even when unticked). */
    val kitchen: Flow<KitchenChoice> = store.data.map {
        KitchenChoice(it[KEY_APPLIANCES] ?: DEFAULT_APPLIANCES, it[KEY_CUSTOM_APPS] ?: emptySet())
    }

    /** Appliance keys the user owns. Hob, Oven and Microwave until the user first saves a choice. */
    val appliances: Flow<Set<String>> = kitchen.map { it.selected }

    /** Kitchen mode: "free" | "medium" | "strict". Defaults to "medium". */
    val kitchenMode: Flow<String> = store.data.map { it[KEY_KITCHEN_MODE] ?: "medium" }

    /** Theme: "system" | "light" | "dark". Defaults to "system". */
    val theme: Flow<String> = store.data.map { it[KEY_THEME] ?: "system" }

    /** How a link shared from Instagram is turned into a recipe: "chatbot" | "phone". Defaults to "chatbot". */
    val shareEngine: Flow<String> = store.data.map { it[KEY_SHARE_ENGINE] ?: "chatbot" }

    /** How much the Reader may add on import: "faithful" | "liberty". Remembers the last choice; defaults to "faithful". */
    val importStyle: Flow<String> = store.data.map { it[KEY_IMPORT_STYLE] ?: "faithful" }

    /** Whether to download the AI model on Wi-Fi only. Defaults to true. */
    val readerWifiOnly: Flow<Boolean> = store.data.map { it[KEY_READER_WIFI_ONLY] ?: true }

    suspend fun setLang(value: String) = store.edit { it[KEY_LANG] = value }
    suspend fun setUnits(value: String) = store.edit { it[KEY_UNITS] = value }
    suspend fun setSpices(value: String) = store.edit { it[KEY_SPICES] = value }

    /** Writes both kitchen sets in one edit. */
    suspend fun setKitchen(choice: KitchenChoice) = store.edit {
        it[KEY_APPLIANCES] = choice.selected
        it[KEY_CUSTOM_APPS] = choice.custom
    }

    /** Back to the first-run kitchen: default appliances, nothing added. Used by tests. */
    @VisibleForTesting
    suspend fun resetKitchen() = store.edit {
        it.remove(KEY_APPLIANCES)
        it.remove(KEY_CUSTOM_APPS)
    }

    suspend fun setKitchenMode(value: String) = store.edit { it[KEY_KITCHEN_MODE] = value }
    suspend fun setTheme(value: String) = store.edit { it[KEY_THEME] = value }
    suspend fun setShareEngine(value: String) = store.edit { it[KEY_SHARE_ENGINE] = value }
    suspend fun setImportStyle(value: String) = store.edit { it[KEY_IMPORT_STYLE] = value }
    suspend fun setReaderWifiOnly(value: Boolean) = store.edit { it[KEY_READER_WIFI_ONLY] = value }
}
