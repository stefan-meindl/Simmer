package com.simmer.app.ui.flow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.simmer.app.R
import com.simmer.app.ui.components.DarkButton
import com.simmer.app.ui.components.PrimaryButton
import com.simmer.app.ui.components.SecondaryButton
import com.simmer.app.ui.theme.LocalSimmerColors
import com.simmer.app.ui.theme.SimmerTextStyle_BodyMd
import com.simmer.app.ui.theme.SimmerTextStyle_Heading4

/**
 * Asked when the user leaves Flow Phase 3 backwards while cooking is in progress. Keep cooking is
 * the default (accent) and also what dismissing does; Stop cooking is the heavy dark button.
 */
@Composable
fun LeaveCookingDialog(
    onStop: () -> Unit,
    onKeep: () -> Unit,
    onGoToRecipes: () -> Unit,
) {
    val colors = LocalSimmerColors.current
    AlertDialog(
        onDismissRequest = onKeep,
        containerColor = colors.surfaceCard,
        title = { Text(stringResource(R.string.flow_leave_title), style = SimmerTextStyle_Heading4, color = colors.textPrimary) },
        text = { Text(stringResource(R.string.flow_leave_body), style = SimmerTextStyle_BodyMd, color = colors.textSecondary) },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                PrimaryButton(stringResource(R.string.flow_leave_keep), onClick = onKeep, modifier = Modifier.fillMaxWidth())
                SecondaryButton(stringResource(R.string.flow_leave_recipes), onClick = onGoToRecipes, modifier = Modifier.fillMaxWidth())
                DarkButton(stringResource(R.string.flow_leave_stop), onClick = onStop, modifier = Modifier.fillMaxWidth())
            }
        },
    )
}
