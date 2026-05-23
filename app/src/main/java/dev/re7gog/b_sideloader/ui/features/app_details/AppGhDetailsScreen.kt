package dev.re7gog.b_sideloader.ui.features.app_details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val shouldUpdate by viewModel.shouldUpdate.collectAsStateWithLifecycle()
    val isInstalling by viewModel.isInstalling.collectAsStateWithLifecycle()
    val installProgress by viewModel.installProgress.collectAsStateWithLifecycle()

    if (uiState != null) {
        Scaffold(
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
                onInstallClick = viewModel::installAppGh,
                onSaveClick = viewModel::saveGhToDb,
                shouldUpdate = shouldUpdate,
                isInstalling = isInstalling,
                installProgress = installProgress,
                onIncludeFilterChange = viewModel::onFilterIncludeChange,
                onExcludeFilterChange = viewModel::onFilterExcludeChange,
                onReleaseIncludeFilterChange = viewModel::onReleasesFilterIncludeChange,
                onReleaseExcludeFilterChange = viewModel::onReleasesFilterExcludeChange,
                modifier = Modifier.padding(paddingValues)
            )
        }
    } else {
        CircularProgressIndicator()
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
            Text("Autoupdate", modifier = Modifier.padding(16.dp))
            Switch(
                checked = autoupdateEnabled,
                onCheckedChange = onAutoupdateChange
            )
        }
    )
}

@Composable
fun AppGhDetailsContent(
    uiState: AppGhDetailsUiState,
    onInstallClick: () -> Unit,
    onSaveClick: () -> Unit,
    shouldUpdate: Boolean,
    isInstalling: Boolean,
    installProgress: Float,
    onIncludeFilterChange: (String) -> Unit,
    onExcludeFilterChange: (String) -> Unit,
    onReleaseIncludeFilterChange: (String) -> Unit,
    onReleaseExcludeFilterChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AsyncImage(
                model = uiState.iconUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(16.dp))
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
            onValueChange = onReleaseIncludeFilterChange,
            label = { Text("Release name must contain (use space to divide key words):") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.releasesExclude,
            onValueChange = onReleaseExcludeFilterChange,
            label = { Text("Release name must not contain:") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = uiState.filterInclude,
            onValueChange = onIncludeFilterChange,
            label = { Text("APK file name must contain:") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.filterExclude,
            onValueChange = onExcludeFilterChange,
            label = { Text("APK file name must not contain:") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp)
        )

        GhInstallButton(
            state = uiState,
            onInstallClick = onInstallClick,
            onSaveClick = onSaveClick,
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
    onSaveClick: () -> Unit,
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
            onClick = {
                if (!state.isFromDb) onSaveClick()
                onInstallClick()
            },
            modifier = modifier
        ) {
            Text(
                if (!state.isFromDb){
                    "Save and install"
                } else if (shouldUpdate) {
                    "Update"
                } else {
                    "Install"
                }
            )
        }
    }
}
