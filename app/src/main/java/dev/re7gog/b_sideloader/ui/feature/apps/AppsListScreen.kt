package dev.re7gog.b_sideloader.ui.feature.apps

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.ui.common.component.ConfirmDialog
import dev.re7gog.b_sideloader.ui.common.component.EmptyState
import dev.re7gog.b_sideloader.ui.common.component.rememberInstalledAppIcon
import dev.re7gog.b_sideloader.ui.common.text.asString

/**
 * The tracked apps, with multi-select for bulk remove/uninstall.
 *
 * Stateless with respect to its data: everything comes from one [AppsListUiState], so the whole
 * screen can be rendered in a test or a preview from a literal.
 */
@Composable
fun AppsListScreen(
    onAppClick: (appId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppsListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AppsListScreen(
        uiState = uiState,
        onAppClick = onAppClick,
        onToggleSelection = viewModel::toggleSelection,
        onLongPress = viewModel::onAppLongPress,
        onClearSelection = viewModel::clearSelection,
        onSelectAll = viewModel::selectAll,
        onRemoveSelected = viewModel::removeSelectedFromList,
        onUninstallSelected = viewModel::uninstallSelected,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsListScreen(
    uiState: AppsListUiState,
    onAppClick: (appId: Long) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onLongPress: (Long) -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onRemoveSelected: () -> Unit,
    onUninstallSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingBulkAction by remember { mutableStateOf<BulkAction?>(null) }

    // In selection mode, back clears the selection instead of leaving the screen.
    BackHandler(enabled = uiState.inSelectionMode, onBack = onClearSelection)

    Scaffold(
        topBar = {
            AppsListTopBar(
                inSelectionMode = uiState.inSelectionMode,
                selectedCount = uiState.selectedCount,
                onClearSelection = onClearSelection,
                onSelectAll = onSelectAll,
                onRemoveClick = { pendingBulkAction = BulkAction.Remove },
                onUninstallClick = { pendingBulkAction = BulkAction.Uninstall },
            )
        },
        modifier = modifier,
    ) { padding ->
        if (uiState.isEmpty) {
            EmptyState(
                iconRes = R.drawable.apps_24px,
                title = stringResource(R.string.no_apps_title),
                subtitle = stringResource(R.string.no_apps_subtitle),
                tinted = true,
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(uiState.apps, key = { it.id }) { app ->
                    AppListItemCard(
                        app = app,
                        inSelectionMode = uiState.inSelectionMode,
                        onClick = {
                            if (uiState.inSelectionMode) onToggleSelection(app.id) else onAppClick(app.id)
                        },
                        onLongClick = { onLongPress(app.id) },
                    )
                }
            }
        }
    }

    pendingBulkAction?.let { action ->
        ConfirmDialog(
            title = stringResource(action.titleRes),
            message = when (action) {
                BulkAction.Remove -> stringResource(action.messageRes, uiState.selectedCount)
                BulkAction.Uninstall -> stringResource(action.messageRes)
            },
            confirmLabel = stringResource(action.confirmRes),
            onConfirm = {
                pendingBulkAction = null
                when (action) {
                    BulkAction.Remove -> onRemoveSelected()
                    BulkAction.Uninstall -> onUninstallSelected()
                }
            },
            onDismiss = { pendingBulkAction = null },
        )
    }
}

/** The two destructive bulk actions, so the dialog is written once. */
private enum class BulkAction(val titleRes: Int, val messageRes: Int, val confirmRes: Int) {
    Remove(R.string.remove_apps_title, R.string.remove_apps_message, R.string.remove),
    Uninstall(R.string.uninstall_apps_title, R.string.uninstall_apps_message, R.string.uninstall),
}

@Composable
private fun AppListItemCard(
    app: AppListItemUi,
    inSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val icon = rememberInstalledAppIcon(app.packageName, enabled = app.isInstalled)

    // Expressive motion: a selected card morphs rounder and shifts to a tonal container.
    val cornerRadius by animateDpAsState(
        targetValue = if (app.isSelected) 28.dp else 20.dp,
        label = "cardCorner",
    )
    val containerColor by animateColorAsState(
        targetValue = if (app.isSelected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        label = "cardContainer",
    )
    val shape = RoundedCornerShape(cornerRadius)
    val contentAlpha = if (app.isInstalled) 1f else 0.5f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = shape,
        color = containerColor,
        tonalElevation = if (app.isSelected) 0.dp else 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = icon,
                contentDescription = stringResource(R.string.cd_app_icon, app.name),
                modifier = Modifier
                    .size(48.dp)
                    .alpha(contentAlpha),
                placeholder = painterResource(R.drawable.circle_24px),
                error = painterResource(R.drawable.x_circle_24px),
                fallback = painterResource(R.drawable.x_circle_24px),
            )
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .alpha(contentAlpha),
            ) {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                app.subtitle?.let {
                    Text(
                        text = it.asString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AnimatedVisibility(
                visible = inSelectionMode,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(8.dp))
                    Checkbox(checked = app.isSelected, onCheckedChange = { onClick() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppsListTopBar(
    inSelectionMode: Boolean,
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onRemoveClick: () -> Unit,
    onUninstallClick: () -> Unit,
) {
    val containerColor by animateColorAsState(
        targetValue = if (inSelectionMode) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        label = "topBarContainer",
    )

    TopAppBar(
        title = {
            AnimatedContent(targetState = inSelectionMode, label = "topBarTitle") { selecting ->
                if (selecting) {
                    Text(stringResource(R.string.selection_count, selectedCount))
                } else {
                    Text(stringResource(R.string.app_name))
                }
            }
        },
        navigationIcon = {
            if (inSelectionMode) {
                IconButton(onClick = onClearSelection) {
                    Icon(
                        painter = painterResource(R.drawable.close_24px),
                        contentDescription = stringResource(R.string.clear_selection),
                    )
                }
            }
        },
        actions = {
            if (inSelectionMode) {
                IconButton(onClick = onSelectAll) {
                    Icon(
                        painter = painterResource(R.drawable.check_24px),
                        contentDescription = stringResource(R.string.select_all),
                    )
                }
                SelectionOverflowMenu(
                    onRemoveClick = onRemoveClick,
                    onUninstallClick = onUninstallClick,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = containerColor),
    )
}

@Composable
private fun SelectionOverflowMenu(
    onRemoveClick: () -> Unit,
    onUninstallClick: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(R.drawable.more_vert_24px),
                contentDescription = stringResource(R.string.more_actions),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.remove_from_list)) },
                onClick = {
                    expanded = false
                    onRemoveClick()
                },
                leadingIcon = {
                    Icon(painterResource(R.drawable.delete_24px), contentDescription = null)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.uninstall_from_device)) },
                onClick = {
                    expanded = false
                    onUninstallClick()
                },
                leadingIcon = {
                    Icon(painterResource(R.drawable.apk_file_24px), contentDescription = null)
                },
            )
        }
    }
}
