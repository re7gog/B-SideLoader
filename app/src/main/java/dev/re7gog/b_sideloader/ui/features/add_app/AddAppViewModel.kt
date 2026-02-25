package dev.re7gog.b_sideloader.ui.features.add_app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.re7gog.b_sideloader.data.remote.GithubApi
import dev.re7gog.b_sideloader.data.remote.dto.GithubRepoDto
import dev.re7gog.b_sideloader.domain.repository.AppsRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class AddAppViewModel @Inject constructor(
    private val appsRepository: AppsRepository,
    private val githubApi: GithubApi
) : ViewModel() {
    var searchQuery by mutableStateOf("")
    var isSearchExpanded by mutableStateOf(false)
    var searchResult by mutableStateOf<List<GithubRepoDto>>(emptyList())
    var isLoading by mutableStateOf(false)

    private val _searchTextFlow = MutableStateFlow("")

    init {
        viewModelScope.launch {
            _searchTextFlow
                .debounce(500L) // Wait 0.5 sec
                .filter { it.isNotBlank() }
                .distinctUntilChanged()  // Don't search if not changed
                .collect { query ->
                    performSearch(query)
                }
        }
    }

    private suspend fun performSearch(query: String) {
        isLoading = true
        try {
            val response = githubApi.searchRepositories(query)
            searchResult = response.items
        } catch (e: Exception) {
            // TODO: Handle exceptions
        } finally {
            isLoading = false
        }
    }

    fun onQueryChange(newQuery: String) {
        searchQuery = newQuery
        _searchTextFlow.value = newQuery
    }

    /*
    var name by mutableStateOf("")

    val canAdd: Boolean
        get() = name.isNotBlank()  // May be more difficult conditions in the future

    fun addApp(onSuccess: () -> Unit) {
        // Test
        viewModelScope.launch {
            val app = AppEntity(
                sourceType = "github",
                name = name
            )
            val details = GithubDetailsEntity(
                id = 0,
                url = "https://github.com/$name",
                usePrereleases = false
            )
            val appWithDetails = AppType.GithubApp(app, details)
            appsRepository.addApp(appWithDetails)
            onSuccess()
        }
    }
    */
}