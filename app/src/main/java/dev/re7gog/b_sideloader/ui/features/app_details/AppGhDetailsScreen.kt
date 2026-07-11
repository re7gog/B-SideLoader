package dev.re7gog.b_sideloader.ui.features.app_details

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppGhDetailsScreen(
    onBack: () -> Unit,
    onInstalledExit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppGhDetailsViewModel = hiltViewModel()
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
            AppGhDetailsContent(
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
fun AppGhDetailsContent(
    uiState: AppGhDetailsUiState,
    viewModel: AppGhDetailsViewModel,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shouldUpdate by viewModel.shouldUpdate.collectAsStateWithLifecycle()
    val shouldSave by viewModel.shouldSave.collectAsStateWithLifecycle()
    val isInstalling by viewModel.isInstalling.collectAsStateWithLifecycle()
    val installProgress by viewModel.installProgress.collectAsStateWithLifecycle()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsStateWithLifecycle()

    val isInstalled = uiState.installed
    val appIcon = remember(isInstalled, uiState.packageName) {
        if (isInstalled && uiState.packageName != "") viewModel.getAppIcon(uiState.packageName) else null
    }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val primaryLabel: String
    val primaryOnClick: () -> Unit
    // Actions that download an APK must wait for the release lookup to settle
    val primaryNeedsRelease: Boolean
    when {
        !uiState.isFromDb -> {
            primaryLabel = stringResource(R.string.save_and_install); primaryOnClick = viewModel::installAppGh
            primaryNeedsRelease = true
        }
        shouldSave -> {
            primaryLabel = stringResource(R.string.save_changes); primaryOnClick = viewModel::saveToDb
            primaryNeedsRelease = false
        }
        shouldUpdate -> {
            primaryLabel = stringResource(R.string.update); primaryOnClick = viewModel::installAppGh
            primaryNeedsRelease = true
        }
        !isInstalled -> {
            primaryLabel = stringResource(R.string.install); primaryOnClick = viewModel::installAppGh
            primaryNeedsRelease = true
        }
        else -> {
            primaryLabel = stringResource(R.string.open); primaryOnClick = viewModel::openApp
            primaryNeedsRelease = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            AsyncImage(
                model = appIcon ?: uiState.iconUrl,
                placeholder = painterResource(R.drawable.circle_24px),
                error = painterResource(R.drawable.x_circle_24px),
                contentDescription = null,
                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(24.dp))
            )
            Column {
                Text(
                    uiState.name,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    uiState.owner,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(stringResource(R.string.stars_count, uiState.stars), style = MaterialTheme.typography.bodyMedium)
                if (uiState.version.isNotEmpty()) {
                    Text(stringResource(R.string.version_label, uiState.version), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Text(
            text = uiState.description ?: stringResource(R.string.no_description),
            style = MaterialTheme.typography.bodyMedium
        )

        DetailActionArea(
            label = primaryLabel,
            enabled = !(primaryNeedsRelease && isCheckingUpdate),
            isInstalling = isInstalling,
            progress = installProgress,
            onClick = primaryOnClick
        )
        DetailSecondaryActions(
            showUninstall = isInstalled,
            showDelete = uiState.isFromDb,
            onUninstall = viewModel::uninstallApp,
            onDelete = { showDeleteDialog = true }
        )

        DetailAutoupdateRow(uiState.autoupdate, viewModel::onAutoUpdateChange)

        DetailSectionLabel(stringResource(R.string.release_filters))
        DetailSwitchRow(
            title = stringResource(R.string.prereleases),
            description = stringResource(R.string.prereleases_description),
            checked = uiState.usePrereleases,
            onCheckedChange = viewModel::onUsePrereleasesChange
        )
        DetailFilterField(uiState.releasesInclude, viewModel::onReleasesFilterIncludeChange,
            stringResource(R.string.release_must_contain))
        DetailFilterField(uiState.releasesExclude, viewModel::onReleasesFilterExcludeChange,
            stringResource(R.string.release_must_not_contain))

        DetailSectionLabel(stringResource(R.string.apk_filters))
        DetailFilterField(uiState.filterInclude, viewModel::onFilterIncludeChange,
            stringResource(R.string.apk_must_contain))
        DetailFilterField(uiState.filterExclude, viewModel::onFilterExcludeChange,
            stringResource(R.string.apk_must_not_contain))
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
