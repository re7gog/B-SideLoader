package dev.re7gog.b_sideloader.ui.features.app_details

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.re7gog.b_sideloader.R
import org.drinkless.tdlib.TdApi

@Composable
fun AppTgDetailsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppTgDetailsViewModel = hiltViewModel()
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
            topBar = {
                AppTgDetailsTopBar(
                    onBackClick = onBackClick,
                    onAutoupdateChange = viewModel::onAutoUpdateChange,
                    autoupdateEnabled = uiState!!.autoupdate
                )
            },
            modifier = modifier
        ) { paddingValues ->
            AppTgDetailsContent(
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
fun AppTgDetailsTopBar(
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
fun AppTgDetailsContent(
    uiState: AppTgDetailsUiState,
    viewModel: AppTgDetailsViewModel,
    modifier: Modifier = Modifier
) {

    val shouldUpdate by viewModel.shouldUpdate.collectAsStateWithLifecycle()
    val isInstalling by viewModel.isInstalling.collectAsStateWithLifecycle()
    val installProgress by viewModel.installProgress.collectAsStateWithLifecycle()
    val filteredMessages by viewModel.filteredApkMessages.collectAsStateWithLifecycle()
    val targetApkMessage by viewModel.targetApkMessage.collectAsStateWithLifecycle()

    val includeFilter by viewModel.includeFilter.collectAsStateWithLifecycle()
    val excludeFilter by viewModel.excludeFilter.collectAsStateWithLifecycle()
    val msgIncludeFilter by viewModel.msgIncludeFilter.collectAsStateWithLifecycle()
    val msgExcludeFilter by viewModel.msgExcludeFilter.collectAsStateWithLifecycle()

    val isAppInstalled = uiState.installed
    var imageModifier = Modifier.size(120.dp).clip(RoundedCornerShape(16.dp))
    if (!isAppInstalled) imageModifier = imageModifier.alpha(0.5f)
    val packageName = uiState.packageName
    val appIcon = remember(isAppInstalled, packageName) {
        if (isAppInstalled && packageName != "") viewModel.getAppIcon(uiState.packageName) else null
    }

    val targetApkName = remember(targetApkMessage?.id) {
        (targetApkMessage?.content as? TdApi.MessageDocument)?.document?.fileName ?: ""
    }

    Column(modifier = modifier) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AsyncImage(
                model = appIcon,
                placeholder = painterResource(R.drawable.circle_24px),
                error = painterResource(R.drawable.x_circle_24px),
                contentDescription = null,
                modifier = imageModifier
            )
            Column {
                Text(uiState.name, style = MaterialTheme.typography.headlineMedium)
                if (uiState.version.isNotEmpty()) {
                    Text(
                        text = "Version (message id): " + uiState.version,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        OutlinedTextField(
            value = includeFilter,
            onValueChange = viewModel::onIncludeFilterChange,
            label = { Text("Apk file name must contain (use space to divide):") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = excludeFilter,
            onValueChange = viewModel::onExcludeFilterChange,
            label = { Text("Apk file name must not contain:") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = msgIncludeFilter,
            onValueChange = viewModel::onMsgIncludeFilterChange,
            label = { Text("Message text must contain:") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = msgExcludeFilter,
            onValueChange = viewModel::onMsgExcludeFilterChange,
            label = { Text("Message text must not contain:") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        TgInstallButton(
            state = uiState,
            onInstallClick = viewModel::installAppTg,
            shouldUpdate = shouldUpdate,
            isInstalling = isInstalling,
            installProgress = installProgress,
            targetApkName = targetApkName,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        )

        Text(
            "Available APKs",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 12.dp)
        )
        LazyColumn(modifier = modifier) {
            items(filteredMessages, key = { it.id }) { message ->
                val isTarget = message.id == targetApkMessage?.id
                ApkMessageStaticRow(message, isTarget)
            }
        }
    }
}

@Composable
fun TgInstallButton(
    state: AppTgDetailsUiState,
    onInstallClick: () -> Unit,
    shouldUpdate: Boolean,
    isInstalling: Boolean,
    installProgress: Float,
    targetApkName: String?,
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
            enabled = targetApkName != null,
            modifier = modifier
        ) {
            Text(
                if (!state.isFromDb){
                    "Save and install $targetApkName"
                } else if (shouldUpdate) {
                    "Update $targetApkName"
                } else if (state.installed) {
                    "Open"
                } else {
                    "Install $targetApkName"
                }
            )
        }
    }
}

@Composable
fun ApkMessageStaticRow(message: TdApi.Message, isTarget: Boolean) {
    val doc = (message.content as TdApi.MessageDocument).document
    val caption = (message.content as TdApi.MessageDocument).caption.text

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isTarget) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = if (isTarget) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(doc.fileName, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (caption.isNotEmpty()) {
                Text(caption, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Text("${doc.document.size / 1024 / 1024} MB", style = MaterialTheme.typography.bodySmall)
        }
    }
}