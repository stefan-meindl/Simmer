package com.simmer.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simmer.app.data.AppDatabase
import com.simmer.app.data.MIGRATION_1_2
import com.simmer.app.data.ShoppingRepository
import com.simmer.app.domain.PlannedChoice
import com.simmer.app.domain.ShoppingEntry
import com.simmer.app.domain.model.Selection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The Shopping list survives closing and reopening the database (an app restart), on real SQLite. */
@RunWith(AndroidJUnit4::class)
class ShoppingPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "shopping-persistence-test.db"

    private fun open() =
        Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(MIGRATION_1_2).build()

    private fun withDb(block: suspend (AppDatabase) -> Unit) = runBlocking {
        val db = open()
        try { block(db) } finally { db.close() }
    }

    @Before fun clean() {
        context.deleteDatabase(name)
    }

    @After fun cleanUp() {
        context.deleteDatabase(name)
    }

    private fun entry(id: String, aisle: String, amount: String = "1") =
        ShoppingEntry(id, id.replaceFirstChar { it.uppercase() }, amount, aisle, "Chilli con carne")

    @Test fun recipeAndManualItemsSurviveRestartAndReAddingReplaces() {
        val choice = PlannedChoice(4, Selection(emptyMap(), emptyMap()))
        withDb { db ->
            val repo = ShoppingRepository(db.shoppingDao())
            repo.addRecipeToList("chilli", listOf(entry("onions", "Produce"), entry("rice", "Pantry")), choice)
            repo.addManualItem("Batteries")
            repo.setChecked("chilli-rice", true)
        }

        withDb { db ->
            val repo = ShoppingRepository(db.shoppingDao())
            val items = repo.items.first().associateBy { it.name }
            assertEquals(setOf("Onions", "Rice", "Batteries"), items.keys)
            assertEquals(true, items.getValue("Rice").checked)
            assertEquals("Produce", items.getValue("Onions").aisle)

            repo.addRecipeToList("chilli", listOf(entry("onions", "Produce", "6")), choice)
            val after = repo.items.first().associateBy { it.name }
            assertEquals(setOf("Onions", "Batteries"), after.keys)
            assertEquals("6", after.getValue("Onions").amount)
        }
    }

    @Test fun clearAllAndRestoreRoundTripOnRealSqlite() {
        val choice = PlannedChoice(4, Selection(emptyMap(), emptyMap()))
        withDb { db ->
            val repo = ShoppingRepository(db.shoppingDao())
            repo.addRecipeToList("chilli", listOf(entry("onions", "Produce"), entry("rice", "Pantry")), choice)
            repo.addManualItem("Batteries")
            repo.setChecked("chilli-rice", true)

            val ticked = repo.clearTicked()
            assertEquals(1, ticked.items.size)
            assertEquals(setOf("Onions", "Batteries"), repo.items.first().map { it.name }.toSet())
            assertEquals(choice, repo.getPlanned("chilli"))

            repo.restore(ticked)
            val all = repo.clearAll()
            assertEquals(0, repo.items.first().size)
            assertEquals(null, repo.getPlanned("chilli"))

            repo.restore(all)
            assertEquals(true, repo.items.first().first { it.name == "Rice" }.checked)
            assertEquals(choice, repo.getPlanned("chilli"))
        }
    }
}
