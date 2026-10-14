package com.simmer.app

import com.simmer.app.ui.nav.BackOrigin
import com.simmer.app.ui.nav.backOriginFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Seam: which Back banner a tab shows, from the route of the entry underneath it. Routes are the real ones. */
class BackOriginTest {
    private val nav = "com.simmer.app.ui.nav"
    private val settings = "$nav.SettingsTab"
    private val shopping = "$nav.ShoppingListTab"
    private val recipes = "$nav.RecipesTab"

    @Test
    fun addRecipeUnderASettingsTabNamesTheRecipeCreator() {
        assertEquals(BackOrigin.RecipeCreator, backOriginFor(settings, "$nav.AddRecipe"))
    }

    @Test
    fun recipeFlowUnderTheShoppingTabNamesTheRecipe() {
        assertEquals(BackOrigin.Recipe, backOriginFor(shopping, "$nav.RecipeFlow/{recipeId}"))
        assertEquals(BackOrigin.Recipe, backOriginFor(shopping, "$nav.RecipeFlow/chilli"))
    }

    @Test
    fun tabsOpenedNormallyHaveNoBanner() {
        assertNull(backOriginFor(shopping, recipes))
        assertNull(backOriginFor(settings, shopping))
        assertNull(backOriginFor(recipes, null))
    }

    @Test
    fun otherPushedScreensAndPhasesAreNotOrigins() {
        assertNull(backOriginFor(settings, "$nav.YourKitchen"))
        assertNull(backOriginFor(shopping, "$nav.RecipeFlowPhase2/{recipeId}"))
        assertNull(backOriginFor(shopping, "$nav.EditRecipe?recipeId={recipeId}"))
    }

    @Test
    fun onlyTabsShowABanner() {
        assertNull(backOriginFor("$nav.YourKitchen", "$nav.AddRecipe"))
        assertNull(backOriginFor("$nav.EditRecipe?recipeId={recipeId}", "$nav.RecipeFlow/{recipeId}"))
        assertNull(backOriginFor(null, "$nav.AddRecipe"))
    }
}
