package dev.re7gog.b_sideloader.ui.feature.background

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.domain.background.BackgroundHealth
import dev.re7gog.b_sideloader.domain.background.BackgroundIssue
import dev.re7gog.b_sideloader.domain.background.DeviceVendor
import dev.re7gog.b_sideloader.ui.common.component.LoadingState
import dev.re7gog.b_sideloader.ui.common.component.SectionLabel
import dev.re7gog.b_sideloader.ui.common.component.SnackbarMessages
import dev.re7gog.b_sideloader.ui.common.util.OnResume

/**
 * "Will background updates actually run on this phone?"
 *
 * Aggressive OEM ROMs are the single biggest reason a sideloading updater silently stops working,
 * and none of their autostart allowlists can be read or requested programmatically. So instead of
 * firing blind intents — which is what the old one-shot "Allow background" button did — this
 * screen states each condition, says whether it is satisfied where that is knowable, and gives one
 * button per fix. The last item is the fix that always works: switch to the persistent service.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BackgroundSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    SnackbarMessages(messages = viewModel.messages, hostState = snackbarHostState)

    // The system screens this sends the user to change state behind our back; re-read on return.
    OnResume(onResume = viewModel::refresh)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.background_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.arrow_back_24px),
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { padding ->
        val health = uiState.health
        if (health == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        BackgroundChecklist(
            health = health,
            onBatteryClick = viewModel::requestBatteryExemption,
            onAutoStartClick = viewModel::openAutoStartSettings,
            onNotificationsClick = viewModel::openNotificationSettings,
            onAppSettingsClick = viewModel::openAppSettings,
            onUsePersistentService = viewModel::usePersistentService,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}

@Composable
private fun BackgroundChecklist(
    health: BackgroundHealth,
    onBatteryClick: () -> Unit,
    onAutoStartClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onAppSettingsClick: () -> Unit,
    onUsePersistentService: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SummaryCard(health) }

        item { SectionLabel(stringResource(R.string.background_checklist)) }

        item {
            CheckRow(
                title = stringResource(R.string.background_battery_title),
                description = stringResource(R.string.background_battery_description),
                state = if (health.ignoresBatteryOptimizations) CheckState.Ok else CheckState.Action,
                actionLabel = stringResource(R.string.background_battery_action),
                onAction = onBatteryClick,
            )
        }

        item {
            CheckRow(
                title = stringResource(R.string.background_notifications_title),
                description = stringResource(R.string.background_notifications_description),
                state = if (health.notificationsEnabled) CheckState.Ok else CheckState.Action,
                actionLabel = stringResource(R.string.background_notifications_action),
                onAction = onNotificationsClick,
            )
        }

        if (health.isBackgroundRestricted) {
            item {
                CheckRow(
                    title = stringResource(R.string.background_restricted_title),
                    description = stringResource(R.string.background_restricted_description),
                    state = CheckState.Action,
                    actionLabel = stringResource(R.string.background_open_app_settings),
                    onAction = onAppSettingsClick,
                )
            }
        }

        if (health.vendor.restrictsBackgroundAggressively) {
            item {
                CheckRow(
                    title = stringResource(R.string.background_autostart_title),
                    description = stringResource(
                        R.string.background_autostart_description,
                        health.vendor.displayName(),
                    ),
                    // No ROM exposes its autostart state, so this can only ever be "unknown".
                    state = CheckState.Unknown,
                    actionLabel = stringResource(
                        if (health.autoStartSettingsAvailable) R.string.background_autostart_action
                        else R.string.background_open_app_settings
                    ),
                    onAction = onAutoStartClick,
                )
            }
            items(health.vendor.instructions()) { step ->
                InstructionLine(stringResource(step))
            }
        }

        if (BackgroundIssue.PeriodicUnreliableOnThisRom in health.issues) {
            item { SectionLabel(stringResource(R.string.background_recommended)) }
            item {
                CheckRow(
                    title = stringResource(R.string.background_persistent_title),
                    description = stringResource(R.string.background_persistent_description),
                    state = CheckState.Action,
                    actionLabel = stringResource(R.string.background_persistent_action),
                    onAction = onUsePersistentService,
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(health: BackgroundHealth) {
    val healthy = health.isHealthy
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = if (healthy) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(
                    if (healthy) R.drawable.check_24px else R.drawable.info_24px
                ),
                contentDescription = null,
                tint = if (healthy) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Column {
                Text(
                    text = stringResource(
                        if (healthy) R.string.background_summary_ok
                        else R.string.background_summary_issues
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(
                        R.string.background_summary_device,
                        health.vendor.displayName(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private enum class CheckState { Ok, Action, Unknown }

@Composable
private fun CheckRow(
    title: String,
    description: String,
    state: CheckState,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusBadge(state)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state != CheckState.Ok) {
                    TextButton(
                        onClick = onAction,
                        modifier = Modifier.padding(top = 4.dp),
                    ) { Text(actionLabel) }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(state: CheckState) {
    val (icon, tint) = when (state) {
        CheckState.Ok -> R.drawable.check_24px to MaterialTheme.colorScheme.primary
        CheckState.Action -> R.drawable.x_circle_24px to MaterialTheme.colorScheme.error
        CheckState.Unknown -> R.drawable.info_24px to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.size(36.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun InstructionLine(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(start = 8.dp),
    ) {
        Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DeviceVendor.displayName(): String = stringResource(
    when (this) {
        DeviceVendor.Xiaomi -> R.string.vendor_xiaomi
        DeviceVendor.Huawei -> R.string.vendor_huawei
        DeviceVendor.Oppo -> R.string.vendor_oppo
        DeviceVendor.Vivo -> R.string.vendor_vivo
        DeviceVendor.Meizu -> R.string.vendor_meizu
        DeviceVendor.Transsion -> R.string.vendor_transsion
        DeviceVendor.Asus -> R.string.vendor_asus
        DeviceVendor.Samsung -> R.string.vendor_samsung
        DeviceVendor.Other -> R.string.vendor_generic
    }
)

/** The manual steps for this ROM, in the ROM's own wording. */
private fun DeviceVendor.instructions(): List<Int> = when (this) {
    DeviceVendor.Xiaomi -> listOf(
        R.string.vendor_step_xiaomi_autostart,
        R.string.vendor_step_xiaomi_battery,
        R.string.vendor_step_xiaomi_lock,
    )

    DeviceVendor.Huawei -> listOf(
        R.string.vendor_step_huawei_manage,
        R.string.vendor_step_huawei_launch,
    )

    DeviceVendor.Oppo -> listOf(
        R.string.vendor_step_oppo_autostart,
        R.string.vendor_step_oppo_battery,
    )

    DeviceVendor.Vivo -> listOf(
        R.string.vendor_step_vivo_autostart,
        R.string.vendor_step_vivo_battery,
    )

    DeviceVendor.Samsung -> listOf(
        R.string.vendor_step_samsung_sleeping,
    )

    DeviceVendor.Meizu,
    DeviceVendor.Transsion,
    DeviceVendor.Asus,
    DeviceVendor.Other,
    -> listOf(R.string.vendor_step_generic)
}
