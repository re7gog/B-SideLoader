package dev.re7gog.b_sideloader.ui.features.settings

import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.data.installer.InstallerMode
import kotlinx.coroutines.launch

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
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        ) {
            item {
                Text(
                    text = "General",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 0.dp)
                )
            }
            item {
                InstallerModeSetting(
                    selectedMode = installerMode,
                    onModeSelected = { viewModel.selectInstallerMode(it) }
                )
            }
            item {
                SettingsSwitchItem(
                    title = "Enable autoupdates",
                    checked = useAutoupdates,
                    onCheckedChange = { viewModel.switchAutoupdates(it) }
                )
            }
            if (useAutoupdates) {
                item {
                    SettingsSwitchItem(
                        title = "Enable autoupdates over limited data",
                        checked = useMobileData,
                        onCheckedChange = { viewModel.switchMobileData(it) }
                    )
                }
            }
            item {
                Button(
                    onClick = viewModel::allowBackground,
                    modifier = Modifier.padding(16.dp).fillMaxWidth()
                ) {
                    Text("Allow background work")
                }
            }
            item {
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 0.dp)
                )
            }
            item {
                SettingsSwitchItem(
                    title = "Use dynamic color",
                    checked = useDynamicColor,
                    onCheckedChange = { viewModel.switchDynamicColor(it) }
                )
            }
            item {
                Text(
                    text = "Login & Accounts",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 0.dp)
                )
            }
            item {
                GithubTokenSetting()
            }
            item {
                Button(
                    onClick = onTgLoginClick,
                    modifier = Modifier.padding(16.dp).fillMaxWidth()
                ) {
                    Text("Login Telegram")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallerModeSetting(
    selectedMode: InstallerMode,
    onModeSelected: (InstallerMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth().padding(16.dp)
    ) {
        OutlinedTextField(
            value = selectedMode.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Installation method") },
            supportingText = { Text("Session, or a privileged installer (Shizuku/Sui, Dhizuku)") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
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
    }
}

@Composable
fun SettingsSwitchItem(
    title: String,
    subtitle: String? = null,
    @DrawableRes leadingIcon: Int? = null,
    @DrawableRes thumbIcon: Int? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { if (subtitle != null) Text(subtitle) },
        leadingContent = {
            if (leadingIcon != null)
                Icon(painter = painterResource(leadingIcon), contentDescription = null)
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                thumbContent = if (thumbIcon != null && checked) {
                    {
                        Icon(
                            painter = painterResource(thumbIcon),
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    }
                } else null
            )
        },
        modifier = Modifier.clickable { onCheckedChange(!checked) }
    )
}

@Composable
fun GithubTokenSetting(viewModel: SettingsViewModel = hiltViewModel()) {
    val token by viewModel.githubToken.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    ListItem(headlineContent = {
        OutlinedTextField(
            value = token,
            onValueChange = { viewModel.updateGithubToken(it) },
            modifier = Modifier.fillMaxWidth(),
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
    })
}
