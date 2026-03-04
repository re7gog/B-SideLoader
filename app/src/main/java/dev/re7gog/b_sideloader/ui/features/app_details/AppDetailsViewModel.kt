package dev.re7gog.b_sideloader.ui.features.app_details

import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.re7gog.b_sideloader.data.local.entities.AppEntity
import dev.re7gog.b_sideloader.data.local.entities.GithubDetailsEntity
import dev.re7gog.b_sideloader.data.remote.GithubApi
import dev.re7gog.b_sideloader.data.remote.dto.GithubReleaseAssetDto
import dev.re7gog.b_sideloader.domain.logic.IInstallManager
import dev.re7gog.b_sideloader.domain.model.AppType
import dev.re7gog.b_sideloader.domain.repository.AppsRepository
import dev.re7gog.b_sideloader.ui.navigation.AppDetailsFromDbRoute
import dev.re7gog.b_sideloader.ui.navigation.AppDetailsFromSearchRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: AppsRepository,
    private val githubApi: GithubApi,
    private val installManager: IInstallManager
) : ViewModel() {
    private val _uiState = MutableStateFlow<AppDetailsUiState?>(null)
    val uiState: StateFlow<AppDetailsUiState?> = _uiState.asStateFlow()

    init {
        val fromDb = runCatching { savedStateHandle.toRoute<AppDetailsFromDbRoute>() }.getOrNull()
        val fromSearch = runCatching { savedStateHandle.toRoute<AppDetailsFromSearchRoute>() }.getOrNull()

        viewModelScope.launch {
            if (fromDb != null) {
                repository.getAppStream(fromDb.appId)
                    .collect { data ->
                        _uiState.value = data?.toUiState(githubApi)
                    }
            } else if (fromSearch != null) {
                _uiState.value = fromSearch.toUiState()
            }
        }
    }

    fun saveToDb() {
        viewModelScope.launch {
            val app = AppEntity(
                sourceType = "github",
                name = _uiState.value?.name ?: ""
            )
            val details = GithubDetailsEntity(
                id = 0,
                fullName = _uiState.value?.fullName ?: "",
                usePrereleases = false
            )
            val appWithDetails = AppType.GithubApp(app, details)
            repository.addApp(appWithDetails)
        }
    }

    fun installApp() {
        viewModelScope.launch {
            val assets = githubApi.getReleases(
                owner = _uiState.value?.owner ?: "", repo = _uiState.value?.name ?: ""
            )[0].assets
            val url = findCurrentAbiApk(assets) ?: return@launch
            try {
                _uiState.update { it?.copy(isInstalling = true, installProgress = 0f) }
                installManager.downloadAndInstall(url) { progress ->
                    _uiState.update { it?.copy(installProgress = progress) }
                }
            } catch (_: Exception) {
                _uiState.update { it?.copy(isInstalling = false, installProgress = null) }
                // TODO: Show error
            }
        }
    }

    private fun findCurrentAbiApk(assets: List<GithubReleaseAssetDto>): String? {
        val deviceAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "" // For example "arm64-v8a"
        val bestMatch = assets.find {
            it.name.contains(deviceAbi, ignoreCase = true) && it.name.endsWith(".apk")
        }
        return (bestMatch ?: assets.find { it.name.endsWith(".apk") })?.browserDownloadUrl
    }
}