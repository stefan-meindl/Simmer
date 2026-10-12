package com.simmer.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simmer.app.data.PrefsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Settings choices are written to DataStore and read back by a fresh repository (as after a restart). */
@RunWith(AndroidJUnit4::class)
class SettingsPrefsTest {

    @Test
    fun settingsChoicesPersistInDataStore(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = PrefsRepository(context)
        try {
            prefs.setLang("de")
            prefs.setUnits("imperial")
            prefs.setSpices("grams")
            prefs.setKitchenMode("strict")

            val reread = PrefsRepository(context)
            assertEquals("de", reread.lang.first())
            assertEquals("imperial", reread.units.first())
            assertEquals("grams", reread.spices.first())
            assertEquals("strict", reread.kitchenMode.first())
        } finally {
            prefs.setLang("en")
            prefs.setUnits("metric")
            prefs.setSpices("spoons")
            prefs.setKitchenMode("medium")
        }
    }
}
