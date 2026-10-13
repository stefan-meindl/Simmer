package com.simmer.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simmer.app.data.PrefsRepository
import com.simmer.app.domain.KitchenChoice
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Your kitchen choices live in DataStore: defaults on first run, written on each change, read back after a restart. */
@RunWith(AndroidJUnit4::class)
class YourKitchenPrefsTest {

    @Test
    fun firstRunDefaultsThenChoicesPersist(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = PrefsRepository(context)
        try {
            prefs.resetKitchen()
            assertEquals(KitchenChoice(setOf("hob", "oven", "microwave"), emptySet()), prefs.kitchen.first())
            assertEquals("medium", prefs.kitchenMode.first())

            // Unticking everything is a choice, not a first run: the defaults must not come back.
            prefs.setKitchen(KitchenChoice(emptySet(), setOf("Bamix")))
            val reread = PrefsRepository(context)
            assertEquals(KitchenChoice(emptySet(), setOf("Bamix")), reread.kitchen.first())

            prefs.setKitchen(KitchenChoice(setOf("wok", "c:Bamix"), setOf("Bamix")))
            assertEquals(setOf("wok", "c:Bamix"), PrefsRepository(context).appliances.first())
        } finally {
            prefs.resetKitchen()
        }
    }
}
