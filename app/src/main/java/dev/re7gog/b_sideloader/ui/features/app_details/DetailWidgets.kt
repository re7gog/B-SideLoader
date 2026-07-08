package dev.re7gog.b_sideloader.ui.features.app_details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Small primary-colored label used to separate sections on a details page. */
@Composable
fun DetailSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
    )
}

/** Rounded filter text field with consistent expressive shape. */
@Composable
fun DetailFilterField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    )
}

/** Tonal card row hosting the autoupdate toggle. */
@Composable
fun DetailAutoupdateRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        ListItem(
            headlineContent = { Text("Autoupdate") },
            supportingContent = { Text("Keep this app up to date automatically") },
            trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable { onCheckedChange(!checked) }
        )
    }
}

/** Large rounded primary action button, or a progress bar while installing. */
@Composable
fun DetailActionArea(
    label: String,
    enabled: Boolean,
    isInstalling: Boolean,
    progress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isInstalling) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier.fillMaxWidth()
        ) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Downloading ${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(20.dp),
            modifier = modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Row of secondary actions: Uninstall (when installed) and Delete (when saved). */
@Composable
fun DetailSecondaryActions(
    showUninstall: Boolean,
    showDelete: Boolean,
    onUninstall: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!showUninstall && !showDelete) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        if (showUninstall) {
            OutlinedButton(
                onClick = onUninstall,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Uninstall")
            }
        }
        if (showDelete) {
            OutlinedButton(
                onClick = onDelete,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text("Remove")
            }
        }
    }
}

/** Confirmation dialog shown before removing an app from the database. */
@Composable
fun DeleteConfirmDialog(
    appName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove app") },
        text = {
            Text(
                "Remove \"$appName\" from your list? This only deletes it here and " +
                        "does not uninstall it from your device.",
                textAlign = TextAlign.Start
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) { Text("Remove") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
