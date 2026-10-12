package com.simmer.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "simmer_prefs")

/**
 * Repository for user preferences stored in DataStore.
 * Covers all settings from the design: lang, units, spices, appliances,
 * customApps, kitchenMode, theme, and readerWifiOnly.
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

    /** Set of appliance keys the user owns. */
    val appliances: Flow<Set<String>> = store.data.map { it[KEY_APPLIANCES] ?: emptySet() }

    /** Set of custom appliance names added by the user. */
    val customApps: Flow<Set<String>> = store.data.map { it[KEY_CUSTOM_APPS] ?: emptySet() }

    /** Kitchen mode: "free" | "medium" | "strict". Defaults to "medium". */
    val kitchenMode: Flow<String> = store.data.map { it[KEY_KITCHEN_MODE] ?: "medium" }

    /** Theme: "system" | "light" | "dark". Defaults to "system". */
    val theme: Flow<String> = store.data.map { it[KEY_THEME] ?: "system" }

    /** Whether to download the AI model on Wi-Fi only. Defaults to true. */
    val readerWifiOnly: Flow<Boolean> = store.data.map { it[KEY_READER_WIFI_ONLY] ?: true }

    suspend fun setLang(value: String) = store.edit { it[KEY_LANG] = value }
    suspend fun setUnits(value: String) = store.edit { it[KEY_UNITS] = value }
    suspend fun setSpices(value: String) = store.edit { it[KEY_SPICES] = value }
    suspend fun setAppliances(value: Set<String>) = store.edit { it[KEY_APPLIANCES] = value }
    suspend fun setCustomApps(value: Set<String>) = store.edit { it[KEY_CUSTOM_APPS] = value }
    suspend fun setKitchenMode(value: String) = store.edit { it[KEY_KITCHEN_MODE] = value }
    suspend fun setTheme(value: String) = store.edit { it[KEY_THEME] = value }
    suspend fun setReaderWifiOnly(value: Boolean) = store.edit { it[KEY_READER_WIFI_ONLY] = value }
}
