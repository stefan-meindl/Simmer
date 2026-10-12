package com.simmer.app.ui.components

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.simmer.app.ui.theme.LocalSimmerColors
import com.simmer.app.ui.theme.SimmerTextStyle_Heading4
import com.simmer.app.ui.theme.SimmerTextStyle_MicroUppercase
import com.simmer.app.ui.theme.SimmerTheme

// ---------------------------------------------------------------------------
// Shared preview scaffold
// ---------------------------------------------------------------------------

@Composable
private fun PreviewGallery(content: @Composable () -> Unit) {
    SimmerTheme {
        val colors = LocalSimmerColors.current
        Surface(color = colors.surfacePage) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    val colors = LocalSimmerColors.current
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = title.uppercase(),
        style = SimmerTextStyle_MicroUppercase,
        color = colors.textMuted,
    )
    HorizontalDivider(color = colors.borderSubtle)
    Spacer(modifier = Modifier.height(4.dp))
}

// ---------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------

@Preview(name = "Buttons – Light", showBackground = true)
@Composable
private fun PreviewButtonsLight() = PreviewGallery { ButtonsContent() }

@Preview(name = "Buttons – Dark", showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun PreviewButtonsDark() = PreviewGallery { ButtonsContent() }

@Composable
private fun ButtonsContent() {
    Section("Buttons")
    PrimaryButton(text = "Primary", onClick = {}, modifier = Modifier.fillMaxWidth())
    DarkButton(text = "Dark", onClick = {}, modifier = Modifier.fillMaxWidth())
    SecondaryButton(text = "Secondary", onClick = {}, modifier = Modifier.fillMaxWidth())
    CreamButton(text = "Cream", onClick = {}, modifier = Modifier.fillMaxWidth())
    LinkButton(text = "Link button", onClick = {})
    Section("Disabled")
    PrimaryButton(text = "Primary (disabled)", onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth())
    SecondaryButton(text = "Secondary (disabled)", onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth())
}

// ---------------------------------------------------------------------------
// Text fields
// ---------------------------------------------------------------------------

@Preview(name = "TextField – Light", showBackground = true)
@Composable
private fun PreviewTextFieldLight() = PreviewGallery { TextFieldContent() }

@Preview(name = "TextField – Dark", showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun PreviewTextFieldDark() = PreviewGallery { TextFieldContent() }

@Composable
private fun TextFieldContent() {
    var value1 by remember { mutableStateOf("") }
    var value2 by remember { mutableStateOf("Some text") }
    var value3 by remember { mutableStateOf("") }
    Section("Text Fields")
    SimmerTextField(
        value = value1,
        onValueChange = { value1 = it },
        label = "Recipe name",
        placeholder = "e.g. Spaghetti Carbonara",
        modifier = Modifier.fillMaxWidth(),
    )
    SimmerTextField(
        value = value2,
        onValueChange = { value2 = it },
        label = "With value",
        modifier = Modifier.fillMaxWidth(),
    )
    SimmerTextField(
        value = "",
        onValueChange = {},
        label = "Error state",
        isError = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Section("Text Area")
    SimmerTextArea(
        value = value3,
        onValueChange = { value3 = it },
        label = "Notes",
        placeholder = "Add any notes about this recipe…",
        modifier = Modifier.fillMaxWidth(),
    )
}

// ---------------------------------------------------------------------------
// Tabs
// ---------------------------------------------------------------------------

@Preview(name = "Tabs – Light", showBackground = true)
@Composable
private fun PreviewTabsLight() = PreviewGallery { TabsContent() }

@Preview(name = "Tabs – Dark", showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun PreviewTabsDark() = PreviewGallery { TabsContent() }

@Composable
private fun TabsContent() {
    var selected by remember { mutableIntStateOf(0) }
    Section("Underline Tabs")
    UnderlineTabs(
        tabs = listOf("Overview", "Ingredients", "Steps"),
        selectedIndex = selected,
        onTabSelected = { selected = it },
        modifier = Modifier.fillMaxWidth(),
    )
}

// ---------------------------------------------------------------------------
// Chips
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Preview(name = "Chips – Light", showBackground = true)
@Composable
private fun PreviewChipsLight() = PreviewGallery { ChipsContent() }

@OptIn(ExperimentalLayoutApi::class)
@Preview(name = "Chips – Dark", showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun PreviewChipsDark() = PreviewGallery { ChipsContent() }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipsContent() {
    var selected by remember { mutableIntStateOf(1) }
    val labels = listOf("All", "Breakfast", "Lunch", "Dinner", "Dessert")
    Section("Filter Chips")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            FilterChip(
                label = label,
                selected = selected == index,
                onToggle = { selected = index },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Cards
// ---------------------------------------------------------------------------

@Preview(name = "Cards – Light", showBackground = true)
@Composable
private fun PreviewCardsLight() = PreviewGallery { CardsContent() }

@Preview(name = "Cards – Dark", showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun PreviewCardsDark() = PreviewGallery { CardsContent() }

@Composable
private fun CardsContent() {
    val colors = LocalSimmerColors.current
    var optionSelected by remember { mutableIntStateOf(0) }
    Section("Option Cards")
    listOf("Quick (< 30 min)", "Medium (30–60 min)", "Long (60+ min)").forEachIndexed { i, label ->
        OptionCard(
            selected = optionSelected == i,
            onClick = { optionSelected = i },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = label,
                style = SimmerTextStyle_Heading4,
                color = colors.textPrimary,
                modifier = Modifier.padding(16.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
    }
    Section("Gradient Hero Card")
    GradientHeroCard(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.padding(24.dp)) {
            Text(
                text = "Spaghetti\nCarbonara",
                style = SimmerTextStyle_Heading4,
                color = colors.textPrimary,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// CheckRow
// ---------------------------------------------------------------------------

@Preview(name = "CheckRow – Light", showBackground = true)
@Composable
private fun PreviewCheckRowLight() = PreviewGallery { CheckRowContent() }

@Preview(name = "CheckRow – Dark", showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun PreviewCheckRowDark() = PreviewGallery { CheckRowContent() }

@Composable
private fun CheckRowContent() {
    var checked1 by remember { mutableStateOf(false) }
    var checked2 by remember { mutableStateOf(true) }
    var checked3 by remember { mutableStateOf(false) }
    Section("Check Rows")
    CheckRow(
        label = "200g spaghetti",
        checked = checked1,
        onCheckedChange = { checked1 = it },
    )
    CheckRow(
        label = "3 egg yolks",
        checked = checked2,
        onCheckedChange = { checked2 = it },
        subtitle = "Room temperature",
    )
    CheckRow(
        label = "100g Pecorino Romano",
        checked = checked3,
        onCheckedChange = { checked3 = it },
        subtitle = "Finely grated",
    )
}

// ---------------------------------------------------------------------------
// Badges
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Preview(name = "Badges – Light", showBackground = true)
@Composable
private fun PreviewBadgesLight() = PreviewGallery { BadgesContent() }

@OptIn(ExperimentalLayoutApi::class)
@Preview(name = "Badges – Dark", showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun PreviewBadgesDark() = PreviewGallery { BadgesContent() }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BadgesContent() {
    Section("Badges")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Badge(label = "Primary", variant = BadgeVariant.Primary)
        Badge(label = "Cream", variant = BadgeVariant.Cream)
        Badge(label = "Dark", variant = BadgeVariant.Dark)
        Badge(label = "30 min")
        Badge(label = "4 servings", variant = BadgeVariant.Cream)
        Badge(label = "Easy", variant = BadgeVariant.Dark)
    }
}

// ---------------------------------------------------------------------------
// StepTimer
// ---------------------------------------------------------------------------

@Preview(name = "StepTimer – Light", showBackground = true)
@Composable
private fun PreviewStepTimerLight() = PreviewGallery { StepTimerContent() }

@Preview(name = "StepTimer – Dark", showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun PreviewStepTimerDark() = PreviewGallery { StepTimerContent() }

@Composable
private fun StepTimerContent() {
    Section("Step Timer (Idle)")
    StepTimer(durationText = "5 min", onClick = {})
    StepTimer(durationText = "1:30", onClick = {})
}

// ---------------------------------------------------------------------------
// Full gallery (all components in one preview)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Preview(name = "All Components – Light", showBackground = true, widthDp = 400)
@Composable
private fun PreviewAllLight() = PreviewGallery {
    ButtonsContent()
    TextFieldContent()
    TabsContent()
    ChipsContent()
    CardsContent()
    CheckRowContent()
    BadgesContent()
    StepTimerContent()
}

@OptIn(ExperimentalLayoutApi::class)
@Preview(name = "All Components – Dark (OLED)", showBackground = true, widthDp = 400, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun PreviewAllDark() = PreviewGallery {
    ButtonsContent()
    TextFieldContent()
    TabsContent()
    ChipsContent()
    CardsContent()
    CheckRowContent()
    BadgesContent()
    StepTimerContent()
}
