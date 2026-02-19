package dev.re7gog.b_sideloader.ui.features.add_app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.re7gog.b_sideloader.R

@Composable
fun AddAppScreen(
    onSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddAppViewModel = hiltViewModel()
) {
    Scaffold(
        topBar = { AddAppTopBar() },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues)
        ) {
            OutlinedTextField(
                value = viewModel.name,
                onValueChange = { viewModel.name = it },
                label = { Text("App name") }
            )
            Button(
                onClick = { viewModel.addApp(onSuccess) },
                enabled = viewModel.canAdd
            ) {
                Text("Add app")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAppTopBar(
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = { Text(stringResource(R.string.add)) },
        modifier = modifier
    )
}