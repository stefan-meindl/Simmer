package com.simmer.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.simmer.app.R
import com.simmer.app.ui.theme.LocalSimmerColors
import com.simmer.app.ui.theme.SimmerTextStyle_BodyMd

/** One entry of an [OverflowMenu]. */
data class MenuAction(val label: String, val onClick: () -> Unit)

/** A three-dot button (48dp target) that opens a dropdown of [actions]. */
@Composable
fun OverflowMenu(contentDescription: String, actions: List<MenuAction>, modifier: Modifier = Modifier) {
    val colors = LocalSimmerColors.current
    var open by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier) {
        Icon(
            painter = painterResource(R.drawable.ic_more_vertical),
            contentDescription = contentDescription,
            tint = colors.textPrimary,
            modifier = Modifier.size(24.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label, style = SimmerTextStyle_BodyMd, color = colors.textPrimary) },
                    onClick = {
                        open = false
                        action.onClick()
                    },
                )
            }
        }
    }
}
