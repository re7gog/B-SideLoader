package dev.re7gog.b_sideloader.ui.features.apps_list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.domain.model.AppWithDetails

@Composable
fun AppsListScreen(
    onAppClick: (id: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppsListViewModel = hiltViewModel()
) {
    val appsState by viewModel.appsState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { AppsListTopBar() },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.padding(paddingValues)
        ) {
            items(
                items = appsState,
                key = { it.app.id }
            ) { appItem ->
                AppItemCard(
                    appItem = appItem,
                    modifier = Modifier.clickable { onAppClick(appItem.app.id) }
                )
            }
        }
    }
}

@Composable
fun AppItemCard(
    appItem: AppWithDetails,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
    ) {
        Row {
            Text(text = appItem.app.name)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsListTopBar(
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        modifier = modifier
    )
}