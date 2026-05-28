package dev.re7gog.b_sideloader.ui.features.app_details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.re7gog.b_sideloader.R

@Composable
fun AppGhDetailsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppGhDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    if (uiState != null) {
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                AppGhDetailsTopBar(
                    onBackClick = onBackClick,
                    onAutoupdateChange = viewModel::onAutoUpdateChange,
                    autoupdateEnabled = uiState!!.autoupdate
                )
            },
            modifier = modifier
        ) { paddingValues ->
            AppGhDetailsContent(
                uiState = uiState!!,
                viewModel = viewModel,
                modifier = Modifier.padding(paddingValues)
            )
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppGhDetailsTopBar(
    onBackClick: () -> Unit,
    onAutoupdateChange: (Boolean) -> Unit,
    autoupdateEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        modifier = modifier,
        title = { },
        navigationIcon = {
            IconButton(onClick = { onBackClick() }) {
                Icon(
                    painterResource(R.drawable.arrow_back_24px),
                    "Back"
                )
            }
        },
        actions = {
            Text("Autoupdate", modifier = Modifier.padding(8.dp))
            Switch(
                checked = autoupdateEnabled,
                onCheckedChange = onAutoupdateChange,
                modifier = Modifier.padding(8.dp)
            )
        }
    )
}

@Composable
fun AppGhDetailsContent(
    uiState: AppGhDetailsUiState,
    viewModel: AppGhDetailsViewModel,
    modifier: Modifier = Modifier
) {
    val shouldUpdate by viewModel.shouldUpdate.collectAsStateWithLifecycle()
    val isInstalling by viewModel.isInstalling.collectAsStateWithLifecycle()
    val installProgress by viewModel.installProgress.collectAsStateWithLifecycle()

    val isAppInstalled = uiState.installed
    var imageModifier = Modifier.size(120.dp).clip(RoundedCornerShape(16.dp))
    if (!isAppInstalled) imageModifier = imageModifier.alpha(0.5f)
    val packageName = uiState.packageName
    val appIcon = remember(isAppInstalled, packageName) {
        if (isAppInstalled && packageName != "") viewModel.getAppIcon(uiState.packageName) else null
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AsyncImage(
                model = appIcon ?: uiState.iconUrl,
                placeholder = painterResource(R.drawable.circle_24px),
                error = painterResource(R.drawable.x_circle_24px),
                contentDescription = null,
                modifier = imageModifier
            )
            Column {
                Text(uiState.name, style = MaterialTheme.typography.headlineMedium)
                Text(uiState.owner, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = uiState.stars.toString() + " ⭐",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (uiState.version.isNotEmpty()) {
                    Text(
                        text = "Version: " + uiState.version,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        Text(
            text = uiState.description ?: "No description",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        OutlinedTextField(
            value = uiState.releasesInclude,
            onValueChange = viewModel::onReleasesFilterIncludeChange,
            label = { Text("Release name must contain (use space to divide key words):") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.releasesExclude,
            onValueChange = viewModel::onReleasesFilterExcludeChange,
            label = { Text("Release name must not contain:") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = uiState.filterInclude,
            onValueChange = viewModel::onFilterIncludeChange,
            label = { Text("APK file name must contain:") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.filterExclude,
            onValueChange = viewModel::onFilterExcludeChange,
            label = { Text("APK file name must not contain:") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp)
        )

        GhInstallButton(
            state = uiState,
            onInstallClick = viewModel::installAppGh,
            shouldUpdate = shouldUpdate,
            isInstalling = isInstalling,
            installProgress = installProgress,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        )
    }
}

@Composable
fun GhInstallButton(
    state: AppGhDetailsUiState,
    onInstallClick: () -> Unit,
    shouldUpdate: Boolean,
    isInstalling: Boolean,
    installProgress: Float,
    modifier: Modifier = Modifier
) {
    if (isInstalling) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier
        ) {
            LinearProgressIndicator(
                progress = { installProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Text(
                text = "Load: ${(installProgress * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall
            )
        }
    } else{
        Button(
            onClick = onInstallClick,
            modifier = modifier
        ) {
            Text(
                if (!state.isFromDb){
                    "Save and install"
                } else if (shouldUpdate) {
                    "Update"
                } else if (state.installed){
                    "Open"
                } else {
                    "Install"
                }
            )
        }
    }
}
