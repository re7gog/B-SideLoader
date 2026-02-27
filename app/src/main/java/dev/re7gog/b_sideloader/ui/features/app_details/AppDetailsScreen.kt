package dev.re7gog.b_sideloader.ui.features.app_details

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
    ) {
        Row{
            AsyncImage(
                model = uiState.iconUrl,
                contentDescription = null
            )
            Column {
                Text(uiState.name)
                Text(uiState.owner)
            }
        }
        Text(uiState.stars.toString() + " stars")
        Text(uiState.description ?: "No description")
        Button(onClick = { saveApp() }) {
            Text("Save app")
        }
    }
}