package dev.re7gog.b_sideloader.ui.feature.appdetails

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.domain.model.AppSource
import dev.re7gog.b_sideloader.domain.model.UpdateCandidate
import dev.re7gog.b_sideloader.ui.common.component.ConfirmDialog
import dev.re7gog.b_sideloader.ui.common.component.FilterField
import dev.re7gog.b_sideloader.ui.common.component.SectionDefaults
import dev.re7gog.b_sideloader.ui.common.component.LoadingState
import dev.re7gog.b_sideloader.ui.common.component.PrimaryActionArea
import dev.re7gog.b_sideloader.ui.common.component.SecondaryActions
import dev.re7gog.b_sideloader.ui.common.component.SectionLabel
import dev.re7gog.b_sideloader.ui.common.component.SnackbarMessages
import dev.re7gog.b_sideloader.ui.common.component.SwitchCard
import dev.re7gog.b_sideloader.ui.common.component.TelegramAvatar
import dev.re7gog.b_sideloader.ui.common.component.rememberInstalledAppIcon

/**
 * App details, for either source.
 *
 * One screen where there used to be two near-identical ones. The source only decides the header
 * and which extra filter fields appear; the action area, autoupdate toggle, filter section and
 * candidate list are shared.
 */
@Composable
fun AppDetailsScreen(
    args: AppDetailsArgs,
    onBack: () -> Unit,
    onFinishedFromSearch: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * False when this screen is the detail pane of a two-pane layout: the list next to it is
     * already the way "back", so a second one would be noise — and, more importantly, a
     * `BackHandler` here would swallow the gesture that should leave the app.
     */
    showBackAffordance: Boolean = true,
    viewModel: AppDetailsViewModel = hiltViewModel<AppDetailsViewModel, AppDetailsViewModel.Factory>(
        creationCallback = { factory -> factory.create(args) },
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    SnackbarMessages(messages = viewModel.messages, hostState = snackbarHostState)

    AppDetailsScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onFinishedFromSearch = onFinishedFromSearch,
        onPrimaryAction = viewModel::onPrimaryAction,
        onUninstall = viewModel::onUninstall,
        onDelete = { viewModel.onDelete(onBack) },
        onNameChange = viewModel::onNameChange,
        onAutoUpdateChange = viewModel::onAutoUpdateChange,
        onFilterModeChange = viewModel::onFilterModeChange,
        onAssetIncludeChange = viewModel::onAssetIncludeChange,
        onAssetExcludeChange = viewModel::onAssetExcludeChange,
        onReleaseIncludeChange = viewModel::onReleaseIncludeChange,
        onReleaseExcludeChange = viewModel::onReleaseExcludeChange,
        onPrereleasesChange = viewModel::onPrereleasesChange,
        onMessageIncludeChange = viewModel::onMessageIncludeChange,
        onMessageExcludeChange = viewModel::onMessageExcludeChange,
        downloadPhoto = viewModel::downloadPhoto,
        modifier = modifier,
        showBackAffordance = showBackAffordance,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailsScreen(
    uiState: AppDetailsUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onFinishedFromSearch: () -> Unit,
    onPrimaryAction: () -> Unit,
    onUninstall: () -> Unit,
    onDelete: () -> Unit,
    onNameChange: (String) -> Unit,
    onAutoUpdateChange: (Boolean) -> Unit,
    onFilterModeChange: (Boolean) -> Unit,
    onAssetIncludeChange: (String) -> Unit,
    onAssetExcludeChange: (String) -> Unit,
    onReleaseIncludeChange: (String) -> Unit,
    onReleaseExcludeChange: (String) -> Unit,
    onPrereleasesChange: (Boolean) -> Unit,
    onMessageIncludeChange: (String) -> Unit,
    onMessageExcludeChange: (String) -> Unit,
    downloadPhoto: suspend (Int) -> String?,
    modifier: Modifier = Modifier,
    showBackAffordance: Boolean = true,
) {
    // After a successful install started from search, back goes to the apps list — the search
    // results the user came from are a dead end once the app is tracked.
    val handleBack: () -> Unit = {
        if (uiState.installSucceeded) onFinishedFromSearch() else onBack()
    }
    BackHandler(enabled = showBackAffordance, onBack = handleBack)

    var showDeleteDialog by remember { mutableStateOf(false) }
    val app = uiState.app

    if (uiState.isLoading || app == null) {
        LoadingState(modifier)
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // No title here: the heading below already carries the app's name, in a size the page
            // is actually built around. A bar with only a back button is all that is left, so on
            // the detail pane of a two-pane layout — where there is nothing to go back to — the
            // bar is dropped entirely rather than left as an empty strip.
            if (showBackAffordance) {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = handleBack) {
                            Icon(
                                painterResource(R.drawable.arrow_back_24px),
                                contentDescription = stringResource(R.string.cd_back),
                            )
                        }
                    },
                )
            }
        },
        modifier = modifier,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                DetailsHeader(
                    name = app.name,
                    headline = uiState.headline,
                    packageName = app.packageName,
                    isInstalled = uiState.isInstalled,
                    version = app.version.raw,
                    downloadPhoto = downloadPhoto,
                    onNameChange = onNameChange,
                )
            }

            (uiState.headline as? HeadlineUi.GitHub)?.let { github ->
                item {
                    Text(
                        text = github.description ?: stringResource(R.string.no_description),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            item {
                PrimaryActionArea(
                    label = stringResource(uiState.primaryAction.labelRes),
                    onClick = onPrimaryAction,
                    enabled = uiState.isPrimaryEnabled,
                    inProgress = uiState.isInstalling,
                    fraction = uiState.install?.fraction,
                )
            }
            item {
                SecondaryActions(
                    showUninstall = uiState.isInstalled,
                    showRemove = uiState.isSaved,
                    onUninstall = onUninstall,
                    onRemove = { showDeleteDialog = true },
                )
            }

            item {
                SwitchCard(
                    title = stringResource(R.string.autoupdate),
                    subtitle = stringResource(R.string.autoupdate_description),
                    checked = app.autoUpdate,
                    onCheckedChange = onAutoUpdateChange,
                )
            }

            item { SectionLabel(stringResource(R.string.filters)) }
            item {
                SwitchCard(
                    title = stringResource(R.string.advanced_filters),
                    subtitle = stringResource(R.string.advanced_filters_description),
                    checked = app.filterMode.isAdvanced,
                    onCheckedChange = onFilterModeChange,
                )
            }

            val advanced = app.filterMode.isAdvanced
            when (val source = app.source) {
                is AppSource.GitHub -> {
                    item { SectionLabel(stringResource(R.string.release_filters)) }
                    item {
                        SwitchCard(
                            title = stringResource(R.string.prereleases),
                            subtitle = stringResource(R.string.prereleases_description),
                            checked = source.usePrereleases,
                            onCheckedChange = onPrereleasesChange,
                        )
                    }
                    item {
                        FilterField(
                            value = source.releaseFilter.include,
                            onValueChange = onReleaseIncludeChange,
                            label = stringResource(
                                if (advanced) R.string.release_regex_include
                                else R.string.release_must_contain
                            ),
                        )
                    }
                    item {
                        FilterField(
                            value = source.releaseFilter.exclude,
                            onValueChange = onReleaseExcludeChange,
                            label = stringResource(
                                if (advanced) R.string.release_regex_exclude
                                else R.string.release_must_not_contain
                            ),
                        )
                    }
                }

                is AppSource.Telegram -> {
                    item { SectionLabel(stringResource(R.string.message_filters)) }
                    item {
                        FilterField(
                            value = source.messageFilter.include,
                            onValueChange = onMessageIncludeChange,
                            label = stringResource(
                                if (advanced) R.string.message_regex_include
                                else R.string.message_must_contain
                            ),
                        )
                    }
                    item {
                        FilterField(
                            value = source.messageFilter.exclude,
                            onValueChange = onMessageExcludeChange,
                            label = stringResource(
                                if (advanced) R.string.message_regex_exclude
                                else R.string.message_must_not_contain
                            ),
                        )
                    }
                }
            }

            item { SectionLabel(stringResource(R.string.apk_filters)) }
            item {
                FilterField(
                    value = app.assetFilter.include,
                    onValueChange = onAssetIncludeChange,
                    label = stringResource(
                        if (advanced) R.string.apk_regex_include else R.string.apk_must_contain
                    ),
                )
            }
            item {
                FilterField(
                    value = app.assetFilter.exclude,
                    onValueChange = onAssetExcludeChange,
                    label = stringResource(
                        if (advanced) R.string.apk_regex_exclude else R.string.apk_must_not_contain
                    ),
                )
            }

            item { SectionLabel(stringResource(R.string.available_apks)) }
            if (uiState.candidates.isEmpty()) {
                item {
                    Text(
                        text = stringResource(
                            if (uiState.isResolving) R.string.checking_for_updates
                            else R.string.no_matching_apks
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(uiState.candidates, key = { it.fileName + it.version.raw }) { candidate ->
                CandidateCard(
                    candidate = candidate,
                    isTarget = candidate == uiState.target,
                )
            }
        }
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = stringResource(R.string.remove_app_title),
            message = stringResource(R.string.remove_app_message, app.name),
            confirmLabel = stringResource(R.string.remove),
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}

@Composable
private fun DetailsHeader(
    name: String,
    headline: HeadlineUi?,
    packageName: String,
    isInstalled: Boolean,
    version: String,
    downloadPhoto: suspend (Int) -> String?,
    onNameChange: (String) -> Unit,
) {
    val installedIcon = rememberInstalledAppIcon(packageName, enabled = isInstalled)

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        when {
            installedIcon != null -> AsyncImage(
                model = installedIcon,
                contentDescription = null,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(24.dp)),
            )

            headline is HeadlineUi.GitHub -> AsyncImage(
                model = headline.avatarUrl,
                placeholder = painterResource(R.drawable.circle_24px),
                error = painterResource(R.drawable.x_circle_24px),
                fallback = painterResource(R.drawable.x_circle_24px),
                contentDescription = null,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(24.dp)),
            )

            headline is HeadlineUi.Telegram -> TelegramAvatar(
                fallbackText = name.take(1).uppercase().ifEmpty { "?" },
                photoFileId = headline.photoFileId,
                downloadPhoto = downloadPhoto,
                modifier = Modifier.size(96.dp),
                textStyle = MaterialTheme.typography.headlineMedium,
            )

            else -> Unit
        }

        // Weighted so a long name ellipsises instead of pushing the pencil off the edge.
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                RenameButton(name = name, onNameChange = onNameChange)
            }
            when (headline) {
                is HeadlineUi.GitHub -> {
                    Text(
                        text = headline.owner,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.stars_count, headline.stars),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                is HeadlineUi.Telegram -> Text(
                    text = stringResource(R.string.telegram_channel),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                null -> Unit
            }
            if (version.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.version_label, version),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/**
 * Pencil beside the heading that opens a small rename flyout.
 *
 * A popup anchored to the button rather than a dialog: renaming is a one-field edit, and dimming
 * the whole page for it overstates what is happening. The draft stays local until confirmed, so
 * dismissing the flyout leaves the app untouched and never puts the page into "unsaved changes"
 * by accident.
 */
@Composable
private fun RenameButton(
    name: String,
    onNameChange: (String) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { editing = true }) {
            Icon(
                painter = painterResource(R.drawable.edit_24px),
                contentDescription = stringResource(R.string.cd_edit_name),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (editing) {
            RenameFlyout(
                initialName = name,
                onConfirm = {
                    onNameChange(it)
                    editing = false
                },
                onDismiss = { editing = false },
            )
        }
    }
}

@Composable
private fun RenameFlyout(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Keyed on the name it opened with, so reopening starts from what is on screen rather than
    // from a stale draft left behind by a cancelled edit.
    var draft by remember(initialName) { mutableStateOf(initialName) }
    val isValid = draft.isNotBlank()
    val focusRequester = remember { FocusRequester() }
    val density = LocalDensity.current

    // A popup window appears instantly, so the entrance is animated by a state that flips right
    // after the first composition rather than by the popup itself.
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = true

    Popup(
        alignment = Alignment.TopEnd,
        offset = with(density) { IntOffset(x = 0, y = FLYOUT_ANCHOR_OFFSET.roundToPx()) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        AnimatedVisibility(
            visibleState = visibility,
            enter = fadeIn(tween(FLYOUT_ENTER_MILLIS)) +
                scaleIn(tween(FLYOUT_ENTER_MILLIS), initialScale = 0.92f),
            exit = fadeOut(),
        ) {
            Surface(
                shape = RoundedCornerShape(SectionDefaults.GroupCorner),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                tonalElevation = 3.dp,
                shadowElevation = 6.dp,
                modifier = Modifier.width(FLYOUT_WIDTH),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.app_name_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        isError = !isValid,
                        supportingText = if (isValid) {
                            null
                        } else {
                            { Text(stringResource(R.string.app_name_required)) }
                        },
                        shape = RoundedCornerShape(SectionDefaults.FieldCorner),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = { if (isValid) onConfirm(draft.trim()) },
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.cancel))
                        }
                        TextButton(
                            onClick = { onConfirm(draft.trim()) },
                            enabled = isValid,
                        ) {
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            }
        }
    }

    // Only once the entrance has settled: the field lives inside the AnimatedVisibility above, and
    // requesting focus on a requester that is not attached to a composed node yet throws.
    LaunchedEffect(visibility.currentState) {
        if (visibility.currentState) focusRequester.requestFocus()
    }
}

private val FLYOUT_WIDTH = 288.dp

/** Drops the flyout clear of the pencil it hangs from. */
private val FLYOUT_ANCHOR_OFFSET = 44.dp

private const val FLYOUT_ENTER_MILLIS = 160

/** One matching APK. The one that would actually be installed is outlined. */
@Composable
private fun CandidateCard(candidate: UpdateCandidate, isTarget: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (isTarget) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = if (isTarget) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = candidate.fileName,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            candidate.notes?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            candidate.sizeBytes?.let { bytes ->
                Text(
                    text = stringResource(R.string.size_mb, bytes / BYTES_PER_MB),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val PrimaryAction.labelRes: Int
    get() = when (this) {
        PrimaryAction.SaveAndInstall -> R.string.save_and_install
        PrimaryAction.SaveChanges -> R.string.save_changes
        PrimaryAction.Update -> R.string.update
        PrimaryAction.Install -> R.string.install
        PrimaryAction.Open -> R.string.open
    }

private const val BYTES_PER_MB = 1024L * 1024L
