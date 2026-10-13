package com.simmer.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simmer.app.data.PersistedImport
import com.simmer.app.pipeline.SharedPrefsImportPersistence
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** The Pipeline job's record in SharedPreferences, read back by a new instance as after process death. */
@RunWith(AndroidJUnit4::class)
class ImportPersistenceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After fun clean() = SharedPrefsImportPersistence(context).save(null)

    @Test fun aRunningJobComesBackWithItsAttempts() {
        val running = PersistedImport.Running("Soup\nIngredients\n- 1 onion", "\n- rule", "de", attempts = 1)
        SharedPrefsImportPersistence(context).save(running)

        assertEquals(running, SharedPrefsImportPersistence(context).load())
    }

    @Test fun aFinishedRecipeComesBack() {
        SharedPrefsImportPersistence(context).save(PersistedImport.Ready("{\"id\":\"u1\"}"))

        assertEquals(PersistedImport.Ready("{\"id\":\"u1\"}"), SharedPrefsImportPersistence(context).load())
    }

    @Test fun clearingForgetsTheJob() {
        val store = SharedPrefsImportPersistence(context)
        store.save(PersistedImport.Running("x", "", "en", 0))
        store.save(null)

        assertNull(SharedPrefsImportPersistence(context).load())
    }
}
