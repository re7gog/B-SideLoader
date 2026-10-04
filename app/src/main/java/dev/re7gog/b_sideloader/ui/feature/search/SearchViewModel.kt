package dev.re7gog.b_sideloader.ui.feature.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.core.coroutines.suspendRunCatching
import dev.re7gog.b_sideloader.core.log.Logger
import dev.re7gog.b_sideloader.domain.error.AppError
import dev.re7gog.b_sideloader.domain.model.GithubRepoLink
import dev.re7gog.b_sideloader.domain.model.GithubRepoSummary
import dev.re7gog.b_sideloader.domain.model.TelegramAuthState
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
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
import kotlinx.coroutines.flow.map
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
    /** The first page of a search is on its way. Later pages report through [paging]. */
    val isLoading: Boolean = false,
    val paging: SearchPaging = SearchPaging.Exhausted,
    val githubResults: ImmutableList<GithubRepoSummary> = persistentListOf(),
    val telegramChats: ImmutableList<TelegramChatSummary> = persistentListOf(),
    /** Signed out of Telegram, so its search cannot run. False while TDLib is still starting. */
    val telegramNeedsLogin: Boolean = false,
    val directLink: DirectLinkState = DirectLinkState(),
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

/** Where a result list stands with the pages after the ones it shows. */
enum class SearchPaging {
    /** There is more; scrolling near the end asks for it. */
    MoreAvailable,

    /** The next page is on its way. */
    Loading,

    /** The next page failed; the list offers a retry instead of asking again by itself. */
    Failed,

    /** Everything the source has for this query is shown. */
    Exhausted,
}

/** The "paste a repository link" field on the empty GitHub page. */
@Immutable
data class DirectLinkState(
    val text: String = "",
    /** The repository is being looked up. */
    val isOpening: Boolean = false,
    /** Why the link could not be opened, shown under the field until it is edited. */
    val error: UiText? = null,
)

