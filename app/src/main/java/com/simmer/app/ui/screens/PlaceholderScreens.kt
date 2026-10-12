package com.simmer.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.simmer.app.R
import com.simmer.app.ui.theme.LocalSimmerColors

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

@Composable
private fun CenteredLabel(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text)
    }
}

// ---------------------------------------------------------------------------
// Recipe flow screens (hide tab bar, own top bar + sticky bottom action bar)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeFlowScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSimmerColors.current
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_left),
                            contentDescription = "Back",
                            tint = colors.textPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.surfacePage,
                    titleContentColor = colors.textPrimary,
                ),
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {},
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Secondary Action")
                }
                Button(
                    onClick = {},
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.accentFg,
                    ),
                ) {
                    Text("Primary Action")
                }
            }
        },
    ) { innerPadding ->
        CenteredLabel(text = title, modifier = Modifier.padding(innerPadding))
    }
}

@Composable
fun RecipeFlowPlaceholder(recipeId: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    RecipeFlowScaffold(
        title = "Recipe Flow (id=$recipeId)",
        onBack = onBack,
        modifier = modifier,
    )
}

// ---------------------------------------------------------------------------
// Add / Edit Recipe screens (hide tab bar, own top bar + sticky bottom action bar)
// ---------------------------------------------------------------------------

@Composable
fun AddRecipePlaceholder(onBack: () -> Unit, modifier: Modifier = Modifier) {
    RecipeFlowScaffold(
        title = "Add Recipe",
        onBack = onBack,
        modifier = modifier,
    )
}

// ---------------------------------------------------------------------------
// Your Kitchen screen (hides tab bar, back -> Settings)
// ---------------------------------------------------------------------------

@Composable
fun YourKitchenPlaceholder(onBack: () -> Unit, modifier: Modifier = Modifier) {
    RecipeFlowScaffold(
        title = "Your Kitchen",
        onBack = onBack,
        modifier = modifier,
    )
}
