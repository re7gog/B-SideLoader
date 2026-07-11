package dev.re7gog.b_sideloader.ui.features.settings

import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.data.installer.InstallerMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onTgLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val installerMode by viewModel.installerMode.collectAsStateWithLifecycle()
    val useAutoupdates by viewModel.useAutoupdates.collectAsStateWithLifecycle()
    val useMobileData by viewModel.useMobileData.collectAsStateWithLifecycle()
    val useForegroundService by viewModel.useForegroundService.collectAsStateWithLifecycle()
    val useDynamicColor by viewModel.useDynamicColor.collectAsStateWithLifecycle()
    val tgAccount by viewModel.tgAccount.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.toastEvents.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = { SettingsTopBar() },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 24.dp,
                start = 16.dp,
                end = 16.dp
            )
        ) {
            item { SettingsSectionHeader(stringResource(R.string.settings_general)) }
            item {
                SettingsGroup {
                    InstallerModeSetting(
                        selectedMode = installerMode,
                        onModeSelected = { viewModel.selectInstallerMode(it) }
                    )
                    SettingsSwitchItem(
                        title = stringResource(R.string.settings_autoupdates),
                        subtitle = stringResource(R.string.settings_autoupdates_subtitle),
                        checked = useAutoupdates,
                        onCheckedChange = { viewModel.switchAutoupdates(it) }
                    )
                    if (useAutoupdates) {
                        SettingsSwitchItem(
                            title = stringResource(R.string.settings_metered),
                            subtitle = stringResource(R.string.settings_metered_subtitle),
                            checked = useMobileData,
                            onCheckedChange = { viewModel.switchMobileData(it) }
                        )
                        SettingsSwitchItem(
                            title = stringResource(R.string.settings_persistent_service),
                            subtitle = stringResource(R.string.settings_persistent_service_subtitle),
                            checked = useForegroundService,
                            onCheckedChange = { viewModel.switchForegroundService(it) }
                        )
                    }
                    SettingsClickableItem(
                        title = stringResource(R.string.settings_allow_background),
                        subtitle = stringResource(R.string.settings_allow_background_subtitle),
                        onClick = viewModel::allowBackground
                    )
                }
            }

            item { SettingsSectionHeader(stringResource(R.string.settings_appearance)) }
            item {
                SettingsGroup {
                    LanguageSetting()
                    SettingsSwitchItem(
                        title = stringResource(R.string.settings_dynamic_color),
                        subtitle = stringResource(R.string.settings_dynamic_color_subtitle),
                        checked = useDynamicColor,
                        onCheckedChange = { viewModel.switchDynamicColor(it) }
                    )
                }
            }

            item { SettingsSectionHeader(stringResource(R.string.settings_login_accounts)) }
            item {
                SettingsGroup {
                    TelegramAccountSetting(
                        account = tgAccount,
                        onLoginClick = onTgLoginClick,
                        onLogoutClick = { viewModel.logoutTelegram() }
                    )
                    GithubTokenSetting()
                }
            }
        }
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

/** Section label shown above a [SettingsGroup]. */
@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}

/** Rounded container that visually groups related settings rows. */
@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(content = content)
    }
}

@Composable
fun SettingsSwitchItem(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        supportingContent = if (subtitle != null) {
            { Text(subtitle) }
        } else null,
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onCheckedChange(!checked) }
    ) { Text(title) }
}

