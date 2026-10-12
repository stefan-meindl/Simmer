package com.simmer.app

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simmer.app.ui.nav.AddRecipe
import com.simmer.app.ui.nav.EditRecipe
import com.simmer.app.ui.nav.RECIPE_SAVED_KEY
import com.simmer.app.ui.nav.RecipesTab
import com.simmer.app.ui.nav.SettingsTab
import com.simmer.app.ui.nav.ShoppingListTab
import com.simmer.app.ui.nav.finishAddRecipe
import com.simmer.app.ui.nav.navigateToTab
import com.simmer.app.ui.nav.openSettingsOnTop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The seam is the nav helpers the app uses (tab bar, Add recipe) on a graph with the real routes and
 * stub screens: what matters is the back stack, not the screens.
 */
@RunWith(AndroidJUnit4::class)
class TabNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var nav: NavHostController

    @Before
    fun setUp() {
        compose.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = RecipesTab) {
                composable<RecipesTab> { Text("recipes") }
                composable<ShoppingListTab> { Text("shopping") }
                composable<SettingsTab> { Text("settings") }
                composable<AddRecipe> { Text("add") }
                composable<EditRecipe> { Text("edit") }
            }
        }
        compose.waitForIdle()
    }

    private fun act(block: NavHostController.() -> Unit) {
        compose.runOnUiThread { nav.block() }
        compose.waitForIdle()
    }

    /** Route names of the back stack, bottom first (the nav graph itself left out). */
    private fun stack(): List<String> = compose.runOnIdle {
        nav.currentBackStack.value
            .mapNotNull { it.destination.route }
            .map { it.substringAfterLast('.').substringBefore('/') }
            .filter { it.endsWith("Tab") || it == "AddRecipe" || it == "EditRecipe" }
    }

    private fun addRecipeWithSettingsOnTop() {
        act { navigate(AddRecipe) }
        act { openSettingsOnTop() }
        assertEquals(listOf("RecipesTab", "AddRecipe", "SettingsTab"), stack())
    }

    @Test
    fun shoppingTabSwitchesWhileSettingsIsOpenOnAddRecipe() {
        addRecipeWithSettingsOnTop()
        act { navigateToTab(ShoppingListTab) }
        assertEquals(listOf("RecipesTab", "ShoppingListTab"), stack())
    }

    @Test
    fun recipesTabSwitchesWhileSettingsIsOpenOnAddRecipe() {
        addRecipeWithSettingsOnTop()
        act { navigateToTab(RecipesTab) }
        assertEquals(listOf("RecipesTab"), stack())
    }

    @Test
    fun switchingTabAbandonsAddRecipeForGood() {
        addRecipeWithSettingsOnTop()
        act { navigateToTab(ShoppingListTab) }
        act { navigateToTab(RecipesTab) }
        assertEquals(listOf("RecipesTab"), stack())
        act { navigateToTab(SettingsTab) }
        assertEquals(listOf("RecipesTab", "SettingsTab"), stack())
    }

    @Test
    fun backFromSettingsReturnsToAddRecipe() {
        addRecipeWithSettingsOnTop()
        act { popBackStack() }
        assertEquals(listOf("RecipesTab", "AddRecipe"), stack())
    }

    @Test
    fun tappingTheShownSettingsTabKeepsAddRecipeBelow() {
        addRecipeWithSettingsOnTop()
        act { navigateToTab(SettingsTab) }
        assertEquals(listOf("RecipesTab", "AddRecipe", "SettingsTab"), stack())
    }

    @Test
    fun tabsStillHopBetweenEachOther() {
        act { navigateToTab(ShoppingListTab) }
        act { navigateToTab(SettingsTab) }
        assertEquals(listOf("RecipesTab", "SettingsTab"), stack())
        act { navigateToTab(RecipesTab) }
        assertEquals(listOf("RecipesTab"), stack())
    }

    @Test
    fun saveRecipeLandsOnRecipesAndFlagsIt() {
        act { navigate(AddRecipe) }
        act { finishAddRecipe() }
        assertEquals(listOf("RecipesTab"), stack())
        val flagged = compose.runOnIdle {
            nav.getBackStackEntry<RecipesTab>().savedStateHandle.get<Boolean>(RECIPE_SAVED_KEY)
        }
        assertTrue(flagged == true)
    }
}
