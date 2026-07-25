package dev.re7gog.b_sideloader.ui.feature.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.re7gog.b_sideloader.core.coroutines.suspendRunCatching
import dev.re7gog.b_sideloader.core.log.Logger
import dev.re7gog.b_sideloader.domain.model.GithubRepoSummary
import dev.re7gog.b_sideloader.domain.model.TelegramChatSummary
import dev.re7gog.b_sideloader.domain.model.TelegramTopicSummary
import dev.re7gog.b_sideloader.domain.repository.GithubRepository
import dev.re7gog.b_sideloader.domain.repository.TelegramRepository
import dev.re7gog.b_sideloader.ui.common.error.toUiText
import dev.re7gog.b_sideloader.ui.common.text.UiText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/** What the search screen renders. */
@Immutable
data class SearchUiState(
    val query: String = "",
    val source: SearchSource = SearchSource.GitHub,
    val isLoading: Boolean = false,
    val githubResults: ImmutableList<GithubRepoSummary> = persistentListOf(),
    val telegramChats: ImmutableList<TelegramChatSummary> = persistentListOf(),
    /** Non-null while drilled into a forum channel's topic list. */
    val topicsOf: TelegramChatSummary? = null,
    val topics: ImmutableList<TelegramTopicSummary> = persistentListOf(),
) {
    val inTopicList: Boolean get() = topicsOf != null

    val hasResults: Boolean
        get() = when (source) {
            SearchSource.GitHub -> githubResults.isNotEmpty()
            SearchSource.Telegram -> telegramChats.isNotEmpty()
            SearchSource.LocalFile -> true
        }
}

/**
 * Search across every source.
 *
 * Both sources go through one debounced query flow instead of the two independent pipelines the
 * old code ran (one imperative `collect`, one reactive `mapLatest`), which is why switching source
 * needed a manual re-search. Here the source is part of the key, so a switch re-queries by itself
 * and an in-flight request for the previous source is cancelled by `flatMapLatest`.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val githubRepository: GithubRepository,
    private val telegramRepository: TelegramRepository,
    private val logger: Logger,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val source = MutableStateFlow(SearchSource.GitHub)
    private val loading = MutableStateFlow(false)
    private val topicList = MutableStateFlow<TopicListState?>(null)

    private val _messages = MutableSharedFlow<UiText>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<UiText> = _messages.asSharedFlow()

    private val results: StateFlow<SearchResults> =
        combine(query, source) { text, src -> text to src }
            .debounce(DEBOUNCE_MS.milliseconds)
            .distinctUntilChanged()
            .flatMapLatest { (text, src) -> searchFlow(text, src) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_MS), SearchResults())

    val uiState: StateFlow<SearchUiState> = combine(
        query, source, loading, results, topicList,
    ) { text, src, isLoading, found, topics ->
        SearchUiState(
            query = text,
            source = src,
            isLoading = isLoading,
            githubResults = found.github,
            telegramChats = found.telegram,
            topicsOf = topics?.chat,
            topics = topics?.topics ?: persistentListOf(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_MS), SearchUiState())

    /**
     * One request per (query, source). Emits an empty result immediately so a cleared field drops
     * stale rows without waiting for the debounce.
     */
    private fun searchFlow(text: String, src: SearchSource) = flow {
        if (text.isBlank() || !src.isSearchable) {
            loading.value = false
            emit(SearchResults())
            return@flow
        }
        loading.value = true
        try {
            val found = when (src) {
                SearchSource.GitHub -> SearchResults(
                    github = githubRepository.searchRepositories(text).toImmutableList(),
                )

                SearchSource.Telegram -> SearchResults(
                    telegram = telegramRepository.searchChats(text).toImmutableList(),
                )

                SearchSource.LocalFile -> SearchResults()
            }
            emit(found)
        } catch (e: Throwable) {
            // flatMapLatest cancels this flow on a new query; that must not surface as an error.
            if (e is kotlinx.coroutines.CancellationException) throw e
            logger.w(TAG, e) { "Search failed for '$text' on $src" }
            _messages.tryEmit(e.toUiText())
            emit(SearchResults())
        } finally {
            loading.value = false
        }
    }

    fun onQueryChange(newQuery: String) {
        query.value = newQuery
        if (newQuery.isBlank()) loading.value = false
    }

    fun clearQuery() = onQueryChange("")

    fun onSourceSelected(newSource: SearchSource) {
        if (source.value == newSource) return
        source.value = newSource
        topicList.value = null
        if (!newSource.isSearchable) loading.value = false
    }

    /** A forum channel drills into its topics; a plain channel goes straight to details. */
    fun onChatSelected(chat: TelegramChatSummary, onReady: (chatId: Long, topicId: Int, title: String) -> Unit) {
        viewModelScope.launch {
            val resolved = suspendRunCatching { telegramRepository.getChat(chat.id) }
                .onFailure { _messages.tryEmit(it.toUiText()) }
                .getOrNull()
                ?: chat

            if (!resolved.isForum) {
                onReady(resolved.id, NO_TOPIC, resolved.title)
                return@launch
            }
            topicList.value = TopicListState(resolved, persistentListOf())
            val topics = suspendRunCatching { telegramRepository.getTopics(resolved.id) }
                .onFailure { _messages.tryEmit(it.toUiText()) }
                .getOrDefault(emptyList())
            topicList.update { it?.copy(topics = topics.toImmutableList()) }
        }
    }

    fun onBackToChats() {
        topicList.value = null
    }

    /** Chat/topic avatars are fetched lazily by the row that shows them. */
    suspend fun downloadPhoto(fileId: Int): String? =
        suspendRunCatching { telegramRepository.downloadPhoto(fileId) }.getOrNull()

    private data class SearchResults(
        val github: ImmutableList<GithubRepoSummary> = persistentListOf(),
        val telegram: ImmutableList<TelegramChatSummary> = persistentListOf(),
    )

    private data class TopicListState(
        val chat: TelegramChatSummary,
        val topics: ImmutableList<TelegramTopicSummary>,
    )

    private companion object {
        const val TAG = "Search"
        const val DEBOUNCE_MS = 400L
        const val SUBSCRIPTION_MS = 5_000L
        const val NO_TOPIC = 0
    }
}
