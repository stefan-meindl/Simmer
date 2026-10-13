package com.simmer.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simmer.app.data.SharedPrefsCookingPersistence
import com.simmer.app.domain.CookingProgressStore
import com.simmer.app.domain.PlannedChoice
import com.simmer.app.domain.model.Selection
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Cooking Progress and its choice are written to SharedPreferences and read back by a fresh store (as after process death). */
@RunWith(AndroidJUnit4::class)
class CookingPersistenceTest {

    @Test
    fun progressAndChoiceSurviveANewStore() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val choice = PlannedChoice(6, Selection(choice = mapOf("mince" to "plant"), add = mapOf("choc" to true)))
        val first = CookingProgressStore(SharedPrefsCookingPersistence(context))
        try {
            first.setChoice("test-recipe", choice)
            first.markDone("test-recipe", "p-veg")
            first.markDone("test-recipe", "c-simmer")
            first.undo("test-recipe", "p-veg")

            val reread = CookingProgressStore(SharedPrefsCookingPersistence(context))
            assertEquals(setOf("c-simmer"), reread.doneSteps("test-recipe"))
            assertEquals(choice, reread.choice("test-recipe"))
        } finally {
            first.reset("test-recipe")
        }
    }
}
