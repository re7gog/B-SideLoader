package dev.re7gog.b_sideloader.ui.feature.manualinstall

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.core.coroutines.suspendRunCatching
import dev.re7gog.b_sideloader.data.di.ApplicationScope
import dev.re7gog.b_sideloader.domain.installer.ApkStagingArea
import dev.re7gog.b_sideloader.domain.installer.InstallerGateway
import dev.re7gog.b_sideloader.domain.model.InstallOutcome
import dev.re7gog.b_sideloader.domain.model.InstallProgress
import dev.re7gog.b_sideloader.domain.model.LocalApk
import dev.re7gog.b_sideloader.ui.common.error.toUiText
import dev.re7gog.b_sideloader.ui.common.text.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where the manual install flow currently is. */
@Immutable
sealed interface ManualInstallUiState {
    /** Nothing picked yet — only the tip is showing. */
    data object Idle : ManualInstallUiState

    /** Copying the picked file out of its content URI and reading its manifest. */
    data object Reading : ManualInstallUiState

    /** Parsed; waiting for the user to confirm what the archive really is. */
    data class Confirming(val apk: LocalApk) : ManualInstallUiState

    data class Installing(val apk: LocalApk, val progress: InstallProgress) : ManualInstallUiState
}

/**
 * One-shot installer for an APK the user picked from storage.
 *
 * Deliberately never touches the repository: a manually installed app is not tracked and gets no
 * update checks. It also no longer listens on a global install bus — the install flow it started
 * *is* the source of the result, so a background update finishing mid-dialog cannot be mistaken
 * for this one.
 */
@HiltViewModel
class ManualInstallViewModel @Inject constructor(
    private val stagingArea: ApkStagingArea,
    private val installerGateway: InstallerGateway,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ManualInstallUiState>(ManualInstallUiState.Idle)
    val uiState: StateFlow<ManualInstallUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<UiText>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<UiText> = _messages.asSharedFlow()

    private var installJob: Job? = null

    fun onFileSelected(uri: String) {
        viewModelScope.launch {
            _uiState.value = ManualInstallUiState.Reading
            suspendRunCatching { stagingArea.stage(uri) }
                .onSuccess { _uiState.value = ManualInstallUiState.Confirming(it) }
                .onFailure {
                    _uiState.value = ManualInstallUiState.Idle
                    _messages.tryEmit(it.toUiText())
                }
        }
    }

    fun onCancel() {
        if (_uiState.value is ManualInstallUiState.Installing) return
        reset()
    }

    fun onConfirm() {
        val apk = (_uiState.value as? ManualInstallUiState.Confirming)?.apk ?: return
        // Nothing here ever sets INSTALL_ALLOW_DOWNGRADE, so a downgrade is not "likely to fail",
        // it *will* fail — after streaming the whole archive into a session. Say so now, naming
        // both versions, instead of spending the install and reporting a bare framework conflict.
        if (apk.isDowngrade) {
            _messages.tryEmit(
                UiText.of(
                    R.string.error_install_downgrade_versions,
                    apk.versionName,
                    apk.installedVersionName.orEmpty(),
                )
            )
            return
        }
        installJob = viewModelScope.launch {
            _uiState.value = ManualInstallUiState.Installing(apk, InstallProgress.Preparing)
            installerGateway.installLocal(apk).collect { progress ->
                if (progress !is InstallProgress.Finished) {
                    _uiState.value = ManualInstallUiState.Installing(apk, progress)
                    return@collect
                }
                _messages.tryEmit(
                    when (val outcome = progress.outcome) {
                        is InstallOutcome.Success -> UiText.of(R.string.installed_app, apk.label)
                        is InstallOutcome.Failure -> outcome.error.toUiText()
                    }
                )
                reset()
            }
        }
    }

    private fun reset() {
        _uiState.value = ManualInstallUiState.Idle
        viewModelScope.launch { stagingArea.clear() }
    }

    override fun onCleared() {
        // A committed session may still be streaming the staged file from disk, so it is only
        // dropped when no install is in flight. `viewModelScope` is already cancelled by the time
        // this runs, which is why the cleanup goes to the application-wide scope instead.
        if (_uiState.value is ManualInstallUiState.Installing) return
        installJob?.cancel()
        applicationScope.launch { stagingArea.clear() }
    }
}
