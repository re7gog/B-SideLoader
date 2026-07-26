package dev.re7gog.b_sideloader.ui.feature.apps

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.ui.common.component.ConfirmDialog
import dev.re7gog.b_sideloader.ui.common.component.EmptyState
import dev.re7gog.b_sideloader.ui.common.component.SnackbarMessages
import dev.re7gog.b_sideloader.ui.common.component.rememberInstalledAppIcon
import dev.re7gog.b_sideloader.ui.common.text.asString

/**
 * The tracked apps, with multi-select for bulk remove/uninstall and inline updating.
 *
 * Stateless with respect to its data: everything comes from one [AppsListUiState], so the whole
 * screen can be rendered in a test or a preview from a literal.
 */
@Composable
fun AppsListScreen(
    onAppClick: (appId: Long) -> Unit,
    modifier: Modifier = Modifier,
    /** Set in the two-pane layout so the row whose details are showing stays marked. */
    highlightedAppId: Long? = null,
    viewModel: AppsListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    SnackbarMessages(messages = viewModel.messages, hostState = snackbarHostState)

    AppsListScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onAppClick = onAppClick,
        onToggleSelection = viewModel::toggleSelection,
        onLongPress = viewModel::onAppLongPress,
        onClearSelection = viewModel::clearSelection,
        onSelectAll = viewModel::selectAll,
        onRemoveSelected = viewModel::removeSelectedFromList,
        onUninstallSelected = viewModel::uninstallSelected,
        onRefresh = viewModel::refresh,
        onUpdateApp = viewModel::updateApp,
        onUpdateAll = viewModel::updateAll,
        onDismissLongPressHint = viewModel::dismissLongPressHint,
        highlightedAppId = highlightedAppId,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsListScreen(
    uiState: AppsListUiState,
    snackbarHostState: SnackbarHostState,
    onAppClick: (appId: Long) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onLongPress: (Long) -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onRemoveSelected: () -> Unit,
    onUninstallSelected: () -> Unit,
    onRefresh: () -> Unit,
    onUpdateApp: (Long) -> Unit,
    onUpdateAll: () -> Unit,
    onDismissLongPressHint: () -> Unit,
    modifier: Modifier = Modifier,
    highlightedAppId: Long? = null,
) {
    var pendingBulkAction by remember { mutableStateOf<BulkAction?>(null) }

    // In selection mode, back clears the selection instead of leaving the screen.
    BackHandler(enabled = uiState.inSelectionMode, onBack = onClearSelection)

    Scaffold(
        topBar = {
            AppsListTopBar(
                inSelectionMode = uiState.inSelectionMode,
                selectedCount = uiState.selectedCount,
                updatableCount = uiState.updatableCount,
                showUpdateAll = uiState.showUpdateAll,
                onClearSelection = onClearSelection,
                onSelectAll = onSelectAll,
                onRemoveClick = { pendingBulkAction = BulkAction.Remove },
                onUninstallClick = { pendingBulkAction = BulkAction.Uninstall },
                onUpdateAll = onUpdateAll,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (uiState.isEmpty) {
                EmptyState(
                    iconRes = R.drawable.apps_24px,
                    title = stringResource(R.string.no_apps_title),
                    subtitle = stringResource(R.string.no_apps_subtitle),
                    tinted = true,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(key = HINT_KEY) {
                        LongPressHintCard(
                            visible = uiState.showLongPressHint,
                            onDismiss = onDismissLongPressHint,
                        )
                    }
                    items(uiState.apps, key = { it.id }) { app ->
                        AppListItemCard(
                            app = app,
                            inSelectionMode = uiState.inSelectionMode,
                            isHighlighted = app.id == highlightedAppId,
                            onClick = {
                                if (uiState.inSelectionMode) onToggleSelection(app.id) else onAppClick(app.id)
                            },
                            onLongClick = { onLongPress(app.id) },
                            onUpdate = { onUpdateApp(app.id) },
                            // Finding an update re-sorts the list under the user's finger. Without
                            // this the affected rows teleport; with it they visibly move up.
                            modifier = Modifier.animateItem(),
                        )
                    }
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

/**
 * One-time note that a long press starts a selection.
 *
 * Kept as a list item rather than a dialog: a modal on first launch is the thing people dismiss
 * without reading, and this one has to survive being ignored until the user is curious.
 */
@Composable
private fun LongPressHintCard(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.info_24px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.hint_long_press),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        painter = painterResource(R.drawable.close_24px),
                        contentDescription = stringResource(R.string.hint_dismiss),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppListItemCard(
    app: AppListItemUi,
    inSelectionMode: Boolean,
    isHighlighted: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon = rememberInstalledAppIcon(app.packageName, enabled = app.isInstalled)
    val haptics = LocalHapticFeedback.current

    // Expressive motion: a selected card morphs rounder and shifts to a tonal container.
    val cornerRadius by animateDpAsState(
        targetValue = if (app.isSelected) 28.dp else 20.dp,
        label = "cardCorner",
    )
    val containerColor by animateColorAsState(
        targetValue = when {
            app.isSelected -> MaterialTheme.colorScheme.secondaryContainer
            isHighlighted -> MaterialTheme.colorScheme.surfaceContainerHighest
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        label = "cardContainer",
    )
    val shape = RoundedCornerShape(cornerRadius)
    val contentAlpha = if (app.isInstalled) 1f else 0.5f

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
            .then(
                if (isHighlighted && !app.isSelected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier
                }
            ),
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
                // Ellipsised rather than wrapped: in the two-pane layout this row lives in a
                // 320 dp list pane alongside a 48 dp icon and an Update button, and a wrapping
                // name would make rows different heights for no benefit.
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                app.subtitle?.let {
                    Text(
                        text = it.asString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // The update affordance and the checkbox share the trailing slot: in selection mode
            // the row is about picking apps, not about acting on one of them.
            AnimatedVisibility(
                visible = !inSelectionMode && (app.canUpdate || app.isUpdating),
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(8.dp))
                    RowUpdateButton(
                        isUpdating = app.isUpdating,
                        progress = app.updateProgress,
                        appName = app.name,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onUpdate()
                        },
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

/** Compact per-row "Update", which becomes a progress ring for the app being installed. */
@Composable
private fun RowUpdateButton(
    isUpdating: Boolean,
    progress: Float?,
    appName: String,
    onClick: () -> Unit,
) {
    if (isUpdating) {
        Box(
            modifier = Modifier.size(UPDATE_BUTTON_HEIGHT),
            contentAlignment = Alignment.Center,
        ) {
            if (progress != null) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(24.dp),
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
        return
    }

    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.height(UPDATE_BUTTON_HEIGHT),
        contentPadding = PaddingValues(horizontal = 14.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Icon(
            painter = painterResource(R.drawable.update_24px),
            contentDescription = stringResource(R.string.cd_update_app, appName),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.update), style = MaterialTheme.typography.labelLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppsListTopBar(
    inSelectionMode: Boolean,
    selectedCount: Int,
    updatableCount: Int,
    showUpdateAll: Boolean,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onRemoveClick: () -> Unit,
    onUninstallClick: () -> Unit,
    onUpdateAll: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
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
                // Ellipsised because this bar also lives in the 360 dp list pane of the two-pane
                // layout, where the title and an "Update all (12)" button compete for the width.
                Text(
                    text = if (selecting) {
                        stringResource(R.string.selection_count, selectedCount)
                    } else {
                        stringResource(R.string.app_name)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
            } else {
                AnimatedVisibility(
                    visible = showUpdateAll,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally(),
                ) {
                    FilledTonalButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onUpdateAll()
                        },
                        modifier = Modifier.padding(end = 8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.download_24px),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.update_all, updatableCount))
                    }
                }
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

/** Stable key for the hint row so adding it never re-keys the app rows below it. */
private const val HINT_KEY = "long-press-hint"

private val UPDATE_BUTTON_HEIGHT = 36.dp
