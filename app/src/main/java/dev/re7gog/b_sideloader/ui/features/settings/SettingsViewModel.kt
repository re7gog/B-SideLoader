package dev.re7gog.b_sideloader.ui.features.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.re7gog.b_sideloader.data.background.openAutostartSettings
import dev.re7gog.b_sideloader.data.background.requestBatteryOptimizationExemption
import dev.re7gog.b_sideloader.data.encrypt.SecureStorage
import dev.re7gog.b_sideloader.data.installer.InstallManager
import dev.re7gog.b_sideloader.data.installer.InstallerMode
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
    val installerMode = settingsManager.installerMode.stateIn(
        viewModelScope, SharingStarted.Eagerly, InstallerMode.SESSION
    )

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

    private val _toastEvents = MutableSharedFlow<String>()
    val toastEvents = _toastEvents.asSharedFlow()

    /**
     * Applies the chosen installer mode. Session needs no privileges and is set
     * directly; privileged modes are verified first and only stored if their
     * service is present and permission is granted, otherwise a toast explains why.
     */
    fun selectInstallerMode(mode: InstallerMode) {
        viewModelScope.launch {
            if (mode == installerMode.value) return@launch
            if (!mode.isPrivileged) {  // Session, no checks needed
                settingsManager.setInstallerMode(mode)
                return@launch
            }
            when (installManager.checkPrivilegedPermission(mode.useDhizuku)) {
                ShizukuPermission.GRANTED_ADB,
                ShizukuPermission.GRANTED_OWNER,
                ShizukuPermission.GRANTED_ROOT -> {
                    settingsManager.setInstallerMode(mode)
                    _toastEvents.emit("${mode.displayName} installer enabled")
                }
                ShizukuPermission.DENIED ->
                    _toastEvents.emit("${mode.displayName} permission denied")
                ShizukuPermission.SERVICES_NOT_FOUND ->
                    _toastEvents.emit(
                        if (mode.useDhizuku) "Dhizuku not found, not installed or not running"
                        else "Shizuku/Sui not found, not installed or not running"
                    )
                ShizukuPermission.OLD_SHIZUKU ->
                    _toastEvents.emit("Please update Shizuku")
                ShizukuPermission.OLD_ANDROID_WITH_ADB ->
                    _toastEvents.emit(
                        "Android 8.1 or newer is required for Shizuku over ADB," +
                                " use Sui (Root) or Dhizuku instead"
                    )
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