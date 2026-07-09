package dev.re7gog.b_sideloader.ui.features.search_app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.re7gog.b_sideloader.data.encrypt.SecureStorage
import dev.re7gog.b_sideloader.data.remote.GithubApi
import dev.re7gog.b_sideloader.data.remote.dto.GithubRepoDto
import dev.re7gog.b_sideloader.data.telegram.TelegramManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

enum class SearchSource {
    GitHub, Telegram
}

sealed class SelectionState {
    object ChatList : SelectionState()
    data class TopicList(val chatId: Long, val chatTitle: String) : SelectionState()
    data class MessageList(val chatId: Long, val chatTitle: String, val topicId: Int?) : SelectionState()
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchAppViewModel @Inject constructor(
    private val githubApi: GithubApi,
    private val telegramManager: TelegramManager,
    private val secureStorage: SecureStorage
) : ViewModel() {
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _ghSearchResult = MutableStateFlow<List<GithubRepoDto>>(emptyList())
    val ghSearchResult = _ghSearchResult.asStateFlow()

    // GitHub-search loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    // Telegram chat-search loading state, kept separate so both sources can share the
    // same loading UI in the redesigned screen
    private val _tgIsLoading = MutableStateFlow(false)
    val tgIsLoading = _tgIsLoading.asStateFlow()

    private val _searchSource = MutableStateFlow(SearchSource.GitHub)
    val searchSource = _searchSource.asStateFlow()

    // Server-side chat search, limited to channels/supergroups. mapLatest cancels an
    // in-flight request when the query changes, so results always match the latest input.
    val tgSearchResults: StateFlow<List<TdApi.Chat>> =
        combine(_searchQuery, _searchSource) { query, source -> query to source }
            .debounce(300L.milliseconds)
            .distinctUntilChanged()
            .mapLatest { (query, source) ->
                if (source != SearchSource.Telegram || query.isBlank()) {
                    _tgIsLoading.value = false
                    emptyList()
                } else {
                    _tgIsLoading.value = true
                    try {
                        telegramManager.searchChatsOnServer(query)
                            .filter { it.type is TdApi.ChatTypeSupergroup }
                    } finally {
                        _tgIsLoading.value = false
                    }
                }
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                emptyList()
            )

    private val _selectionState = MutableStateFlow<SelectionState>(SelectionState.ChatList)
    val selectionState = _selectionState.asStateFlow()

    private val _topics = MutableStateFlow<List<TdApi.ForumTopic>>(emptyList())
    val topics = _topics.asStateFlow()

    private val _ghToken by lazy { secureStorage.getGithubToken() }

    private val _snackbarEvents = MutableSharedFlow<String>()
    //val snackbarEvents = _snackbarEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            _searchQuery
                .debounce(500L.milliseconds) // Wait 0.5 sec
                .filter { it.isNotBlank() }
                .distinctUntilChanged()  // Don't search if not changed
                .collect { query ->
                    // Telegram results come from the reactive server-search flow above,
                    // so only GitHub needs a per-query network request here
                    if (_searchSource.value == SearchSource.GitHub) {
                        performGhSearch(query)
                    }
                }
        }
    }

    private suspend fun performGhSearch(query: String) {
        _isLoading.value = true
        try {
            val response = githubApi.searchRepositories(query = query, token = _ghToken)
            _ghSearchResult.value = response.items
        } catch (e: Exception) {
            _snackbarEvents.emit(e.message ?: "Search request error")
        } finally {
            _isLoading.value = false
        }
    }

    fun onQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
        if (newQuery.isBlank()) {
            // Clearing the field should immediately drop stale results / spinners so the
            // empty state shows for whichever source is active
            _ghSearchResult.value = emptyList()
            _isLoading.value = false
            _tgIsLoading.value = false
        } else if (_searchSource.value == SearchSource.Telegram) {
            // Give Telegram instant loading feedback ahead of the debounced server search
            _tgIsLoading.value = true
        }
    }

    fun clearQuery() {
        onQueryChange("")
    }

    fun onSourceSelected(source: SearchSource) {
        if (_searchSource.value == source) return
        _searchSource.value = source
        // The reactive query collector only fires on query changes, so switching back to
        // GitHub with an existing query needs an explicit re-search. Telegram re-searches
        // automatically because its results flow also keys off the source.
        val query = _searchQuery.value
        if (source == SearchSource.GitHub && query.isNotBlank()) {
            viewModelScope.launch { performGhSearch(query) }
        }
    }

    /** Downloads a chat photo/avatar and returns its local path (null if none). */
    suspend fun downloadPhoto(fileId: Int): String? = telegramManager.downloadPhoto(fileId)

    fun onChatSelected(chat: TdApi.Chat) {
        viewModelScope.launch {
            val isChatForum = telegramManager.isForum(chat.id)

            if (isChatForum) {
                _selectionState.value = SelectionState.TopicList(chat.id, chat.title)
                val topicsRes = telegramManager.getForumTopics(chat.id)
                _topics.value = topicsRes?.topics?.toList() ?: emptyList()
            } else {
                _selectionState.value = SelectionState.MessageList(chat.id, chat.title, null)
            }
        }
    }

    fun onTopicSelected(topicId: Int? = null) {
        val state = _selectionState.value as SelectionState.TopicList
        _selectionState.value = SelectionState.MessageList(state.chatId, state.chatTitle, topicId)
    }

    fun onBackToChats() {
        _selectionState.value = SelectionState.ChatList
        _topics.value = emptyList()
    }
}