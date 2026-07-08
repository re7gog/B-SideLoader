package dev.re7gog.b_sideloader.ui.features.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.data.background.openAutostartSettings
import dev.re7gog.b_sideloader.data.background.requestBatteryOptimizationExemption
import dev.re7gog.b_sideloader.data.encrypt.SecureStorage
import dev.re7gog.b_sideloader.data.installer.InstallManager
import dev.re7gog.b_sideloader.data.settings.SettingsManager
import dev.re7gog.b_sideloader.data.installer.ShizukuPermission
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager,
    private val installManager: InstallManager,
    private val secureStorage: SecureStorage
) : ViewModel() {
    val useShizuku = settingsManager.useShizuku.stateIn(
        viewModelScope, SharingStarted.Eagerly, false
    )

    private val _shizukuIcon = MutableStateFlow(R.drawable.terminal_2_24px)
    val shizukuIcon = _shizukuIcon.asStateFlow()

    val useAutoupdates = settingsManager.useAutoupdates.stateIn(
        viewModelScope, SharingStarted.Eagerly, true
    )

    val useMobileData = settingsManager.useMobileData.stateIn(
        viewModelScope, SharingStarted.Eagerly, false
    )

    val useDynamicColor = settingsManager.useDynamicColor.stateIn(
        viewModelScope, SharingStarted.Eagerly, false
    )

    private val _githubToken = MutableStateFlow(secureStorage.getGithubToken() ?: "")
    val githubToken = _githubToken.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>()
    val snackbarEvents = _snackbarEvents.asSharedFlow()

    fun updateShizuku(switch: Boolean = false) {
        viewModelScope.launch {
            if (!switch && !useShizuku.value) return@launch  // Do nothing if checking disabled state
            if (switch && useShizuku.value) {  // If we want to disable Shizuku
                settingsManager.setUseShizuku(false)
                return@launch
            }
            // Do check if we want to enable Shizuku or verify enabled state
            val permission = installManager.checkPrivilegedPermission()
            when (permission) {
                ShizukuPermission.GRANTED_ADB -> {
                    _shizukuIcon.emit(R.drawable.terminal_2_24px)
                    // Enable only when requested, don't touch when checking state
                    if (!useShizuku.value) settingsManager.setUseShizuku(true)
                }
                ShizukuPermission.GRANTED_OWNER -> {
                    _shizukuIcon.emit(R.drawable.supervisor_account_24px)
                    if (!useShizuku.value) settingsManager.setUseShizuku(true)
                }
                ShizukuPermission.GRANTED_ROOT -> {
                    _shizukuIcon.emit(R.drawable.tag_24px)
                    if (!useShizuku.value) settingsManager.setUseShizuku(true)
                }
                ShizukuPermission.DENIED -> {
                    if (useShizuku.value) {
                        settingsManager.setUseShizuku(false)  // Disable when state check failed
                    } else _snackbarEvents.emit("Denied")  // Show notification only when trying to enable
                }
                ShizukuPermission.SERVICES_NOT_FOUND -> {
                    if (useShizuku.value) {
                        settingsManager.setUseShizuku(false)
                    } else _snackbarEvents.emit("Shizuku/Sui/Dhizuku services not found or not installed")
                }
                ShizukuPermission.OLD_SHIZUKU -> {
                    if (useShizuku.value) {
                        settingsManager.setUseShizuku(false)
                    } else _snackbarEvents.emit("Please update Shizuku")
                }
                ShizukuPermission.OLD_ANDROID_WITH_ADB -> {
                    if (useShizuku.value) {
                        settingsManager.setUseShizuku(false)
                    } else _snackbarEvents.emit("Please update system to Android 8.1 or newer," +
                            " or use other installation methods (Sui(Root) or Dhizuku)")
                }
            }
        }
    }

    fun switchAutoupdates(enable: Boolean) {
        viewModelScope.launch {
            settingsManager.setUseAutoupdates(enable)
        }
    }

    fun switchMobileData(enable: Boolean) {
        viewModelScope.launch {
            settingsManager.setUseMobileData(enable)
        }
    }

    fun switchDynamicColor(enable: Boolean) {
        viewModelScope.launch {
            settingsManager.setUseDynamicColor(enable)
        }
    }

    fun updateGithubToken(newToken: String) {
        secureStorage.saveGithubToken(newToken)
        _githubToken.value = newToken
    }

    fun allowBackground() {
        requestBatteryOptimizationExemption(context)
        openAutostartSettings(context)
    }
}