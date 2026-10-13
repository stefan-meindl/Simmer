package com.simmer.app

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simmer.app.ui.components.EditAction
import com.simmer.app.ui.components.EditIconAction
import com.simmer.app.ui.theme.SimmerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The shared edit affordance (flow "Edit", review "Edit details"): a >= 44dp button that reports clicks. */
@RunWith(AndroidJUnit4::class)
class EditActionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun isAtLeast44dpTallAndClickable() {
        var clicks = 0
        compose.setContent { SimmerTheme(darkTheme = false) { EditAction("Edit details", onClick = { clicks++ }) } }
        compose.onNodeWithText("Edit details").assertHasClickAction().assertHeightIsAtLeast(44.dp)
        compose.onNodeWithText("Edit details").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun iconOnlyVariantIsA44dpLabelledButton() {
        var clicks = 0
        compose.setContent {
            SimmerTheme(darkTheme = false) { EditIconAction("Change kitchen settings", onClick = { clicks++ }) }
        }
        compose.onNodeWithContentDescription("Change kitchen settings")
            .assertHasClickAction().assertHeightIsAtLeast(44.dp).assertWidthIsAtLeast(44.dp)
        compose.onNodeWithContentDescription("Change kitchen settings").performClick()
        assertEquals(1, clicks)
    }
}
