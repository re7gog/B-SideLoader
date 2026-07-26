package dev.re7gog.b_sideloader.ui.feature.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.domain.model.BackgroundMode
import dev.re7gog.b_sideloader.domain.model.InstallerMode
import dev.re7gog.b_sideloader.domain.model.TelegramAccount
import dev.re7gog.b_sideloader.domain.model.ThemeMode
import dev.re7gog.b_sideloader.ui.common.component.NavigationRow
import dev.re7gog.b_sideloader.ui.common.component.SectionLabel
import dev.re7gog.b_sideloader.ui.common.component.SettingsGroup
import dev.re7gog.b_sideloader.ui.common.component.SnackbarMessages
import dev.re7gog.b_sideloader.ui.common.component.SwitchRow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onTelegramLoginClick: () -> Unit,
    onBackgroundSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    SnackbarMessages(messages = viewModel.messages, hostState = snackbarHostState)

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
                start = 16.dp,
                end = 16.dp,
            ),
        ) {
            item { SectionLabel(stringResource(R.string.settings_general), Modifier.padding(top = 16.dp)) }
            item {
                SettingsGroup {
                    InstallerModeRow(
                        selected = uiState.settings.installerMode,
                        onSelect = viewModel::selectInstallerMode,
                    )
                    SwitchRow(
                        title = stringResource(R.string.settings_autoupdates),
                        subtitle = stringResource(R.string.settings_autoupdates_subtitle),
                        checked = uiState.settings.autoUpdate,
                        onCheckedChange = viewModel::setAutoUpdate,
                    )
                    if (uiState.settings.autoUpdate) {
                        SwitchRow(
                            title = stringResource(R.string.settings_metered),
                            subtitle = stringResource(R.string.settings_metered_subtitle),
                            checked = uiState.settings.allowMeteredNetwork,
                            onCheckedChange = viewModel::setAllowMeteredNetwork,
                        )
                        SwitchRow(
                            title = stringResource(R.string.settings_persistent_service),
                            subtitle = stringResource(R.string.settings_persistent_service_subtitle),
                            checked = uiState.settings.backgroundMode == BackgroundMode.Persistent,
                            onCheckedChange = { persistent ->
                                viewModel.setBackgroundMode(
                                    if (persistent) BackgroundMode.Persistent else BackgroundMode.Periodic
                                )
                            },
                        )
                    }
                    SwitchRow(
                        title = stringResource(R.string.settings_parallel_checks),
                        subtitle = stringResource(R.string.settings_parallel_checks_subtitle),
                        checked = uiState.settings.parallelUpdateChecks,
                        onCheckedChange = viewModel::setParallelUpdateChecks,
                    )
                    NavigationRow(
                        title = stringResource(R.string.settings_background_reliability),
                        subtitle = stringResource(R.string.settings_background_reliability_subtitle),
                        onClick = onBackgroundSettingsClick,
                    )
                }
            }

            item { SectionLabel(stringResource(R.string.settings_appearance), Modifier.padding(top = 16.dp)) }
            item {
                SettingsGroup {
                    ThemeModeRow(
                        selected = uiState.settings.themeMode,
                        onSelect = viewModel::setThemeMode,
                    )
                    LanguageRow()
                    SwitchRow(
                        title = stringResource(R.string.settings_dynamic_color),
                        subtitle = stringResource(R.string.settings_dynamic_color_subtitle),
                        checked = uiState.settings.useDynamicColor,
                        onCheckedChange = viewModel::setDynamicColor,
                    )
                }
            }

            item { SectionLabel(stringResource(R.string.settings_login_accounts), Modifier.padding(top = 16.dp)) }
            item {
                SettingsGroup {
                    TelegramAccountRow(
                        account = uiState.telegramAccount,
                        onLoginClick = onTelegramLoginClick,
                        onLogoutClick = viewModel::signOutOfTelegram,
                    )
                    GithubTokenRow(
                        token = uiState.githubToken,
                        onTokenChange = viewModel::updateGithubToken,
                    )
                }
            }
        }
    }
}

