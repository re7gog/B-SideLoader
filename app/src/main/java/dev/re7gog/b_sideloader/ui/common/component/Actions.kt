package dev.re7gog.b_sideloader.ui.common.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.re7gog.b_sideloader.R

/**
 * The big primary action, which becomes a progress bar while work is running.
 *
 * [fraction] of `null` renders an indeterminate bar. The old version only accepted a `Float` and
 * so had to show "0%" for phases whose size is unknown — which read as "stuck".
 */
@Composable
fun PrimaryActionArea(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    inProgress: Boolean = false,
    fraction: Float? = null,
    progressLabel: String? = null,
) {
    if (inProgress) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier.fillMaxWidth(),
        ) {
            if (fraction == null) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ActionDefaults.ProgressHeight),
                    strokeCap = StrokeCap.Round,
                )
            } else {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ActionDefaults.ProgressHeight),
                    strokeCap = StrokeCap.Round,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = progressLabel
                    ?: fraction?.let {
                        stringResource(R.string.downloading_percent, (it * PERCENT).toInt())
                    }
                    ?: stringResource(R.string.working),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(ActionDefaults.ButtonCorner),
            modifier = modifier
                .fillMaxWidth()
                .height(ActionDefaults.ButtonHeight),
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Uninstall (when installed) and Remove (when saved), side by side. */
@Composable
fun SecondaryActions(
    showUninstall: Boolean,
    showRemove: Boolean,
    onUninstall: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!showUninstall && !showRemove) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        if (showUninstall) {
            OutlinedButton(
                onClick = onUninstall,
                shape = RoundedCornerShape(ActionDefaults.ButtonCorner),
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.uninstall)) }
        }
        if (showRemove) {
            OutlinedButton(
                onClick = onRemove,
                shape = RoundedCornerShape(ActionDefaults.ButtonCorner),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.remove)) }
        }
    }
}

object ActionDefaults {
    val ButtonCorner = 20.dp
    val ButtonHeight = 56.dp
    val ProgressHeight = 10.dp
}

private const val PERCENT = 100
