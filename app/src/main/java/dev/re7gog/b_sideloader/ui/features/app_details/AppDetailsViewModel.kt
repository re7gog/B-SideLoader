package dev.re7gog.b_sideloader.ui.features.app_details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.re7gog.b_sideloader.data.local.entities.AppEntity
import dev.re7gog.b_sideloader.data.local.entities.GithubDetailsEntity
import dev.re7gog.b_sideloader.data.remote.GithubApi
import dev.re7gog.b_sideloader.domain.model.AppType
import dev.re7gog.b_sideloader.domain.repository.AppsRepository
import dev.re7gog.b_sideloader.ui.navigation.AppDetailsFromDbRoute
import dev.re7gog.b_sideloader.ui.navigation.AppDetailsFromSearchRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: AppsRepository,
    private val githubApi: GithubApi
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
}