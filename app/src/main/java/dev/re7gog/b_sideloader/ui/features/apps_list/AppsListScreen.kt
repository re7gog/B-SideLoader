package dev.re7gog.b_sideloader.ui.features.apps_list

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.TextButton
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
import dev.re7gog.b_sideloader.domain.model.AppWithDetails

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsListScreen(
    onGhAppClick: (id: Long, installed: Boolean) -> Unit,
    onTgAppClick: (id: Long, installed: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppsListViewModel = hiltViewModel()
) {
    val appsState by viewModel.appsState.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val refreshKey by viewModel.installedRefreshKey.collectAsStateWithLifecycle()

    val selectionMode = selectedIds.isNotEmpty()
    var showRemoveDialog by remember { mutableStateOf(false) }
    var showUninstallDialog by remember { mutableStateOf(false) }

    // In selection mode the system back button clears the selection instead of leaving the screen.
    BackHandler(enabled = selectionMode) { viewModel.clearSelection() }

    Scaffold(
        topBar = {
            AppsListTopBar(
                selectionMode = selectionMode,
                selectedCount = selectedIds.size,
                onClearSelection = { viewModel.clearSelection() },
                onSelectAll = { viewModel.selectAll() },
                onRemoveClick = { showRemoveDialog = true },
                onUninstallClick = { showUninstallDialog = true }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        if (appsState.isEmpty()) {
            EmptyAppsPlaceholder(modifier = Modifier.padding(paddingValues))
        } else {
            LazyColumn(
                modifier = Modifier.padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = appsState,
                    key = { it.app.id }
                ) { appItem ->
                    AppItemCard(
                        appItem = appItem,
                        selectionMode = selectionMode,
                        selected = appItem.app.id in selectedIds,
                        refreshKey = refreshKey,
                        onClick = {
                            if (selectionMode) {
                                viewModel.toggleSelection(appItem.app.id)
                            } else {
                                val installed = viewModel.isPackageInstalled(appItem.app.packageName)
                                if (appItem.githubDetails != null) {
                                    onGhAppClick(appItem.app.id, installed)
                                } else if (appItem.telegramDetails != null) {
                                    onTgAppClick(appItem.app.id, installed)
                                }
                            }
                        },
                        onLongClick = { viewModel.toggleSelection(appItem.app.id) },
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    if (showRemoveDialog) {
        BulkConfirmDialog(
            title = stringResource(R.string.remove_apps_title),
            message = stringResource(R.string.remove_apps_message, selectedIds.size),
            confirmLabel = stringResource(R.string.remove),
            onConfirm = {
                viewModel.deleteSelectedFromDb()
                showRemoveDialog = false
            },
            onDismiss = { showRemoveDialog = false }
        )
    }
    if (showUninstallDialog) {
        BulkConfirmDialog(
            title = stringResource(R.string.uninstall_apps_title),
            message = stringResource(R.string.uninstall_apps_message),
            confirmLabel = stringResource(R.string.uninstall),
            onConfirm = {
                viewModel.uninstallSelected()
                showUninstallDialog = false
            },
            onDismiss = { showUninstallDialog = false }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppItemCard(
    appItem: AppWithDetails,
    selectionMode: Boolean,
    selected: Boolean,
    refreshKey: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    viewModel: AppsListViewModel,
) {
    val packageName = appItem.app.packageName
    val isAppInstalled = remember(packageName, refreshKey) {
        viewModel.isPackageInstalled(packageName)
    }
    val appIcon = remember(isAppInstalled, packageName, refreshKey) {
        if (isAppInstalled) viewModel.getAppIcon(packageName) else null
    }

    // Expressive motion: selected cards morph to a rounder shape and shift to a tonal container.
    val cornerRadius by animateDpAsState(
        targetValue = if (selected) 28.dp else 20.dp,
        label = "cardCorner"
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        label = "cardContainer"
    )

    val shape = RoundedCornerShape(cornerRadius)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = shape,
        color = containerColor,
        tonalElevation = if (selected) 0.dp else 1.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = appIcon,
                contentDescription = "Icon of ${appItem.app.name} app",
                modifier = Modifier
                    .size(48.dp)
                    .alpha(if (isAppInstalled) 1f else 0.5f),
                placeholder = painterResource(R.drawable.circle_24px),
                error = painterResource(R.drawable.x_circle_24px)
            )
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .alpha(if (isAppInstalled) 1f else 0.5f)
            ) {
                Text(
                    text = appItem.app.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                val subtitle = when {
                    appItem.githubDetails != null -> "by ${appItem.githubDetails.owner}"
                    else -> null
                }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            AnimatedVisibility(
                visible = selectionMode,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(8.dp))
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onClick() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsListTopBar(
    selectionMode: Boolean,
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onRemoveClick: () -> Unit,
    onUninstallClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = if (selectionMode) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        label = "topBarContainer"
    )

    TopAppBar(
        title = {
            AnimatedContent(
                targetState = selectionMode,
                label = "topBarTitle"
            ) { inSelection ->
                if (inSelection) {
                    Text(stringResource(R.string.selection_count, selectedCount))
                } else {
                    Text(stringResource(R.string.app_name))
                }
            }
        },
        navigationIcon = {
            if (selectionMode) {
                IconButton(onClick = onClearSelection) {
                    Icon(
                        painter = painterResource(R.drawable.close_24px),
                        contentDescription = stringResource(R.string.clear_selection)
                    )
                }
            }
        },
        actions = {
            if (selectionMode) {
                IconButton(onClick = onSelectAll) {
                    Icon(
                        painter = painterResource(R.drawable.check_24px),
                        contentDescription = stringResource(R.string.select_all)
                    )
                }
                SelectionOverflowMenu(
                    onRemoveClick = onRemoveClick,
                    onUninstallClick = onUninstallClick
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = containerColor),
        modifier = modifier
    )
}

@Composable
private fun SelectionOverflowMenu(
    onRemoveClick: () -> Unit,
    onUninstallClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(R.drawable.more_vert_24px),
                contentDescription = stringResource(R.string.more_actions)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.remove_from_list)) },
                onClick = {
                    expanded = false
                    onRemoveClick()
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.delete_24px),
                        contentDescription = null
                    )
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.uninstall_from_device)) },
                onClick = {
                    expanded = false
                    onUninstallClick()
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.apk_file_24px),
                        contentDescription = null
                    )
                }
            )
        }
    }
}

@Composable
private fun BulkConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun EmptyAppsPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.apps_24px),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.size(16.dp))
            Text(
                text = stringResource(R.string.no_apps_title),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = stringResource(R.string.no_apps_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
