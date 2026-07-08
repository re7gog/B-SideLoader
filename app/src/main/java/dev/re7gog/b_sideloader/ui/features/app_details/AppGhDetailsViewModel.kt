package dev.re7gog.b_sideloader.ui.features.app_details

import android.graphics.drawable.Drawable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.re7gog.b_sideloader.data.encrypt.SecureStorage
import dev.re7gog.b_sideloader.data.installer.InstallEventManager
import dev.re7gog.b_sideloader.data.installer.InstallManager
import dev.re7gog.b_sideloader.data.local.entities.AppEntity
import dev.re7gog.b_sideloader.data.local.entities.GithubDetailsEntity
import dev.re7gog.b_sideloader.data.remote.GithubApi
import dev.re7gog.b_sideloader.data.updater.UpdatesManager
import dev.re7gog.b_sideloader.domain.model.AppType
import dev.re7gog.b_sideloader.domain.repository.AppsRepository
import dev.re7gog.b_sideloader.ui.navigation.AppGhDetailsFromDbRoute
import dev.re7gog.b_sideloader.ui.navigation.AppGhDetailsFromSearchRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppGhDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: AppsRepository,
    private val githubApi: GithubApi,
    private val installManager: InstallManager,
    private val secureStorage: SecureStorage,
    private val updatesManager: UpdatesManager
) : ViewModel() {
    private val _uiState = MutableStateFlow<AppGhDetailsUiState?>(null)
    val uiState: StateFlow<AppGhDetailsUiState?> = _uiState.asStateFlow()

    private val _shouldUpdate = MutableStateFlow(false)
    val shouldUpdate = _shouldUpdate.asStateFlow()

    private val _shouldSave = MutableStateFlow(false)
    val shouldSave = _shouldSave.asStateFlow()

    private val _isInstalling = MutableStateFlow(false)
    val isInstalling = _isInstalling.asStateFlow()

    private val _installProgress = MutableStateFlow(0f)
    val installProgress = _installProgress.asStateFlow()

    // Becomes true once this screen's own install completes successfully; drives
    // where the back button goes (apps list on success vs. search on failure)
    private val _installSucceeded = MutableStateFlow(false)
    val installSucceeded = _installSucceeded.asStateFlow()

    private val installEvents = InstallEventManager.installEvents
    private val uninstallEvents = InstallEventManager.uninstallEvents

    // Install events are a global bus shared by every detail screen, so we only react
    // to one after THIS screen actually started an install (avoids saving an app to the
    // DB just because an unrelated install finished while this page was open).
    private var installRequested = false

    private var downloadUrl = ""
    private var newVersion = ""

    private val _icon = MutableStateFlow<Drawable?>(null)
    val icon = _icon.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>()
    val snackbarEvents = _snackbarEvents.asSharedFlow()

    init {
        val fromDb = runCatching { savedStateHandle.toRoute<AppGhDetailsFromDbRoute>() }.getOrNull()
        val fromSearch = runCatching { savedStateHandle.toRoute<AppGhDetailsFromSearchRoute>() }.getOrNull()

        viewModelScope.launch {
            if (fromDb != null) {
                val app = repository.getAppStream(fromDb.appId).firstOrNull()
                if (app != null && app.githubDetails != null) {
                    _uiState.value = app.toGhUiState(
                        githubApi, secureStorage.getGithubToken(), installed = fromDb.installed
                    )
                }
            } else if (fromSearch != null) {
                _uiState.value = fromSearch.toGhUiState()
            }
            if (_uiState.value?.installed ?: false) _icon.value = installManager.getAppIcon(_uiState.value!!.packageName)
            checkGhUpdate()
        }
        viewModelScope.launch {
            installEvents.collect { installRes ->
                if (!installRequested) return@collect  // Not our install, ignore
                installRequested = false
                if (installRes.succeeded) {
                    _uiState.update {
                        it?.copy(
                            version = newVersion,
                            installed = true,
                            packageName = installRes.packageName ?: it.packageName
                        )
                    }
                    _shouldUpdate.value = false
                    _shouldSave.value = false
                    if (_uiState.value?.isFromDb == true) {
                        repository.updateApp(genGhApp())
                    } else {
                        // First install of a searched app: persist it and turn this
                        // page into a saved, installed one so the button shows "Open"
                        val newId = repository.addApp(genGhApp())
                        _uiState.update { it?.copy(id = newId, isFromDb = true) }
                    }
                    _installSucceeded.value = true
                } else {
                    _snackbarEvents.emit(installRes.errorMessage ?: "Installation error")
                }
            }
        }
        viewModelScope.launch {
            uninstallEvents.collect { uninstallRes ->
                if (uninstallRes.succeeded) {
                    _uiState.update { it?.copy(installed = false) }
                } else {
                    _snackbarEvents.emit(uninstallRes.errorMessage ?: "Uninstall failed")
                }
            }
        }
    }

    private fun genGhApp(): AppType.GithubApp {
        val app = AppEntity(
            id = _uiState.value?.id ?: 0L,
            sourceType = 1,
            packageName = _uiState.value?.packageName ?: "",
            name = _uiState.value?.name ?: "",
            version = _uiState.value?.version ?: "",
            autoupdate = _uiState.value?.autoupdate ?: true,
            filterInclude = _uiState.value?.filterInclude ?: "",
            filterExclude = _uiState.value?.filterExclude ?: "",
        )
        val details = GithubDetailsEntity(
            id = _uiState.value?.id ?: 0L,
            owner = _uiState.value?.owner ?: "",
            repo = _uiState.value?.repo ?: "",
            usePrereleases = _uiState.value?.usePrereleases ?: false,
            releasesInclude = _uiState.value?.releasesInclude ?: "",
            releasesExclude = _uiState.value?.releasesExclude ?: ""
        )
        return AppType.GithubApp(app, details)
    }

    suspend fun checkGhUpdate() {
        val res = updatesManager.checkGhUpdate(genGhApp()) ?: return
        downloadUrl = res.downloadUrl
        val resVer = res.version
        val currVer = _uiState.value!!.version
        if (currVer != resVer) {
            newVersion = resVer
            if (currVer != "") _shouldUpdate.value = true
        }
    }

    fun installAppGh() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isInstalling.value = true
                installRequested = true
                installManager.downloadAndInstall(downloadUrl).collect {
                    _installProgress.value = it
                }
            } catch (e: Exception) {
                installRequested = false  // No install event will arrive on download failure
                _snackbarEvents.emit(e.message ?: "Installation error")
            } finally {
                _installProgress.value = 0f
                _isInstalling.value = false
            }
        }
    }

    fun getAppIcon(packageName: String): Drawable? {
        return installManager.getAppIcon(packageName)
    }

    fun uninstallApp() {
        viewModelScope.launch(Dispatchers.IO) {
            installManager.uninstallPackage(_uiState.value?.packageName ?: return@launch)
        }
    }

    /** Opens the installed app. */
    fun openApp() {
        val pkg = _uiState.value?.packageName
        if (pkg.isNullOrEmpty() || !installManager.openApp(pkg)) {
            viewModelScope.launch { _snackbarEvents.emit("Unable to open the app") }
        }
    }

    /** Persists edited fields to the database. */
    fun saveToDb() {
        viewModelScope.launch {
            repository.updateApp(genGhApp())
            _shouldSave.value = false
        }
    }

    /** Removes the app from the database (does not uninstall it). */
    fun deleteApp(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteApp(genGhApp().app)
            onDeleted()
        }
    }

    fun onAutoUpdateChange(enabled: Boolean) {
        _uiState.update { it?.copy(autoupdate = enabled) }
        if (!_shouldSave.value) _shouldSave.value = true
    }

    fun onFilterIncludeChange(filter: String) {
        _uiState.update { it?.copy(filterInclude = filter) }
        if (!_shouldSave.value) _shouldSave.value = true
    }

    fun onFilterExcludeChange(filter: String) {
        _uiState.update { it?.copy(filterExclude = filter) }
        if (!_shouldSave.value) _shouldSave.value = true
    }

    fun onReleasesFilterIncludeChange(filter: String) {
        _uiState.update { it?.copy(releasesInclude = filter) }
        if (!_shouldSave.value) _shouldSave.value = true
    }

    fun onReleasesFilterExcludeChange(filter: String) {
        _uiState.update { it?.copy(releasesExclude = filter) }
        if (!_shouldSave.value) _shouldSave.value = true
    }
}