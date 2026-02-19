package dev.re7gog.b_sideloader.ui.features.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.re7gog.b_sideloader.R

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    //viewModel: SettingsViewModel = hiltViewModel()
) {
    //val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { SettingsTopBar() },
        modifier = modifier
    ) { paddingValues ->
        Text(
            text = "Settings",
            modifier = Modifier.padding(paddingValues)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTopBar(
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = { Text(stringResource(R.string.settings)) },
        modifier = modifier
    )
}