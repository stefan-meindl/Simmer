package com.simmer.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The real app (MainActivity, real SimmerNavHost and screens): Add recipe's pencil to Settings and back. */
@RunWith(AndroidJUnit4::class)
class RealAppBackBannerTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test
    fun pencilOpensSettingsWithTheBannerAndBannerReturnsToAddRecipe() {
        compose.onNodeWithContentDescription(string(R.string.recipes_add)).performClick()
        compose.onNodeWithText(string(R.string.add_choose_phone)).performClick()
        compose.onNodeWithText(string(R.string.add_try_example)).performClick()
        val pencil = compose.onNodeWithContentDescription(string(R.string.add_change_kitchen))
        pencil.performScrollTo().performClick()

        val banner = compose.onNodeWithText(string(R.string.back_to_recipe_creator))
        banner.assertIsDisplayed()
        compose.onNodeWithText(string(R.string.settings_creator)).assertIsDisplayed()

        banner.performClick()
        compose.onNodeWithText(string(R.string.back_to_recipe_creator)).assertDoesNotExist()
        compose.onNodeWithContentDescription(string(R.string.add_change_kitchen)).performScrollTo().assertIsDisplayed()
    }

    /** Taps a bottom tab by its label (the tab bar, not a heading that shares the word). */
    private fun tapTab(label: Int) {
        compose.onAllNodesWithText(string(label)).filter(hasClickAction()).onFirst().performClick()
        compose.waitForIdle()
    }

    @Test
    fun recipesTabShowsTheListAfterViewListAndFromSettings() {
        compose.onNodeWithText("Chilli con carne").performScrollTo().performClick()
        compose.onNode(hasText(string(R.string.flow_add_to_list).substringBefore("%"), substring = true))
            .performScrollTo().performClick()
        compose.onNodeWithText(string(R.string.flow_view_list)).performClick()
        compose.onNodeWithText(string(R.string.back_to_recipe)).assertIsDisplayed()

        tapTab(R.string.tab_recipes)
        compose.onNodeWithText(string(R.string.back_to_recipe)).assertDoesNotExist()
        compose.onNodeWithContentDescription(string(R.string.recipes_add)).assertIsDisplayed()

        tapTab(R.string.tab_settings)
        compose.onNodeWithContentDescription(string(R.string.recipes_add)).assertDoesNotExist()
        tapTab(R.string.tab_recipes)
        compose.onNodeWithContentDescription(string(R.string.recipes_add)).assertIsDisplayed()
    }
}
