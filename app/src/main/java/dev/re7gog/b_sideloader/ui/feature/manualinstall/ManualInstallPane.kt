package dev.re7gog.b_sideloader.ui.feature.manualinstall

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.domain.model.InstallProgress
import dev.re7gog.b_sideloader.domain.model.LocalApk
import dev.re7gog.b_sideloader.ui.common.component.SnackbarMessages

/**
 * APKs are commonly served as `octet-stream`, so accepting only the strict MIME type hides files
 * the user can clearly see in their file manager.
 */
private val APK_MIME_TYPES = arrayOf(
    "application/vnd.android.package-archive",
    "application/octet-stream",
)

/**
 * Body of the "Local file" source: a tip, a file picker, and a confirmation of what the picked
 * archive actually contains before anything is installed.
 */
@Composable
fun ManualInstallPane(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: ManualInstallViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SnackbarMessages(messages = viewModel.messages, hostState = snackbarHostState)

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onFileSelected(it.toString()) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ManualInstallTip()

        when (val state = uiState) {
            is ManualInstallUiState.Reading -> BusyRow(stringResource(R.string.reading_apk))

            is ManualInstallUiState.Installing -> InstallProgressRow(state.progress)

            else -> Button(
                onClick = { filePicker.launch(APK_MIME_TYPES) },
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.apk_file_24px),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(R.string.choose_apk_file),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }

    (uiState as? ManualInstallUiState.Confirming)?.let { confirming ->
        ApkConfirmDialog(
            apk = confirming.apk,
            onConfirm = viewModel::onConfirm,
            onDismiss = viewModel::onCancel,
        )
    }
}

@Composable
private fun ManualInstallTip(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.info_24px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.install_from_file),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            TipLine(stringResource(R.string.manual_tip_installer))
            TipLine(stringResource(R.string.manual_tip_not_tracked))
            TipLine(stringResource(R.string.manual_tip_trust))
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BusyRow(label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Progress for the install phases.
 *
 * `Committing` genuinely has no measurable size — the wait is on the system dialog or the
 * privileged service — so it gets an indeterminate bar instead of a bar pinned at 100% that reads
 * as frozen.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InstallProgressRow(progress: InstallProgress) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        val fraction = progress.fraction
        if (fraction == null) {
            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearWavyProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = when (progress) {
                is InstallProgress.Downloading,
                is InstallProgress.Staging,
                -> stringResource(
                    R.string.installing_percent,
                    ((progress.fraction ?: 0f) * PERCENT).toInt(),
                )

                InstallProgress.Preparing -> stringResource(R.string.reading_apk)
                InstallProgress.Committing, is InstallProgress.Finished ->
                    stringResource(R.string.finishing_install)
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Last stop before the install: what the archive really is, parsed from its own manifest. */
@Composable
private fun ApkConfirmDialog(
    apk: LocalApk,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val size = remember(apk.sizeBytes) { Formatter.formatShortFileSize(context, apk.sizeBytes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = painterResource(R.drawable.apk_file_24px),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text(apk.label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ApkInfoRow(stringResource(R.string.apk_info_package), apk.packageName)
                ApkInfoRow(
                    stringResource(R.string.apk_info_version),
                    stringResource(
                        R.string.apk_version_format,
                        apk.versionName,
                        apk.versionCode.toString(),
                    ),
                )
                ApkInfoRow(stringResource(R.string.apk_info_size), size)
                apk.installedVersionName?.let {
                    ApkInfoRow(stringResource(R.string.apk_info_installed), it)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when {
                        apk.isDowngrade -> stringResource(R.string.apk_downgrade_warning)
                        apk.isReinstall -> stringResource(R.string.apk_replace_warning)
                        else -> stringResource(R.string.apk_not_tracked_warning)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (apk.isDowngrade) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.install)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun ApkInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val PERCENT = 100
