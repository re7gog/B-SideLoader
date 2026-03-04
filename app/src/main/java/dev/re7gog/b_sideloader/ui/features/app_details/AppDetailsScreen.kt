package dev.re7gog.b_sideloader.ui.features.app_details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage

@Composable
fun AppDetailsScreen(
    modifier: Modifier = Modifier,
    viewModel: AppDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier
    ) { paddingValues ->
        if (uiState != null) {
            AppDetailsContent(
                saveApp = { viewModel.saveToDb() },
                uiState = uiState!!,
                viewModel = viewModel,
                modifier = Modifier.padding(paddingValues)
            )
        } else {
            CircularProgressIndicator()
        }
    }
}

@Composable
fun AppDetailsContent(
    saveApp: () -> Unit,
    uiState: AppDetailsUiState,
    viewModel: AppDetailsViewModel,
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
                modifier = Modifier.size(120.dp).clip(RoundedCornerShape(16.dp))
            )
            Column {
                Text(uiState.name, style = MaterialTheme.typography.headlineMedium)
                Text(uiState.owner, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = uiState.stars.toString() + " ⭐",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Text(
            text = uiState.description ?: "No description",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        InstallButton(
            state = uiState,
            onInstallClick = { viewModel.installApp() }
        )
        Button(
            onClick = { saveApp() },
            modifier = Modifier.padding(16.dp).fillMaxWidth()
        ) {
            Text("Save app")
        }
    }
}

@Composable
fun InstallButton(state: AppDetailsUiState, onInstallClick: () -> Unit) {
    if (state.isInstalling) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LinearProgressIndicator(
                progress = { state.installProgress ?: 0f },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Text(
                text = "Load: ${( (state.installProgress ?: 0f) * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall
            )
        }
    } else {
        Button(
            onClick = onInstallClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Download and install")
        }
    }
}