package dev.re7gog.b_sideloader.ui.common.permission

import android.Manifest
import android.os.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import dev.re7gog.b_sideloader.R

/**
 * Asks for `POST_NOTIFICATIONS` once, and explains why if the user has already said no.
 *
 * The permission matters more here than in most apps: without it a foreground service cannot show
 * its ongoing notification, which is what keeps the persistent update monitor alive on aggressive
 * ROMs — so the rationale says that, rather than a generic "we'd like to notify you".
 *
 * The dialog is dismissible. The previous version passed `onDismissRequest = { }`, which made it
 * impossible to close without granting.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun NotificationPermissionGate() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val permissionState = rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    var rationaleDismissed by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(permissionState.status) {
        if (!permissionState.status.isGranted && !permissionState.status.shouldShowRationale) {
            permissionState.launchPermissionRequest()
        }
    }

    if (permissionState.status.shouldShowRationale && !rationaleDismissed) {
        AlertDialog(
            onDismissRequest = { rationaleDismissed = true },
            title = { Text(stringResource(R.string.enable_notifications)) },
            text = { Text(stringResource(R.string.notifications_rationale)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        rationaleDismissed = true
                        permissionState.launchPermissionRequest()
                    }
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { rationaleDismissed = true }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