@Composable
fun SettingsClickableItem(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    ListItem(
        supportingContent = if (subtitle != null) {
            { Text(subtitle) }
        } else null,
        trailingContent = {
            Icon(
                painter = painterResource(R.drawable.chevron_right_24px),
                contentDescription = null
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    ) { Text(title) }
}

@Composable
fun TelegramAccountSetting(
    account: TgAccount?,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    if (account == null) {
        SettingsClickableItem(
            title = stringResource(R.string.telegram),
            subtitle = stringResource(R.string.telegram_login_subtitle),
            onClick = onLoginClick
        )
    } else {
        ListItem(
            leadingContent = {
                if (account.avatarPath != null) {
                    AsyncImage(
                        model = "file://${account.avatarPath}",
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.telegram),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            },
            overlineContent = { Text(stringResource(R.string.telegram)) },
            supportingContent = { Text(account.username?.let { "@$it" } ?: stringResource(R.string.telegram_account)) },
            trailingContent = {
                TextButton(onClick = onLogoutClick) { Text(stringResource(R.string.log_out)) }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        ) { Text(account.name) }
    }
}

@Composable
fun InstallerModeSetting(
    selectedMode: InstallerMode,
    onModeSelected: (InstallerMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ListItem(
        supportingContent = { Text(stringResource(selectedMode.displayNameRes)) },
        trailingContent = {
            Icon(
                painter = painterResource(R.drawable.chevron_right_24px),
                contentDescription = null
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                InstallerMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(stringResource(mode.displayNameRes)) },
                        onClick = {
                            expanded = false
                            onModeSelected(mode)
                        }
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { expanded = true }
    ) { Text(stringResource(R.string.settings_installation_method)) }
}

/** Languages the app can be forced to, independent of the system language. */
enum class AppLanguage(val tag: String?, val endonym: String?) {
    // Endonyms are intentionally shown in their own language, so they are not translated.
    SYSTEM(tag = null, endonym = null),
    ENGLISH(tag = "en", endonym = "English"),
    RUSSIAN(tag = "ru", endonym = "Русский")
}

@Composable
private fun AppLanguage.label(): String =
    endonym ?: stringResource(R.string.language_system_default)

/**
 * Per-app language picker backed by AppCompat's per-app locales. Selecting an entry applies the
 * locale immediately (AppCompat recreates the activity), persists it, and — on Android 13+ — also
 * shows up under the system's per-app language settings.
 */
@Composable
fun LanguageSetting() {
    var expanded by remember { mutableStateOf(false) }
    // Recomputed on recomposition; applying a language recreates the activity, so this
    // always reflects the currently active locale.
    val currentLang = AppCompatDelegate.getApplicationLocales().get(0)?.language
    val selected = AppLanguage.entries.firstOrNull { it.tag == currentLang } ?: AppLanguage.SYSTEM

    ListItem(
        supportingContent = { Text(selected.label()) },
        trailingContent = {
            Icon(
                painter = painterResource(R.drawable.chevron_right_24px),
                contentDescription = null
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                AppLanguage.entries.forEach { lang ->
                    DropdownMenuItem(
                        text = { Text(lang.label()) },
                        onClick = {
                            expanded = false
                            val locales = lang.tag?.let { LocaleListCompat.forLanguageTags(it) }
                                ?: LocaleListCompat.getEmptyLocaleList()
                            AppCompatDelegate.setApplicationLocales(locales)
                        }
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { expanded = true }
    ) { Text(stringResource(R.string.settings_language)) }
}

@Composable
fun GithubTokenSetting(viewModel: SettingsViewModel = hiltViewModel()) {
    val token by viewModel.githubToken.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    OutlinedTextField(
        value = token,
        onValueChange = { viewModel.updateGithubToken(it) },
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        label = { Text(stringResource(R.string.settings_github_token_label)) },
        placeholder = { Text(stringResource(R.string.settings_github_token_placeholder)) },
        trailingIcon = {
            IconButton(onClick = {
                scope.launch {
                    val clipEntry = clipboard.getClipEntry()
                    val text = clipEntry?.clipData?.getItemAt(0)?.text?.toString()

                    if (!text.isNullOrBlank()) {
                        viewModel.updateGithubToken(text)
                        Toast.makeText(context, context.getString(R.string.pasted), Toast.LENGTH_SHORT).show()
                    }
                }
            }) {
                Icon(painter = painterResource(R.drawable.content_paste_24px), contentDescription = stringResource(R.string.cd_paste))
            }
        },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true
    )
}
