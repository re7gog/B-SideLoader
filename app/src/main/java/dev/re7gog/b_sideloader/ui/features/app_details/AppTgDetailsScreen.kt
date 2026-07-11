package dev.re7gog.b_sideloader.ui.features.app_details

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.data.telegram.TgApkCandidate
import dev.re7gog.b_sideloader.ui.components.TelegramAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTgDetailsScreen(
    onBack: () -> Unit,
    onInstalledExit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppTgDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val installSucceeded by viewModel.installSucceeded.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // After a successful install, back goes to the apps list; otherwise to search
    val handleBack: () -> Unit = { if (installSucceeded) onInstalledExit() else onBack() }
    BackHandler(onBack = handleBack)

    val state = uiState
    if (state != null) {
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(state.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = handleBack) {
                            Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.cd_back))
                        }
                    }
                )
            },
            modifier = modifier
        ) { paddingValues ->
            AppTgDetailsContent(
                uiState = state,
                viewModel = viewModel,
                onDeleted = onBack,
                modifier = Modifier.padding(paddingValues)
            )
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@Composable
fun AppTgDetailsContent(
    uiState: AppTgDetailsUiState,
    viewModel: AppTgDetailsViewModel,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredMessages by viewModel.filteredApkMessages.collectAsStateWithLifecycle()
    val targetApkMessage by viewModel.targetApkMessage.collectAsStateWithLifecycle()
    val includeFilter by viewModel.includeFilter.collectAsStateWithLifecycle()
    val excludeFilter by viewModel.excludeFilter.collectAsStateWithLifecycle()
    val msgIncludeFilter by viewModel.msgIncludeFilter.collectAsStateWithLifecycle()
    val msgExcludeFilter by viewModel.msgExcludeFilter.collectAsStateWithLifecycle()
    val advancedMode by viewModel.advancedMode.collectAsStateWithLifecycle()
    val shouldUpdate by viewModel.shouldUpdate.collectAsStateWithLifecycle()
    val shouldSave by viewModel.shouldSave.collectAsStateWithLifecycle()
    val isInstalling by viewModel.isInstalling.collectAsStateWithLifecycle()
    val installProgress by viewModel.installProgress.collectAsStateWithLifecycle()
    val channelPhotoFileId by viewModel.channelPhotoFileId.collectAsStateWithLifecycle()

    val isInstalled = uiState.installed
    val appIcon = remember(isInstalled, uiState.packageName) {
        if (isInstalled && uiState.packageName != "") viewModel.getAppIcon(uiState.packageName) else null
    }
    val hasTarget = targetApkMessage != null
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Decide the primary action. Order matters: a fresh (from-search) app always
    // installs on the first tap (its edited fields are saved on install success).
    val primaryLabel: String
    val primaryEnabled: Boolean
    val primaryOnClick: () -> Unit
    when {
        !uiState.isFromDb -> {
            primaryLabel = stringResource(R.string.save_and_install); primaryEnabled = hasTarget
            primaryOnClick = viewModel::installAppTg
        }
        shouldSave -> {
            primaryLabel = stringResource(R.string.save_changes); primaryEnabled = true
            primaryOnClick = viewModel::saveToDb
        }
        shouldUpdate -> {
            primaryLabel = stringResource(R.string.update); primaryEnabled = hasTarget
            primaryOnClick = viewModel::installAppTg
        }
        !isInstalled -> {
            primaryLabel = stringResource(R.string.install); primaryEnabled = hasTarget
            primaryOnClick = viewModel::installAppTg
        }
        else -> {
            primaryLabel = stringResource(R.string.open); primaryEnabled = true
            primaryOnClick = viewModel::openApp
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (appIcon != null) {
                    AsyncImage(
                        model = appIcon,
                        contentDescription = null,
                        modifier = Modifier.size(96.dp).clip(RoundedCornerShape(24.dp))
                    )
                } else {
                    TelegramAvatar(
                        fallbackText = uiState.name.take(1).uppercase().ifEmpty { "?" },
                        photoFileId = channelPhotoFileId,
                        downloadPhoto = viewModel::downloadPhoto,
                        modifier = Modifier.size(96.dp),
                        textStyle = MaterialTheme.typography.headlineMedium
                    )
                }
                Column {
                    Text(
                        uiState.name,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        stringResource(R.string.telegram_channel),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (uiState.version.isNotEmpty()) {
                        Text(
                            stringResource(R.string.installed_message_id, uiState.version),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        item {
            DetailActionArea(
                label = primaryLabel,
                enabled = primaryEnabled,
                isInstalling = isInstalling,
                progress = installProgress,
                onClick = primaryOnClick
            )
        }
        item {
            DetailSecondaryActions(
                showUninstall = isInstalled,
                showDelete = uiState.isFromDb,
                onUninstall = viewModel::uninstallApp,
                onDelete = { showDeleteDialog = true }
            )
        }

        item { DetailAutoupdateRow(uiState.autoupdate, viewModel::onAutoUpdateChange) }

        item { DetailSectionLabel(stringResource(R.string.filters)) }
        item {
            DetailSwitchRow(
                title = stringResource(R.string.advanced_filters),
                description = stringResource(R.string.advanced_filters_description),
                checked = advancedMode,
                onCheckedChange = viewModel::onAdvancedModeChange
            )
        }
        item {
            DetailFilterField(includeFilter, viewModel::onIncludeFilterChange,
                stringResource(if (advancedMode) R.string.tg_apk_regex_include else R.string.tg_apk_must_contain))
        }
        item {
            DetailFilterField(excludeFilter, viewModel::onExcludeFilterChange,
                stringResource(if (advancedMode) R.string.tg_apk_regex_exclude else R.string.tg_apk_must_not_contain))
        }
        item {
            DetailFilterField(msgIncludeFilter, viewModel::onMsgIncludeFilterChange,
                stringResource(if (advancedMode) R.string.message_regex_include else R.string.message_must_contain))
        }
        item {
            DetailFilterField(msgExcludeFilter, viewModel::onMsgExcludeFilterChange,
                stringResource(if (advancedMode) R.string.message_regex_exclude else R.string.message_must_not_contain))
        }

        item { DetailSectionLabel(stringResource(R.string.available_apks)) }
        if (filteredMessages.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.no_matching_apks),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(filteredMessages, key = { it.id }) { message ->
            ApkMessageStaticRow(message, message.id == targetApkMessage?.id)
        }
    }

    if (showDeleteDialog) {
        DeleteConfirmDialog(
            appName = uiState.name,
            onConfirm = {
                showDeleteDialog = false
                viewModel.deleteApp(onDeleted)
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

@Composable
fun ApkMessageStaticRow(message: TgApkCandidate, isTarget: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (isTarget) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainer,
        border = if (isTarget) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                message.file.fileName,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (message.msgText.isNotEmpty()) {
                Text(
                    message.msgText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                stringResource(R.string.size_mb, message.file.document.size / 1024 / 1024),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
