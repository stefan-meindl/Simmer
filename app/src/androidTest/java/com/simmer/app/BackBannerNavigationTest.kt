package com.simmer.app

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simmer.app.ui.nav.AddRecipe
import com.simmer.app.ui.nav.BackBannerFrame
import com.simmer.app.ui.nav.EditRecipe
import com.simmer.app.ui.nav.RecipeFlow
import com.simmer.app.ui.nav.RecipesTab
import com.simmer.app.ui.nav.SettingsTab
import com.simmer.app.ui.nav.ShoppingListTab
import com.simmer.app.ui.nav.YourKitchen
import com.simmer.app.ui.nav.navigateToTab
import com.simmer.app.ui.nav.openSettingsOnTop
import com.simmer.app.ui.nav.openShoppingFromFlow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The seam is the real routes, nav helpers and [BackBannerFrame] on stub screens: the back stack and the
 * banner are what matter. The stub Add recipe and recipe flow keep a counter in saved state, standing for
 * the draft text and the flow selection.
 */
@RunWith(AndroidJUnit4::class)
class BackBannerNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var nav: NavHostController

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
    private val toCreator get() = string(R.string.back_to_recipe_creator)
    private val toRecipe get() = string(R.string.back_to_recipe)

    @Before
    fun setUp() {
        compose.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = RecipesTab) {
                composable<RecipesTab> { Tab(it, "recipes") }
                composable<ShoppingListTab> { Tab(it, "shopping") }
                composable<SettingsTab> { Tab(it, "settings") }
                composable<AddRecipe> { Counter("add") }
                composable<RecipeFlow> { Counter("flow") }
                composable<EditRecipe> { Text("edit") }
                composable<YourKitchen> { Text("kitchen") }
            }
        }
        compose.waitForIdle()
    }

    /** A tab screen framed like the real app: its own Back banner above it. */
    @androidx.compose.runtime.Composable
    private fun Tab(entry: NavBackStackEntry, name: String) {
        BackBannerFrame(nav, entry) { Text(name) }
    }

    @androidx.compose.runtime.Composable
    private fun Counter(name: String) {
        var n by rememberSaveable { mutableIntStateOf(0) }
        Column {
            Button(onClick = { n++ }) { Text("$name $n") }
        }
    }

    private fun act(block: NavHostController.() -> Unit) {
        compose.runOnUiThread { nav.block() }
        compose.waitForIdle()
    }

    private fun stack(): List<String> = compose.runOnIdle {
        nav.currentBackStack.value
            .mapNotNull { it.destination.route }
            .map { it.substringAfterLast('.').substringBefore('/').substringBefore('?') }
            .filter { it != "(nav graph)" && !it.startsWith("route=") && it.isNotEmpty() }
            .filter { it.endsWith("Tab") || it in setOf("AddRecipe", "RecipeFlow", "YourKitchen", "EditRecipe") }
    }

    private fun addRecipeWithText() {
        act { navigate(AddRecipe) }
        compose.onNodeWithText("add 0").performClick()
        compose.onNodeWithText("add 1").assertIsDisplayed()
    }

    private fun flowWithSelection() {
        act { navigate(RecipeFlow("chilli")) }
        compose.onNodeWithText("flow 0").performClick()
        compose.onNodeWithText("flow 1").assertIsDisplayed()
    }

    @Test
    fun pencilOpensSettingsWithTheCreatorBannerAndBannerReturnsWithTheDraft() {
        addRecipeWithText()
        act { openSettingsOnTop() }
        compose.onNodeWithText(toCreator).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText(toCreator).performClick()
        compose.waitForIdle()
        assertEquals(listOf("RecipesTab", "AddRecipe"), stack())
        compose.onNodeWithText("add 1").assertIsDisplayed()
    }

    @Test
    fun deviceBackFromSettingsReturnsToAddRecipeWithTheDraft() {
        addRecipeWithText()
        act { openSettingsOnTop() }
        act { popBackStack() }
        assertEquals(listOf("RecipesTab", "AddRecipe"), stack())
        compose.onNodeWithText("add 1").assertIsDisplayed()
    }

    @Test
    fun backFromYourKitchenKeepsTheBannerAndTwoStepsReachTheDraft() {
        addRecipeWithText()
        act { openSettingsOnTop() }
        act { navigate(YourKitchen) }
        compose.onNodeWithText(toCreator).assertDoesNotExist()
        act { popBackStack() }
        assertEquals(listOf("RecipesTab", "AddRecipe", "SettingsTab"), stack())
        compose.onNodeWithText(toCreator).assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals(listOf("RecipesTab", "AddRecipe"), stack())
        compose.onNodeWithText("add 1").assertIsDisplayed()
    }

    @Test
    fun viewListOpensShoppingOverTheFlowAndBannerReturnsWithTheSelection() {
        flowWithSelection()
        act { openShoppingFromFlow() }
        assertEquals(listOf("RecipesTab", "RecipeFlow", "ShoppingListTab"), stack())
        compose.onNodeWithText(toRecipe).assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals(listOf("RecipesTab", "RecipeFlow"), stack())
        compose.onNodeWithText("flow 1").assertIsDisplayed()
    }

    @Test
    fun anotherTabDropsTheOriginAndTheBanner() {
        addRecipeWithText()
        act { openSettingsOnTop() }
        act { navigateToTab(ShoppingListTab) }
        assertEquals(listOf("RecipesTab", "ShoppingListTab"), stack())
        compose.onNodeWithText(toCreator).assertDoesNotExist()
        compose.onNodeWithText("shopping").assertIsDisplayed()
    }

    @Test
    fun anotherTabDropsTheRecipeFlowToo() {
        flowWithSelection()
        act { openShoppingFromFlow() }
        act { navigateToTab(SettingsTab) }
        assertEquals(listOf("RecipesTab", "SettingsTab"), stack())
        compose.onNodeWithText(toRecipe).assertDoesNotExist()
        act { navigateToTab(RecipesTab) }
        assertEquals(listOf("RecipesTab"), stack())
    }

    @Test
    fun tappingTheShownTabKeepsTheBanner() {
        flowWithSelection()
        act { openShoppingFromFlow() }
        act { navigateToTab(ShoppingListTab) }
        assertEquals(listOf("RecipesTab", "RecipeFlow", "ShoppingListTab"), stack())
        compose.onNodeWithText(toRecipe).assertIsDisplayed()
    }

    @Test
    fun tabsOpenedNormallyHaveNoBanner() {
        act { navigateToTab(ShoppingListTab) }
        compose.onNodeWithText(toRecipe).assertDoesNotExist()
        compose.onNodeWithText(toCreator).assertDoesNotExist()
        act { navigateToTab(SettingsTab) }
        compose.onNodeWithText(toRecipe).assertDoesNotExist()
        compose.onNodeWithText(toCreator).assertDoesNotExist()
        act { navigateToTab(RecipesTab) }
        compose.onNodeWithText(toRecipe).assertDoesNotExist()
        compose.onNodeWithText(toCreator).assertDoesNotExist()
    }
}
