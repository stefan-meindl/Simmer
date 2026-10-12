package com.simmer.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simmer.app.data.AppDatabase
import com.simmer.app.data.RecipeEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The Recipes list order comes from the DAO, on real SQLite: User Recipes first, newest first, then built-ins. */
@RunWith(AndroidJUnit4::class)
class RecipeOrderTest {
    private lateinit var db: AppDatabase

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
    }

    @After fun close() = db.close()

    private fun row(id: String, mine: Boolean, json: String = "{}") = RecipeEntity(id, json, mine = mine, edited = false)

    private fun ids() = runBlocking { db.recipeDao().observeAll().first().map { it.id } }

    @Test fun userRecipesComeFirstNewestFirstThenBuiltInsInSeedOrder() = runBlocking {
        val dao = db.recipeDao()
        dao.upsert(row("chilli", mine = false))
        dao.upsert(row("curry", mine = false))
        dao.upsert(row("u1", mine = true))
        dao.upsert(row("u2", mine = true))

        assertEquals(listOf("u2", "u1", "chilli", "curry"), ids())
    }

    @Test fun editingARecipeDoesNotMoveIt() = runBlocking {
        val dao = db.recipeDao()
        dao.upsert(row("chilli", mine = false))
        dao.upsert(row("curry", mine = false))
        dao.upsert(row("u1", mine = true))
        dao.upsert(row("u2", mine = true))

        dao.upsert(row("u1", mine = true, json = """{"edited":true}"""))
        dao.upsert(row("chilli", mine = false, json = """{"edited":true}"""))

        assertEquals(listOf("u2", "u1", "chilli", "curry"), ids())
    }
}
