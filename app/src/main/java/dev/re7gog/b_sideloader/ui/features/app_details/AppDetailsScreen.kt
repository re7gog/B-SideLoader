package dev.re7gog.b_sideloader.ui.features.app_details

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import org.drinkless.tdlib.TdApi

@Composable
fun AppDetailsScreen(
    modifier: Modifier = Modifier,
    viewModel: AppDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tgUiState by viewModel.tgUiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier
    ) { paddingValues ->
        if (tgUiState != null) {
            AppDetailsTgContent(
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
            )
            viewModel.loadApkMessages(tgUiState!!.chatId, tgUiState!!.topicId)
        } else if (uiState != null) {
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
fun AppDetailsTgContent(
    viewModel: AppDetailsViewModel,
    modifier: Modifier = Modifier
) {
    val filteredMessages by viewModel.filteredApkMessages.collectAsState()
    val targetApk by viewModel.targetApkMessage.collectAsState()
    val incText by viewModel.includeFilter.collectAsState()
    val excText by viewModel.excludeFilter.collectAsState()

    LazyColumn(modifier = modifier) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()

            Text("Autoselect filters", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp))

            OutlinedTextField(
                value = incText,
                onValueChange = viewModel::onIncludeFilterChange,
                label = { Text("Contains (use space to divide)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = excText,
                onValueChange = viewModel::onExcludeFilterChange,
                label = { Text("Excludes (use space to divide)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.startInstall(targetApk ?: return@Button) },
                enabled = targetApk != null,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                val fileName = (targetApk?.content as? TdApi.MessageDocument)?.document?.fileName
                Text(if (fileName != null) "Install $fileName" else "APK file is not selected")
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
        item {
            Text("Available APKs", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 12.dp))
        }

        items(filteredMessages, key = { it.id }) { message ->
            val isTarget = message.id == targetApk?.id
            ApkMessageStaticRow(message, isTarget)
        }
    }
}

@Composable
fun ApkMessageStaticRow(message: TdApi.Message, isTarget: Boolean) {
    val doc = (message.content as TdApi.MessageDocument).document
    val caption = (message.content as TdApi.MessageDocument).caption.text

    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isTarget) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = if (isTarget) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(doc.fileName, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (caption.isNotEmpty()) {
                Text(caption, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("${doc.document.size / 1024 / 1024} MB", style = MaterialTheme.typography.bodySmall)
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
                text = "Load: ${state.installProgress ?: 0}%",
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