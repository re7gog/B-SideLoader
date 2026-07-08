package dev.re7gog.b_sideloader.ui.features.settings

import android.widget.Toast
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
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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
    val useDynamicColor by viewModel.useDynamicColor.collectAsStateWithLifecycle()
    val tgAccount by viewModel.tgAccount.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(Unit) {
        viewModel.toastEvents.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = { SettingsTopBar(scrollBehavior) },
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
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
            item { SettingsSectionHeader("General") }
            item {
                SettingsGroup {
                    InstallerModeSetting(
                        selectedMode = installerMode,
                        onModeSelected = { viewModel.selectInstallerMode(it) }
                    )
                    SettingsSwitchItem(
                        title = "Autoupdates",
                        subtitle = "Check for new versions periodically",
                        checked = useAutoupdates,
                        onCheckedChange = { viewModel.switchAutoupdates(it) }
                    )
                    if (useAutoupdates) {
                        SettingsSwitchItem(
                            title = "Update over limited data",
                            subtitle = "Allow updates on metered networks",
                            checked = useMobileData,
                            onCheckedChange = { viewModel.switchMobileData(it) }
                        )
                    }
                    SettingsClickableItem(
                        title = "Allow background work",
                        subtitle = "Battery and autostart settings",
                        onClick = viewModel::allowBackground
                    )
                }
            }

            item { SettingsSectionHeader("Appearance") }
            item {
                SettingsGroup {
                    SettingsSwitchItem(
                        title = "Dynamic color",
                        subtitle = "Use colors from your wallpaper",
                        checked = useDynamicColor,
                        onCheckedChange = { viewModel.switchDynamicColor(it) }
                    )
                }
            }

            item { SettingsSectionHeader("Login & Accounts") }
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
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier
) {
    LargeTopAppBar(
        title = { Text(stringResource(R.string.settings)) },
        scrollBehavior = scrollBehavior,
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
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) {
            { Text(subtitle) }
        } else null,
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onCheckedChange(!checked) }
    )
}

@Composable
fun SettingsClickableItem(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
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
    )
}

@Composable
fun TelegramAccountSetting(
    account: TgAccount?,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    if (account == null) {
        SettingsClickableItem(
            title = "Telegram",
            subtitle = "Log in to install from channels",
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
            overlineContent = { Text("Telegram") },
            headlineContent = { Text(account.name) },
            supportingContent = { Text(account.username?.let { "@$it" } ?: "Telegram account") },
            trailingContent = {
                TextButton(onClick = onLogoutClick) { Text("Log out") }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@Composable
fun InstallerModeSetting(
    selectedMode: InstallerMode,
    onModeSelected: (InstallerMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text("Installation method") },
        supportingContent = { Text(selectedMode.displayName) },
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
                        text = { Text(mode.displayName) },
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
    )
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
        label = { Text("GitHub token for increasing API limit") },
        placeholder = { Text("github_pat_****************") },
        trailingIcon = {
            IconButton(onClick = {
                scope.launch {
                    val clipEntry = clipboard.getClipEntry()
                    val text = clipEntry?.clipData?.getItemAt(0)?.text?.toString()

                    if (!text.isNullOrBlank()) {
                        viewModel.updateGithubToken(text)
                        Toast.makeText(context, "Pasted!", Toast.LENGTH_SHORT).show()
                    }
                }
            }) {
                Icon(painter = painterResource(R.drawable.content_paste_24px), contentDescription = "Paste")
            }
        },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true
    )
}
