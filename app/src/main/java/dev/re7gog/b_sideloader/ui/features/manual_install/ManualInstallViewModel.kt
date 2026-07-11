package dev.re7gog.b_sideloader.ui.features.manual_install

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.data.installer.ApkStager
import dev.re7gog.b_sideloader.data.installer.InstallEventManager
import dev.re7gog.b_sideloader.data.installer.InstallManager
import dev.re7gog.b_sideloader.data.installer.StagedApk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ManualInstallState {
    /** Nothing picked yet — the tip is showing. */
    data object Idle : ManualInstallState

    /** Copying the picked file out of its content URI and reading its manifest. */
    data object Reading : ManualInstallState

    data class Confirming(val apk: StagedApk) : ManualInstallState

    data class Installing(val apk: StagedApk, val progress: Float) : ManualInstallState
}

/**
 * One-shot installer for an APK the user picked from storage. Deliberately never touches the
 * repository: a manually installed app is not tracked and gets no update checks.
 */
@HiltViewModel
class ManualInstallViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val apkStager: ApkStager,
    private val installManager: InstallManager
) : ViewModel() {
    private val _state = MutableStateFlow<ManualInstallState>(ManualInstallState.Idle)
    val state = _state.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>()
    val snackbarEvents = _snackbarEvents.asSharedFlow()

    // Install results are a global bus, so only react to one this screen actually started
    private var installRequested = false

    init {
        viewModelScope.launch {
            InstallEventManager.installEvents.collect { result ->
                if (!installRequested) return@collect
                installRequested = false
                val apk = (_state.value as? ManualInstallState.Installing)?.apk
                reset()
                _snackbarEvents.emit(
                    when {
                        !result.succeeded -> result.errorMessage ?: context.getString(R.string.installation_error)
                        apk != null -> context.getString(R.string.installed_app, apk.label)
                        else -> context.getString(R.string.installed)
                    }
                )
            }
        }
    }

    fun onFileSelected(uri: Uri) {
        viewModelScope.launch {
            _state.value = ManualInstallState.Reading
            try {
                _state.value = ManualInstallState.Confirming(apkStager.stage(uri))
            } catch (e: Exception) {
                _state.value = ManualInstallState.Idle
                _snackbarEvents.emit(e.message ?: context.getString(R.string.cant_read_apk))
            }
        }
    }

    fun onCancel() {
        if (_state.value is ManualInstallState.Installing) return
        reset()
    }

    fun onConfirm() {
        val apk = (_state.value as? ManualInstallState.Confirming)?.apk ?: return
        viewModelScope.launch {
            _state.value = ManualInstallState.Installing(apk, 0f)
            installRequested = true
            try {
                installManager.installFromFile(apk.file, apk.sizeBytes).collect { progress ->
                    _state.update {
                        if (it is ManualInstallState.Installing) it.copy(progress = progress) else it
                    }
                }
            } catch (e: Exception) {
                installRequested = false  // No install event arrives when the session never commits
                reset()
                _snackbarEvents.emit(e.message ?: context.getString(R.string.installation_error))
            }
        }
    }

    private fun reset() {
        _state.value = ManualInstallState.Idle
        viewModelScope.launch(Dispatchers.IO) { apkStager.clear() }
    }

    override fun onCleared() {
        // A commit already handed the APK to PackageInstaller, but a session still streaming
        // from disk would break if the file vanished under it
        if (_state.value !is ManualInstallState.Installing) apkStager.clear()
    }
}
