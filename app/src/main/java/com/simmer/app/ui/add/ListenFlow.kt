package com.simmer.app.ui.add

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.simmer.app.R
import com.simmer.app.domain.ListenState
import com.simmer.app.domain.formatGigabytes
import com.simmer.app.listen.ListenService
import com.simmer.app.ui.components.PrimaryButton
import com.simmer.app.ui.components.SecondaryButton
import com.simmer.app.ui.flow.rememberNotificationPermissionRequest
import com.simmer.app.ui.theme.LocalSimmerColors
import com.simmer.app.ui.theme.SimmerTextStyle_BodyMd
import com.simmer.app.ui.theme.SimmerTextStyle_Heading4
import java.util.Locale

/**
 * The platform steps of Listen that need an Activity: the explainer and size dialogs, the RECORD_AUDIO request,
 * the screen-capture consent, and opening the Reel in Instagram once capture is under way. Everything it decides
 * comes from [state]; the answers go back to the [AddRecipeViewModel]'s session.
 */
@Composable
internal fun ListenFlow(state: ListenState, viewModel: AddRecipeViewModel) {
    val context = LocalContext.current
    val askNotifications = rememberNotificationPermissionRequest()

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == android.app.Activity.RESULT_OK && data != null) {
            ListenService.start(context, result.resultCode, data)
            openReel(context, viewModel.reelUrl)
        } else {
            viewModel.listenConsentDenied()
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.listenPermissionGranted() else viewModel.listenPermissionDenied()
    }

    // Launched once per consent step, also across rotation (the flag is saved).
    var consentLaunched by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (state == ListenState.AwaitingConsent && !consentLaunched) {
            consentLaunched = true
            consent.launch(screenCaptureIntent(context))
        } else if (state != ListenState.AwaitingConsent) {
            consentLaunched = false
        }
    }

    when (state) {
        is ListenState.ConfirmDownload -> ListenDialog(
            title = stringResource(R.string.listen_download_title),
            body = stringResource(R.string.listen_download_body, formatMegabytes(state.sizeBytes)),
            confirm = stringResource(R.string.listen_download),
            onConfirm = viewModel::confirmListenDownload,
            onCancel = viewModel::cancelListen,
        )
        ListenState.AwaitingPermission -> ListenDialog(
            title = stringResource(R.string.listen_explainer_title),
            body = stringResource(R.string.listen_explainer_body),
            confirm = stringResource(R.string.listen_continue),
            onConfirm = {
                askNotifications()
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    viewModel.listenPermissionGranted()
                } else {
                    permission.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            onCancel = viewModel::cancelListen,
        )
        else -> Unit
    }
}

private fun formatMegabytes(bytes: Long): String =
    if (bytes >= 1_000_000_000L) formatGigabytes(bytes, Locale.getDefault())
    else String.format(Locale.getDefault(), "%d MB", (bytes + 500_000) / 1_000_000)

/** Full screen (not "a single app") capture, so the sound of whichever app plays the Reel is included. */
private fun screenCaptureIntent(context: Context): Intent {
    val manager = context.getSystemService(MediaProjectionManager::class.java)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
    } else {
        manager.createScreenCaptureIntent()
    }
}

/** Opens the Reel in Instagram (or, without it, whatever opens the link). */
private fun openReel(context: Context, url: String?) {
    if (url == null) return
    val view = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(view.setPackage("com.instagram.android"))
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(view.setPackage(null))
        } catch (e: ActivityNotFoundException) {
            // Nothing can open the link: the user opens the Reel by hand while Simmer listens.
        }
    }
}

@Composable
private fun ListenDialog(title: String, body: String, confirm: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val colors = LocalSimmerColors.current
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = colors.surfaceCard,
        title = { Text(title, style = SimmerTextStyle_Heading4, color = colors.textPrimary) },
        text = { Text(body, style = SimmerTextStyle_BodyMd, color = colors.textSecondary) },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                PrimaryButton(confirm, onClick = onConfirm, modifier = Modifier.fillMaxWidth())
                SecondaryButton(stringResource(R.string.listen_cancel), onClick = onCancel, modifier = Modifier.fillMaxWidth())
            }
        },
    )
}
