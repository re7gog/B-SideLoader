package dev.re7gog.b_sideloader.ui.feature.apps

import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.domain.model.AppsListLayout
import dev.re7gog.b_sideloader.ui.common.component.AppIconPlaceholder
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
        onLayoutChange = viewModel::setLayout,
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
    onLayoutChange: (AppsListLayout) -> Unit,
    modifier: Modifier = Modifier,
    highlightedAppId: Long? = null,
) {
    var pendingBulkAction by remember { mutableStateOf<BulkAction?>(null) }

    // In selection mode, back clears the selection instead of leaving the screen.
    BackHandler(enabled = uiState.inSelectionMode, onBack = onClearSelection)

    // The list's own width, not the window's: in two panes this is the left one, and the details
    // pane beside it takes the width first — so a large screen can still leave the list too
    // narrow for rows. Then only cards fit, and there is no choice to offer.
    BoxWithConstraints(modifier) {
        val narrow = maxWidth < ROWS_MIN_WIDTH
        val layout = if (narrow) AppsListLayout.Grid else uiState.layout

        Scaffold(
            topBar = {
                AppsListTopBar(
                    inSelectionMode = uiState.inSelectionMode,
                    selectedCount = uiState.selectedCount,
                    updatableCount = uiState.updatableCount,
                    showUpdateAll = uiState.showUpdateAll,
                    layout = layout,
                    narrow = narrow,
                    onClearSelection = onClearSelection,
                    onSelectAll = onSelectAll,
                    onRemoveClick = { pendingBulkAction = BulkAction.Remove },
                    onUninstallClick = { pendingBulkAction = BulkAction.Uninstall },
                    onUpdateAll = onUpdateAll,
                    onLayoutChange = onLayoutChange,
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    // One grid for both layouts — rows are a grid of one column. Switching keeps
                    // the scroll position, and every card glides to its new place instead of the
                    // whole list being swapped out.
                    LazyVerticalGrid(
                        columns = when (layout) {
                            AppsListLayout.List -> GridCells.Fixed(1)
                            AppsListLayout.Grid -> GridCells.Adaptive(GRID_CELL_MIN_WIDTH)
                        },
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(CARD_SPACING),
                        horizontalArrangement = Arrangement.spacedBy(CARD_SPACING),
                    ) {
                        item(key = HINT_KEY, span = { GridItemSpan(maxLineSpan) }) {
                            LongPressHintCard(
                                visible = uiState.showLongPressHint,
                                onDismiss = onDismissLongPressHint,
                            )
                        }
                        items(uiState.apps, key = { it.id }) { app ->
                            // Above the layout switch, so changing layouts does not reload it.
                            val icon = rememberInstalledAppIcon(app.packageName, enabled = app.isInstalled)
                            val onClick = {
                                if (uiState.inSelectionMode) onToggleSelection(app.id) else onAppClick(app.id)
                            }
                            // Finding an update re-sorts the list under the user's finger. Without
                            // this the affected cards teleport; with it they visibly move up.
                            val itemModifier = Modifier.animateItem()
                            when (layout) {
                                AppsListLayout.List -> AppListItemCard(
                                    app = app,
                                    icon = icon,
                                    inSelectionMode = uiState.inSelectionMode,
                                    isHighlighted = app.id == highlightedAppId,
                                    onClick = onClick,
                                    onLongClick = { onLongPress(app.id) },
                                    onUpdate = { onUpdateApp(app.id) },
                                    modifier = itemModifier,
                                )
                                AppsListLayout.Grid -> AppGridTile(
                                    app = app,
                                    icon = icon,
                                    inSelectionMode = uiState.inSelectionMode,
                                    isHighlighted = app.id == highlightedAppId,
                                    onClick = onClick,
                                    onLongClick = { onLongPress(app.id) },
                                    onUpdate = { onUpdateApp(app.id) },
                                    modifier = itemModifier,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingBulkAction?.let { action ->
        ConfirmDialog(
            title = stringResource(action.titleRes),
            message = when (action) {
                BulkAction.Remove -> pluralStringResource(
                    R.plurals.remove_apps_message,
                    uiState.selectedCount,
                    uiState.selectedCount,
                )
                BulkAction.Uninstall -> stringResource(R.string.uninstall_apps_message)
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
private enum class BulkAction(val titleRes: Int, val confirmRes: Int) {
    Remove(R.string.remove_apps_title, R.string.remove),
    Uninstall(R.string.uninstall_apps_title, R.string.uninstall),
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

/**
 * What every app sits on, in either layout: tapping, long-pressing, the selected look and the
 * two-pane highlight are the same whether the card holds a row or a tile.
 */
@Composable
private fun AppCard(
    isSelected: Boolean,
    isHighlighted: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current

    // Expressive motion: a selected card morphs rounder and shifts to a tonal container.
    val cornerRadius by animateDpAsState(
        targetValue = if (isSelected) 28.dp else 20.dp,
        label = "cardCorner",
    )
    val containerColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.secondaryContainer
            isHighlighted -> MaterialTheme.colorScheme.surfaceContainerHighest
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        label = "cardContainer",
    )
    val shape = RoundedCornerShape(cornerRadius)

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
                if (isHighlighted && !isSelected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier
                }
            ),
        shape = shape,
        color = containerColor,
        tonalElevation = if (isSelected) 0.dp else 1.dp,
        content = content,
    )
}

@Composable
private fun AppListItemCard(
    app: AppListItemUi,
    icon: Drawable?,
    inSelectionMode: Boolean,
    isHighlighted: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current

    AppCard(
        isSelected = app.isSelected,
        isHighlighted = isHighlighted,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app = app, icon = icon, modifier = Modifier.size(APP_ICON_SIZE))
            Spacer(Modifier.width(16.dp))
            AppLabels(app = app, modifier = Modifier.weight(1f))
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

/**
 * The grid's card: the row's parts stacked, with the trailing slot moved to the top corner.
 *
 * Every tile is the same height whatever it holds — the corner is no taller than the icon, and the
 * subtitle line is kept even when empty — so the rows of the grid line up.
 */
@Composable
private fun AppGridTile(
    app: AppListItemUi,
    icon: Drawable?,
    inSelectionMode: Boolean,
    isHighlighted: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(
        isSelected = app.isSelected,
        isHighlighted = isHighlighted,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(APP_ICON_SIZE),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(app = app, icon = icon, modifier = Modifier.size(APP_ICON_SIZE))
                Spacer(Modifier.weight(1f))
                TileCornerAction(
                    app = app,
                    inSelectionMode = inSelectionMode,
                    onToggleSelection = onClick,
                    onUpdate = onUpdate,
                )
            }
            Spacer(Modifier.height(12.dp))
            AppLabels(app = app)
        }
    }
}

/** What a tile's corner holds, in the order the row's trailing slot gives way. */
private enum class CornerAction { None, Update, Updating, Select }

@Composable
private fun TileCornerAction(
    app: AppListItemUi,
    inSelectionMode: Boolean,
    onToggleSelection: () -> Unit,
    onUpdate: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val action = when {
        inSelectionMode -> CornerAction.Select
        app.isUpdating -> CornerAction.Updating
        app.canUpdate -> CornerAction.Update
        else -> CornerAction.None
    }

    AnimatedContent(
        targetState = action,
        transitionSpec = { (fadeIn() + scaleIn()) togetherWith (fadeOut() + scaleOut()) },
        label = "tileCornerAction",
    ) { target ->
        when (target) {
            CornerAction.None -> Unit
            CornerAction.Update -> FilledTonalIconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onUpdate()
                },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.update_24px),
                    contentDescription = stringResource(R.string.cd_update_app, app.name),
                )
            }
            CornerAction.Updating -> UpdateProgress(
                progress = app.updateProgress,
                modifier = Modifier.size(APP_ICON_SIZE),
            )
            CornerAction.Select -> Checkbox(
                checked = app.isSelected,
                onCheckedChange = { onToggleSelection() },
            )
        }
    }
}

/** The installed icon, or a letter for an app that is not on the device (dimmed with its text). */
@Composable
private fun AppIcon(
    app: AppListItemUi,
    icon: Drawable?,
    modifier: Modifier = Modifier,
) {
    Box(modifier.alpha(app.contentAlpha)) {
        if (icon != null) {
            AsyncImage(
                model = icon,
                contentDescription = stringResource(R.string.cd_app_icon, app.name),
                modifier = Modifier.fillMaxSize(),
                placeholder = painterResource(R.drawable.circle_24px),
                error = painterResource(R.drawable.x_circle_24px),
            )
        } else {
            AppIconPlaceholder(name = app.name, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun AppLabels(
    app: AppListItemUi,
    modifier: Modifier = Modifier,
) {
    Column(modifier.alpha(app.contentAlpha)) {
        // Ellipsised rather than wrapped: in the two-pane layout this lives in a narrow list
        // pane, and a wrapping name would make cards different heights for no benefit.
        Text(
            text = app.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = app.subtitle?.asString().orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** An app that is not on the device is drawn faded: it can be opened, but not updated. */
private val AppListItemUi.contentAlpha: Float get() = if (isInstalled) 1f else 0.5f

/** Compact per-row "Update", which becomes a progress ring for the app being installed. */
@Composable
private fun RowUpdateButton(
    isUpdating: Boolean,
    progress: Float?,
    appName: String,
    onClick: () -> Unit,
) {
    if (isUpdating) {
        UpdateProgress(progress = progress, modifier = Modifier.size(UPDATE_BUTTON_HEIGHT))
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

/** The ring an app's update button turns into while it installs; indeterminate without a figure. */
@Composable
private fun UpdateProgress(
    progress: Float?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (progress != null) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(24.dp),
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppsListTopBar(
    inSelectionMode: Boolean,
    selectedCount: Int,
    updatableCount: Int,
    showUpdateAll: Boolean,
    layout: AppsListLayout,
    narrow: Boolean,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onRemoveClick: () -> Unit,
    onUninstallClick: () -> Unit,
    onUpdateAll: () -> Unit,
    onLayoutChange: (AppsListLayout) -> Unit,
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
                // Ellipsised because this bar also lives in the list pane of the two-pane layout,
                // where the title and an "Update all (12)" button compete for the width.
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
                // A narrow list shows cards whatever the setting says, so a switch there would
                // do nothing.
                if (!narrow) {
                    LayoutToggleButton(layout = layout, onLayoutChange = onLayoutChange)
                }
                AnimatedVisibility(
                    visible = showUpdateAll,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally(),
                ) {
                    UpdateAllButton(count = updatableCount, compact = narrow, onClick = onUpdateAll)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = containerColor),
    )
}

/** Shows the layout a tap switches *to*, the usual convention for a two-way view switch. */
@Composable
private fun LayoutToggleButton(
    layout: AppsListLayout,
    onLayoutChange: (AppsListLayout) -> Unit,
) {
    val next = when (layout) {
        AppsListLayout.List -> AppsListLayout.Grid
        AppsListLayout.Grid -> AppsListLayout.List
    }
    IconButton(onClick = { onLayoutChange(next) }) {
        Crossfade(targetState = next, label = "layoutToggleIcon") { target ->
            when (target) {
                AppsListLayout.Grid -> Icon(
                    painter = painterResource(R.drawable.grid_view_24px),
                    contentDescription = stringResource(R.string.show_as_grid),
                )
                AppsListLayout.List -> Icon(
                    painter = painterResource(R.drawable.view_agenda_24px),
                    contentDescription = stringResource(R.string.show_as_list),
                )
            }
        }
    }
}

/**
 * "Update all (N)". In a [compact] bar — a list pane too narrow for rows — only the icon and the
 * count are drawn, so the title keeps some room; a screen reader still hears the whole label.
 */
@Composable
private fun UpdateAllButton(
    count: Int,
    compact: Boolean,
    onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val label = stringResource(R.string.update_all, count)

    FilledTonalButton(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onClick()
        },
        modifier = Modifier.padding(end = 8.dp),
        contentPadding = PaddingValues(horizontal = if (compact) 12.dp else 16.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.download_24px),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        if (compact) {
            Text(
                text = count.toString(),
                modifier = Modifier.semantics { contentDescription = label },
            )
        } else {
            Text(label)
        }
    }
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

/**
 * Narrowest list that still gets rows. Below it the name is squeezed to a handful of letters
 * between the icon and a labelled Update button — the Russian label, the longer one, included.
 */
private val ROWS_MIN_WIDTH = 320.dp

/**
 * Narrowest a card may get; the grid fits as many columns as this allows and shares out the rest.
 * Two fit on any phone and in the widest list pane of the two-pane layout.
 */
private val GRID_CELL_MIN_WIDTH = 150.dp

private val CARD_SPACING = 10.dp
private val APP_ICON_SIZE = 48.dp
private val UPDATE_BUTTON_HEIGHT = 36.dp
