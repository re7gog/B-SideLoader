package dev.re7gog.b_sideloader.ui.features.add_app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.re7gog.b_sideloader.data.remote.GithubApi
import dev.re7gog.b_sideloader.data.remote.dto.GithubRepoDto
import dev.re7gog.b_sideloader.data.telegram.TelegramManager
import dev.re7gog.b_sideloader.domain.repository.AppsRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import kotlin.collections.emptyList

enum class SearchSource {
    GitHub, Telegram
}

@OptIn(FlowPreview::class)
@HiltViewModel
class AddAppViewModel @Inject constructor(
    private val appsRepository: AppsRepository,
    private val githubApi: GithubApi,
    private val telegramManager: TelegramManager
) : ViewModel() {
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    var isSearchExpanded by mutableStateOf(false)
    var searchResult by mutableStateOf<List<GithubRepoDto>>(emptyList())
    var isLoading by mutableStateOf(false)

    private val _searchTextFlow = MutableStateFlow("")

    private val _searchSource = MutableStateFlow(SearchSource.GitHub)
    val searchSource: StateFlow<SearchSource> = _searchSource.asStateFlow()

    val telegramSearchResults = combine(
        telegramManager.chatsFlow,
        searchQuery
    ) { chats, query ->
        chats.filter { chat ->
            val isChannel = chat.type is TdApi.ChatTypeSupergroup
            val matchesQuery = chat.title.contains(query, ignoreCase = true)
            isChannel && matchesQuery
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    /*
    val availableChats = telegramManager.chatsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )
     */

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
        telegramManager.loadChats()
    }

    private suspend fun performSearch(query: String) {
        isLoading = true
        try {
            val response = githubApi.searchRepositories(query = query)
            searchResult = response.items
        } catch (e: Exception) {
            // TODO: Handle exceptions
        } finally {
            isLoading = false
        }
    }

    fun onQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
        if (_searchSource.value == SearchSource.GitHub) _searchTextFlow.value = newQuery
    }

    fun onSourceSelected(source: SearchSource) {
        _searchSource.value = source
    }
}