package dev.re7gog.b_sideloader.ui.features.apps_list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.domain.model.AppWithDetails

@Composable
fun AppsListScreen(
    onGhAppClick: (id: Long, installed: Boolean) -> Unit,
    onTgAppClick: (id: Long, installed: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppsListViewModel = hiltViewModel()
) {
    val appsState by viewModel.appsState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { AppsListTopBar() },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = appsState,
                key = { it.app.id }
            ) { appItem ->
                AppItemCard(
                    appItem = appItem,
                    onGhAppClick = onGhAppClick,
                    onTgAppClick = onTgAppClick,
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
fun AppItemCard(
    appItem: AppWithDetails,
    onGhAppClick: (id: Long, installed: Boolean) -> Unit,
    onTgAppClick: (id: Long, installed: Boolean) -> Unit,
    viewModel: AppsListViewModel,
) {
    val packageName = appItem.app.packageName
    val isAppInstalled = remember(packageName) { viewModel.isPackageInstalled(packageName) }

    var modifier = Modifier.fillMaxWidth().clickable {
        if (appItem.githubDetails != null) {
            onGhAppClick(appItem.app.id, isAppInstalled)
        } else if (appItem.telegramDetails != null) {
            onTgAppClick(appItem.app.id, isAppInstalled)
        }
    }
    if (!isAppInstalled) modifier = modifier.alpha(0.5f)

    val appIcon = remember(isAppInstalled, packageName) {
        if (isAppInstalled) viewModel.getAppIcon(packageName) else null
    }

    val substring = "by ${appItem.githubDetails?.owner ?: ""}"

    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            AsyncImage(
                model = appIcon,
                contentDescription = "Icon of ${appItem.app.name} app",
                modifier = Modifier.size(48.dp),
                placeholder = painterResource(R.drawable.circle_24px),
                error = painterResource(R.drawable.x_circle_24px)
            )
            Spacer(Modifier.padding(8.dp))
            Column {
                Text(text = appItem.app.name, style = MaterialTheme.typography.titleMedium)
                Text(text = substring, style = MaterialTheme.typography.titleMedium)
            }
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