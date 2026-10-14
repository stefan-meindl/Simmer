package com.simmer.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextDecoration
import com.simmer.app.ui.components.CheckRow
import com.simmer.app.ui.theme.LocalSimmerColors
import com.simmer.app.ui.theme.SimmerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/** A ticked check row strikes its label through, except where the caller opts out (Your Kitchen appliances). */
@RunWith(AndroidJUnit4::class)
class CheckRowLabelStyleTest {
    @get:Rule
    val rule = createComposeRule()

    private fun SemanticsNodeInteraction.labelStyle() = run {
        val results = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        results.single().layoutInput.style
    }

    @Test
    fun tickedRowStrikesLabelThroughByDefault() {
        rule.setContent { SimmerTheme { CheckRow(label = "Milk", checked = true, onCheckedChange = {}) } }
        assertEquals(TextDecoration.LineThrough, rule.onNodeWithText("Milk").labelStyle().textDecoration)
    }

    @Test
    fun tickedRowWithoutStrikeThroughKeepsPrimaryColourAndNoDecoration() {
        var primary: Color? = null
        var tertiary: Color? = null
        rule.setContent {
            SimmerTheme {
                primary = LocalSimmerColors.current.textPrimary
                tertiary = LocalSimmerColors.current.textTertiary
                CheckRow(label = "Hob", checked = true, onCheckedChange = {}, strikeThroughWhenChecked = false)
            }
        }
        rule.waitForIdle()
        val style = rule.onNodeWithText("Hob").labelStyle()
        assertFalse(style.textDecoration?.contains(TextDecoration.LineThrough) ?: false)
        assertNotEquals("tertiary=$tertiary actual=${style.color}", tertiary, style.color)
        assertEquals("primary=$primary actual=${style.color}", primary, style.color)
    }
}
