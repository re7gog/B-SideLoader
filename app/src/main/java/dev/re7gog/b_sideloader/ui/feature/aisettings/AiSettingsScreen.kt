package dev.re7gog.b_sideloader.ui.feature.aisettings

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.domain.model.AiAvailability
import dev.re7gog.b_sideloader.domain.model.AiMode
import dev.re7gog.b_sideloader.domain.model.AiProvider
import dev.re7gog.b_sideloader.domain.model.AiSettings
import dev.re7gog.b_sideloader.domain.model.AiUnavailableReason
import dev.re7gog.b_sideloader.ui.common.component.LoadingState
import dev.re7gog.b_sideloader.ui.common.component.PasteIconButton
import dev.re7gog.b_sideloader.ui.common.component.SectionLabel
import dev.re7gog.b_sideloader.ui.common.component.SettingsGroup
import dev.re7gog.b_sideloader.ui.common.component.SnackbarMessages

/** Which model the AI features use: none, Gemini Nano on the phone, or a cloud model with a key. */
@Composable
fun AiSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AiSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    SnackbarMessages(messages = viewModel.messages, hostState = snackbarHostState)

    AiSettingsScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onModeChange = viewModel::setMode,
        onProviderChange = viewModel::setProvider,
        onApiKeyChange = viewModel::setApiKey,
        onModelChange = viewModel::setModel,
        onBaseUrlChange = viewModel::setOpenAiBaseUrl,
        onDownloadModel = viewModel::downloadModel,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    uiState: AiSettingsUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onModeChange: (AiMode) -> Unit,
    onProviderChange: (AiProvider) -> Unit,
    onApiKeyChange: (AiProvider, String) -> Unit,
    onModelChange: (AiProvider, String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onDownloadModel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ai_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.arrow_back_24px),
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { padding ->
        if (!uiState.isLoaded) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.ai_settings_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            item { SectionLabel(stringResource(R.string.ai_settings_model)) }
            item {
                SettingsGroup(Modifier.selectableGroup()) {
                    AiMode.entries.forEach { mode ->
                        ModeRow(mode = mode, selected = mode == uiState.mode, onSelect = { onModeChange(mode) })
                    }
                }
            }

            when (uiState.mode) {
                AiMode.Off -> Unit

                AiMode.OnDevice -> item {
                    SettingsGroup {
                        OnDeviceStatusRow(
                            availability = uiState.availability,
                            downloadedBytes = uiState.downloadedBytes,
                            onDownload = onDownloadModel,
                        )
                    }
                }

                AiMode.ApiKey -> {
                    item { SectionLabel(stringResource(R.string.ai_settings_provider)) }
                    item {
                        ProviderSettings(
                            uiState = uiState,
                            onProviderChange = onProviderChange,
                            onApiKeyChange = onApiKeyChange,
                            onModelChange = onModelChange,
                            onBaseUrlChange = onBaseUrlChange,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeRow(mode: AiMode, selected: Boolean, onSelect: () -> Unit) {
    ListItem(
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        supportingContent = { Text(stringResource(mode.descriptionRes)) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
    ) { Text(stringResource(mode.labelRes)) }
}

@Composable
private fun OnDeviceStatusRow(
    availability: AiAvailability?,
    downloadedBytes: Long?,
    onDownload: () -> Unit,
) {
    val context = LocalContext.current
    val status = when {
        downloadedBytes != null -> stringResource(
            R.string.ai_on_device_downloading,
            Formatter.formatShortFileSize(context, downloadedBytes),
        )

        availability == null -> stringResource(R.string.ai_on_device_checking)
        availability is AiAvailability.Available && availability.needsDownload ->
            stringResource(R.string.ai_on_device_downloadable)

        availability is AiAvailability.Available -> stringResource(R.string.ai_on_device_ready)
        else -> stringResource(R.string.ai_on_device_unsupported)
    }
    val canDownload = downloadedBytes == null &&
        availability is AiAvailability.Available && availability.needsDownload
    ListItem(
        supportingContent = { Text(status) },
        trailingContent = if (canDownload) {
            { TextButton(onClick = onDownload) { Text(stringResource(R.string.ai_on_device_download)) } }
        } else {
            null
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    ) { Text(stringResource(R.string.ai_on_device_model)) }
}

@Composable
private fun ProviderSettings(
    uiState: AiSettingsUiState,
    onProviderChange: (AiProvider) -> Unit,
    onApiKeyChange: (AiProvider, String) -> Unit,
    onModelChange: (AiProvider, String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
) {
    val provider = uiState.provider
    val missingKey = (uiState.availability as? AiAvailability.Unavailable)?.reason == AiUnavailableReason.MissingApiKey
    SettingsGroup {
        ProviderRow(selected = provider, onSelect = onProviderChange)
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            OutlinedTextField(
                value = uiState.apiKeys[provider].orEmpty(),
                onValueChange = { onApiKeyChange(provider, it) },
                label = { Text(stringResource(R.string.ai_settings_api_key)) },
                supportingText = if (missingKey) {
                    { Text(stringResource(R.string.ai_settings_api_key_required)) }
                } else {
                    null
                },
                isError = missingKey,
                trailingIcon = { PasteIconButton(onPaste = { onApiKeyChange(provider, it) }) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.models[provider].orEmpty(),
                onValueChange = { onModelChange(provider, it) },
                label = { Text(stringResource(R.string.ai_settings_model_name)) },
                placeholder = { Text(provider.defaultModel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (provider == AiProvider.OpenAi) {
                OutlinedTextField(
                    value = uiState.openAiBaseUrl,
                    onValueChange = onBaseUrlChange,
                    label = { Text(stringResource(R.string.ai_settings_base_url)) },
                    placeholder = { Text(AiSettings.DEFAULT_OPENAI_BASE_URL) },
                    supportingText = { Text(stringResource(R.string.ai_settings_base_url_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            PrivacyNote(stringResource(R.string.ai_settings_privacy, stringResource(provider.labelRes)))
        }
    }
}

@Composable
private fun ProviderRow(selected: AiProvider, onSelect: (AiProvider) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ListItem(
        supportingContent = { Text(stringResource(selected.labelRes)) },
        trailingContent = {
            Icon(painterResource(R.drawable.chevron_right_24px), contentDescription = null)
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                AiProvider.entries.forEach { provider ->
                    DropdownMenuItem(
                        text = { Text(stringResource(provider.labelRes)) },
                        onClick = {
                            expanded = false
                            onSelect(provider)
                        },
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { expanded = true },
    ) { Text(stringResource(R.string.ai_settings_provider)) }
}

@Composable
private fun PrivacyNote(text: String) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 4.dp)) {
        Icon(
            painter = painterResource(R.drawable.info_24px),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@get:StringRes
val AiMode.labelRes: Int
    get() = when (this) {
        AiMode.Off -> R.string.ai_mode_off
        AiMode.OnDevice -> R.string.ai_mode_on_device
        AiMode.ApiKey -> R.string.ai_mode_api_key
    }

@get:StringRes
private val AiMode.descriptionRes: Int
    get() = when (this) {
        AiMode.Off -> R.string.ai_mode_off_description
        AiMode.OnDevice -> R.string.ai_mode_on_device_description
        AiMode.ApiKey -> R.string.ai_mode_api_key_description
    }

@get:StringRes
val AiProvider.labelRes: Int
    get() = when (this) {
        AiProvider.OpenAi -> R.string.ai_provider_openai
        AiProvider.Anthropic -> R.string.ai_provider_anthropic
        AiProvider.Gemini -> R.string.ai_provider_gemini
    }