@Composable
private fun InstallerModeRow(
    selected: InstallerMode,
    onSelect: (InstallerMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ListItem(
        supportingContent = { Text(stringResource(selected.labelRes)) },
        trailingContent = {
            Icon(painterResource(R.drawable.chevron_right_24px), contentDescription = null)
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                InstallerMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(stringResource(mode.labelRes)) },
                        onClick = {
                            expanded = false
                            onSelect(mode)
                        },
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { expanded = true },
    ) { Text(stringResource(R.string.settings_installation_method)) }
}

/** Light / dark / follow-the-system, in the same dropdown shape as the installer picker. */
@Composable
private fun ThemeModeRow(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ListItem(
        supportingContent = { Text(stringResource(selected.labelRes)) },
        trailingContent = {
            Icon(painterResource(R.drawable.chevron_right_24px), contentDescription = null)
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ThemeMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(stringResource(mode.labelRes)) },
                        onClick = {
                            expanded = false
                            onSelect(mode)
                        },
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { expanded = true },
    ) { Text(stringResource(R.string.settings_theme)) }
}

@Composable
private fun TelegramAccountRow(
    account: TelegramAccount?,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
) {
    if (account == null) {
        NavigationRow(
            title = stringResource(R.string.telegram),
            subtitle = stringResource(R.string.telegram_login_subtitle),
            onClick = onLoginClick,
        )
        return
    }
    ListItem(
        leadingContent = {
            if (account.avatarPath != null) {
                AsyncImage(
                    model = "file://${account.avatarPath}",
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                )
            } else {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.telegram),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        },
        overlineContent = { Text(stringResource(R.string.telegram)) },
        supportingContent = {
            Text(account.username?.let { "@$it" } ?: stringResource(R.string.telegram_account))
        },
        trailingContent = {
            TextButton(onClick = onLogoutClick) { Text(stringResource(R.string.log_out)) }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    ) { Text(account.displayName) }
}

@Composable
private fun GithubTokenRow(
    token: String,
    onTokenChange: (String) -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    OutlinedTextField(
        value = token,
        onValueChange = onTokenChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        label = { Text(stringResource(R.string.settings_github_token_label)) },
        placeholder = { Text(stringResource(R.string.settings_github_token_placeholder)) },
        trailingIcon = {
            IconButton(
                onClick = {
                    scope.launch {
                        val pasted = clipboard.getClipEntry()
                            ?.clipData
                            ?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)
                            ?.text
                            ?.toString()
                        if (!pasted.isNullOrBlank()) onTokenChange(pasted.trim())
                    }
                }
            ) {
                Icon(
                    painter = painterResource(R.drawable.content_paste_24px),
                    contentDescription = stringResource(R.string.cd_paste),
                )
            }
        },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
    )
}

/** Languages the app can be forced to, independent of the system language. */
private enum class AppLanguage(val tag: String?, val endonym: String?) {
    // Endonyms are shown in their own language on purpose, so they are not translated.
    System(tag = null, endonym = null),
    English(tag = "en", endonym = "English"),
    Russian(tag = "ru", endonym = "Русский"),
}

@Composable
private fun AppLanguage.label(): String = endonym ?: stringResource(R.string.language_system_default)

/**
 * Per-app language picker backed by AppCompat's per-app locales. Selecting an entry applies it
 * immediately (AppCompat recreates the activity), persists it, and on Android 13+ also surfaces
 * under the system's own per-app language settings.
 */
@Composable
private fun LanguageRow() {
    var expanded by remember { mutableStateOf(false) }
    // Recomputed on recomposition; applying a language recreates the activity, so this always
    // reflects the active locale.
    val currentTag = AppCompatDelegate.getApplicationLocales().get(0)?.language
    val selected = AppLanguage.entries.firstOrNull { it.tag == currentTag } ?: AppLanguage.System

    ListItem(
        supportingContent = { Text(selected.label()) },
        trailingContent = {
            Icon(painterResource(R.drawable.chevron_right_24px), contentDescription = null)
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                AppLanguage.entries.forEach { language ->
                    DropdownMenuItem(
                        text = { Text(language.label()) },
                        onClick = {
                            expanded = false
                            AppCompatDelegate.setApplicationLocales(
                                language.tag?.let { LocaleListCompat.forLanguageTags(it) }
                                    ?: LocaleListCompat.getEmptyLocaleList()
                            )
                        },
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { expanded = true },
    ) { Text(stringResource(R.string.settings_language)) }
}

/** Display name for an installer mode. Kept in the UI layer; the domain enum stays resource-free. */
private val InstallerMode.labelRes: Int
    get() = when (this) {
        InstallerMode.Session -> R.string.installer_session
        InstallerMode.Shizuku -> R.string.installer_shizuku
        InstallerMode.Dhizuku -> R.string.installer_dhizuku
    }

private val ThemeMode.labelRes: Int
    get() = when (this) {
        ThemeMode.System -> R.string.theme_system
        ThemeMode.Light -> R.string.theme_light
        ThemeMode.Dark -> R.string.theme_dark
    }