/**
 * Search across every source.
 *
 * Both sources go through one debounced query flow instead of the two independent pipelines the
 * old code ran (one imperative `collect`, one reactive `mapLatest`), which is why switching source
 * needed a manual re-search. Here the source is part of the key, so a switch re-queries by itself
 * and an in-flight request for the previous source is cancelled by `flatMapLatest`.
 *
 * Results are paged: the flow for one key emits the first page, then waits for the list to ask
 * for more ([loadMore]) before fetching the next, so a new query also drops whatever paging the
 * old one was doing.
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
    private val directLink = MutableStateFlow(DirectLinkState())

    /** Conflated: asking twice before the next page arrives still fetches one page. */
    private val loadMoreRequests = Channel<Unit>(Channel.CONFLATED)

    private val _messages = MutableSharedFlow<UiText>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<UiText> = _messages.asSharedFlow()

    private val telegramAuth: Flow<TelegramAuthState> = telegramRepository.authState

    private val results: StateFlow<SearchResults> =
        combine(query, source, telegramAuth) { text, src, auth ->
            SearchKey(
                query = text,
                source = src,
                // Searching Telegram signed out only produces an error; wait for the sign-in, which
                // then re-runs the search by changing the key.
                searchable = src.isSearchable &&
                    (src != SearchSource.Telegram || auth is TelegramAuthState.Ready),
            )
        }
            .debounce(DEBOUNCE_MS.milliseconds)
            .distinctUntilChanged()
            .flatMapLatest(::searchFlow)
            // Eagerly, not while subscribed: the screen unsubscribes while a result's details page
            // is open, and restarting would bring the user back to the first page only.
            .stateIn(viewModelScope, SharingStarted.Eagerly, SearchResults())

    val uiState: StateFlow<SearchUiState> = combine(
        query, source, loading, results, topicList,
    ) { text, src, isLoading, found, topics ->
        SearchUiState(
            query = text,
            source = src,
            isLoading = isLoading,
            paging = found.paging,
            githubResults = found.github,
            telegramChats = found.telegram,
            topicsOf = topics?.chat,
            topics = topics?.topics ?: persistentListOf(),
        )
    }
        .combine(telegramAuth.map { it.needsLogin }) { state, needsLogin ->
            state.copy(telegramNeedsLogin = needsLogin)
        }
        .combine(directLink) { state, link -> state.copy(directLink = link) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_MS), SearchUiState())

    /**
     * Every page of one (query, source). Emits an empty result immediately so a cleared field
     * drops stale rows without waiting for the debounce.
     */
    private fun searchFlow(key: SearchKey) = flow {
        if (key.query.isBlank() || !key.searchable) {
            loading.value = false
            emit(SearchResults())
            return@flow
        }
        // A request the previous query's list made is not one for this list.
        loadMoreRequests.tryReceive()

        loading.value = true
        var found = try {
            fetchPage(SearchResults(), key, pageIndex = 0)
        } catch (e: Throwable) {
            // flatMapLatest cancels this flow on a new query; that must not surface as an error.
            if (e is kotlinx.coroutines.CancellationException) throw e
            logger.w(TAG, e) { "Search failed for '${key.query}' on ${key.source}" }
            _messages.tryEmit(e.toUiText())
            SearchResults()
        } finally {
            loading.value = false
        }
        emit(found)

        var pageIndex = 1
        while (found.paging == SearchPaging.MoreAvailable || found.paging == SearchPaging.Failed) {
            loadMoreRequests.receive()
            emit(found.copy(paging = SearchPaging.Loading))
            found = try {
                fetchPage(found, key, pageIndex).also { pageIndex++ }
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                logger.w(TAG, e) { "Page $pageIndex failed for '${key.query}' on ${key.source}" }
                _messages.tryEmit(e.toUiText())
                found.copy(paging = SearchPaging.Failed)
            }
            emit(found)
        }
    }

    /**
     * [shown] plus page [pageIndex] (from 0). Sources reshuffle between requests, so a repository
     * or chat can come back on a later page; it is kept where it first appeared, because the list
     * keys rows by it.
     */
    private suspend fun fetchPage(shown: SearchResults, key: SearchKey, pageIndex: Int): SearchResults =
        when (key.source) {
            SearchSource.GitHub -> {
                val page = githubRepository.searchRepositories(key.query, page = pageIndex + 1)
                SearchResults(
                    github = (shown.github + page.items).distinctBy { it.slug }.toImmutableList(),
                    paging = page.hasMore.toPaging(),
                )
            }

            SearchSource.Telegram -> {
                val page = telegramRepository.searchChats(
                    key.query,
                    offset = pageIndex * TELEGRAM_PAGE_SIZE,
                    limit = TELEGRAM_PAGE_SIZE,
                )
                SearchResults(
                    telegram = (shown.telegram + page.items).distinctBy { it.id }.toImmutableList(),
                    paging = page.hasMore.toPaging(),
                )
            }

            SearchSource.LocalFile -> SearchResults()
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

    /** The list scrolled near its end, or the user asked to retry a page that failed. */
    fun loadMore() {
        loadMoreRequests.trySend(Unit)
    }

    fun onDirectLinkChange(text: String) {
        directLink.update { it.copy(text = text, error = null) }
    }

    /**
     * Looks the pasted repository up before opening it: that catches a typo or a private
     * repository here rather than on a details page that cannot load, and takes the owner and name
     * as GitHub spells them, which is what the app's row will store.
     */
    fun openDirectLink(onFound: (GithubRepoSummary) -> Unit) {
        val current = directLink.value
        if (current.isOpening) return
        val link = GithubRepoLink.parse(current.text)
        if (link == null) {
            directLink.update { it.copy(error = UiText.of(R.string.search_link_invalid)) }
            return
        }
        directLink.update { it.copy(isOpening = true, error = null) }
        viewModelScope.launch {
            suspendRunCatching { githubRepository.getRepository(link.owner, link.repo) }
                .onSuccess { repo ->
                    directLink.value = DirectLinkState()
                    onFound(repo)
                }
                .onFailure { e ->
                    logger.w(TAG, e) { "Could not open ${link.owner}/${link.repo}" }
                    if (e is AppError.NotFound) {
                        directLink.update {
                            it.copy(isOpening = false, error = UiText.of(R.string.search_link_not_found))
                        }
                    } else {
                        directLink.update { it.copy(isOpening = false) }
                        _messages.tryEmit(e.toUiText())
                    }
                }
        }
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

    private data class SearchKey(
        val query: String,
        val source: SearchSource,
        val searchable: Boolean,
    )

    private data class SearchResults(
        val github: ImmutableList<GithubRepoSummary> = persistentListOf(),
        val telegram: ImmutableList<TelegramChatSummary> = persistentListOf(),
        val paging: SearchPaging = SearchPaging.Exhausted,
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
        const val TELEGRAM_PAGE_SIZE = TelegramRepository.DEFAULT_SEARCH_LIMIT

        fun Boolean.toPaging(): SearchPaging =
            if (this) SearchPaging.MoreAvailable else SearchPaging.Exhausted

        /** Signed out, or part-way through signing in. Not while TDLib is still starting up. */
        val TelegramAuthState.needsLogin: Boolean
            get() = this !is TelegramAuthState.Ready && this !is TelegramAuthState.Initialising
    }
}
