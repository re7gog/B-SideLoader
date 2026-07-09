package dev.re7gog.b_sideloader.ui.features.manual_install

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.data.installer.StagedApk

/** APKs are commonly served as octet-stream, so accepting only the strict type hides files. */
private val APK_MIME_TYPES = arrayOf(
    "application/vnd.android.package-archive",
    "application/octet-stream"
)

/**
 * Body of the "Local file" search source: a tip, a file picker, then a confirmation of what
 * the picked archive actually contains before anything is installed.
 */
@Composable
fun ManualInstallPane(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: ManualInstallViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { snackbarHostState.showSnackbar(it) }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::onFileSelected) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ManualInstallTip()

        when (val current = state) {
            is ManualInstallState.Reading -> ManualInstallBusy("Reading the APK…")

            is ManualInstallState.Installing -> InstallProgress(current.progress)

            else -> Button(
                onClick = { filePicker.launch(APK_MIME_TYPES) },
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.apk_file_24px),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text("Choose an APK file", style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    (state as? ManualInstallState.Confirming)?.let { confirming ->
        ApkConfirmDialog(
            apk = confirming.apk,
            onConfirm = viewModel::onConfirm,
            onDismiss = viewModel::onCancel
        )
    }
}

@Composable
private fun ManualInstallTip(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.info_24px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Install from a file",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            TipLine("Pick any APK on this device and it installs with your current installer mode — silently, when you use Shizuku or Dhizuku.")
            TipLine("The app is not added to your list and will never be checked for updates. Add it from GitHub or Telegram if you want that.")
            TipLine("Only install APKs you trust. Check the package name on the next step.")
        }
    }
}

@Composable
private fun TipLine(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ManualInstallBusy(label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InstallProgress(progress: Float) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        LinearWavyProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text(
            // The bytes are copied long before PackageInstaller reports back, so past 100%
            // the wait is on the system dialog or the privileged service
            text = if (progress < 1f) "Installing ${(progress * 100).toInt()}%"
                   else "Finishing the install…",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Last stop before the install: shows what the archive really is, parsed from its manifest. */
@Composable
private fun ApkConfirmDialog(
    apk: StagedApk,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val size = remember(apk.sizeBytes) { Formatter.formatShortFileSize(context, apk.sizeBytes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            AsyncImage(
                model = apk.icon,
                error = painterResource(R.drawable.apk_file_24px),
                fallback = painterResource(R.drawable.apk_file_24px),
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
        },
        title = {
            Text(apk.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ApkInfoRow("Package", apk.packageName)
                ApkInfoRow("Version", "${apk.versionName} (${apk.versionCode})")
                ApkInfoRow("Size", size)
                if (apk.installedVersionName != null) {
                    ApkInfoRow("Installed", apk.installedVersionName)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when {
                        apk.isDowngrade ->
                            "This is older than the installed version. Android will refuse the " +
                                    "install unless you uninstall the current app first."
                        apk.installedVersionName != null ->
                            "This will replace the installed app. Its data is kept only if both " +
                                    "APKs are signed with the same key."
                        else ->
                            "This app won't be added to your list and won't receive updates."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (apk.isDowngrade) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Install") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ApkInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
