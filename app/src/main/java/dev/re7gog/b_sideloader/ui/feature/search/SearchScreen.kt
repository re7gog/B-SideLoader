package dev.re7gog.b_sideloader.ui.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.domain.model.GithubRepoSummary
import dev.re7gog.b_sideloader.domain.model.TelegramChatSummary
import dev.re7gog.b_sideloader.domain.model.TelegramTopicSummary
import dev.re7gog.b_sideloader.ui.common.component.EmptyState
import dev.re7gog.b_sideloader.ui.common.component.PasteIconButton
import dev.re7gog.b_sideloader.ui.common.component.SnackbarMessages
import dev.re7gog.b_sideloader.ui.common.component.TelegramAvatar
import dev.re7gog.b_sideloader.ui.common.text.asString
import dev.re7gog.b_sideloader.ui.feature.manualinstall.ManualInstallPane
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/**
 * Unified search.
 *
 * Every source shares the same shape — a pill search field carrying a browser-style source picker,
 * and one result-row style — so switching source changes the data, never the layout. Sources that
 * take no query swap the field for a plain source pill and render their own body. Selecting a
 * Telegram forum channel drills into a topic list built from the same rows.
 */
@Composable
fun SearchScreen(
    onGithubRepoClick: (GithubRepoSummary) -> Unit,
    onTelegramTargetClick: (chatId: Long, topicId: Int, title: String) -> Unit,
    onTelegramLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    SnackbarMessages(messages = viewModel.messages, hostState = snackbarHostState)

    SearchScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onQueryChange = viewModel::onQueryChange,
        onClearQuery = viewModel::clearQuery,
        onSourceSelected = viewModel::onSourceSelected,
        onGithubRepoClick = onGithubRepoClick,
        onChatClick = { chat -> viewModel.onChatSelected(chat, onTelegramTargetClick) },
        onTopicClick = { topic ->
            uiState.topicsOf?.let { chat ->
                // The group's name, not the topic's: a topic is a section of a channel, and
                // "Releases" or "APK" on its own says nothing about which app it belongs to. The
                // details screen lets the name be edited, which is where a topic-specific name
                // belongs if the user wants one.
                onTelegramTargetClick(chat.id, topic.id, chat.title)
            }
        },
        onBackToChats = viewModel::onBackToChats,
        onLoadMore = viewModel::loadMore,
        onDirectLinkChange = viewModel::onDirectLinkChange,
        onOpenDirectLink = { viewModel.openDirectLink(onGithubRepoClick) },
        onTelegramLoginClick = onTelegramLoginClick,
        downloadPhoto = { fileId -> viewModel.downloadPhoto(fileId) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchScreen(
    uiState: SearchUiState,
    snackbarHostState: SnackbarHostState,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onSourceSelected: (SearchSource) -> Unit,
    onGithubRepoClick: (GithubRepoSummary) -> Unit,
    onChatClick: (TelegramChatSummary) -> Unit,
    onTopicClick: (TelegramTopicSummary) -> Unit,
    onBackToChats: () -> Unit,
    onLoadMore: () -> Unit,
    onDirectLinkChange: (String) -> Unit,
    onOpenDirectLink: () -> Unit,
    onTelegramLoginClick: () -> Unit,
    downloadPhoto: suspend (Int) -> String?,
    modifier: Modifier = Modifier,
) {
    // Scaffold-based screens paint their own background; this one has none, so during a predictive
    // back gesture the entry underneath showed through it.
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (uiState.inTopicList) {
            TopicListHeader(title = uiState.topicsOf?.title.orEmpty(), onBack = onBackToChats)
        } else {
            SearchHeader(
                query = uiState.query,
                onQueryChange = onQueryChange,
                onClear = onClearQuery,
                source = uiState.source,
                onSourceSelected = onSourceSelected,
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (!uiState.inTopicList && uiState.isLoading) {
                    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                when {
                    uiState.inTopicList -> TopicResultsList(uiState.topics, onTopicClick)

                    uiState.source == SearchSource.LocalFile ->
                        ManualInstallPane(snackbarHostState = snackbarHostState)

                    uiState.source == SearchSource.GitHub -> when {
                        uiState.githubResults.isNotEmpty() || uiState.isLoading -> GithubResultsList(
                            repos = uiState.githubResults,
                            query = uiState.query,
                            paging = uiState.paging,
                            onLoadMore = onLoadMore,
                            onRepoClick = onGithubRepoClick,
                        )

                        uiState.query.isBlank() -> GithubStartState(
                            link = uiState.directLink,
                            onLinkChange = onDirectLinkChange,
                            onOpenLink = onOpenDirectLink,
                        )

                        else -> SearchEmptyState(
                            iconRes = githubIconRes(),
                            hasQuery = true,
                            emptyTitle = R.string.search_github_empty_title,
                            emptySubtitle = R.string.search_github_empty_subtitle,
                            notFoundTitle = R.string.no_repositories_found,
                        )
                    }

                    else -> when {
                        uiState.telegramNeedsLogin -> TelegramLoginState(onTelegramLoginClick)

                        uiState.telegramChats.isEmpty() && !uiState.isLoading -> SearchEmptyState(
                            iconRes = R.drawable.telegram,
                            hasQuery = uiState.query.isNotBlank(),
                            emptyTitle = R.string.search_telegram_empty_title,
                            emptySubtitle = R.string.search_telegram_empty_subtitle,
                            notFoundTitle = R.string.no_channels_found,
                        )

                        else -> TelegramResultsList(
                            chats = uiState.telegramChats,
                            query = uiState.query,
                            paging = uiState.paging,
                            onLoadMore = onLoadMore,
                            downloadPhoto = downloadPhoto,
                            onChatClick = onChatClick,
                        )
                    }
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * Header for every source root: the source picker plus, when the source searches, its query field.
 */
@Composable
private fun SearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    source: SearchSource,
    onSourceSelected: (SearchSource) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }

    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (source.isSearchable) {
                SearchInputField(
                    query = query,
                    onQueryChange = onQueryChange,
                    onClear = onClear,
                    placeholder = source.searchPlaceholderRes?.let { stringResource(it) }.orEmpty(),
                    source = source,
                    onSourceClick = { showPicker = true },
                )
            } else {
                SourceSelectorPill(source = source, onClick = { showPicker = true })
            }
        }
    }

    if (showPicker) {
        SourcePickerSheet(
            currentSource = source,
            onSourceSelected = {
                showPicker = false
                onSourceSelected(it)
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun SearchInputField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    placeholder: String,
    source: SearchSource,
    onSourceClick: () -> Unit,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        leadingIcon = { SourceSelectorButton(source = source, onClick = onSourceClick) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        painterResource(R.drawable.close_24px),
                        contentDescription = stringResource(R.string.cd_clear),
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
        ),
    )
}

/**
 * GitHub's empty page: what the search field is for and, below it, a way around search for when
 * it does not rank the wanted repository anywhere useful — the repository's own link.
 */
@Composable
private fun GithubStartState(
    link: DirectLinkState,
    onLinkChange: (String) -> Unit,
    onOpenLink: () -> Unit,
) {
    EmptyState(
        iconRes = githubIconRes(),
        title = stringResource(R.string.search_github_empty_title),
        subtitle = stringResource(R.string.search_github_empty_subtitle),
        // Keeps the field and its button above the keyboard rather than under it.
        modifier = Modifier.imePadding(),
    ) {
        Row(
            modifier = Modifier.widthIn(max = LINK_FORM_MAX_WIDTH),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.search_or),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            HorizontalDivider(modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = link.text,
            onValueChange = onLinkChange,
            modifier = Modifier
                .widthIn(max = LINK_FORM_MAX_WIDTH)
                .fillMaxWidth(),
            label = { Text(stringResource(R.string.search_link_label)) },
            placeholder = { Text(stringResource(R.string.search_link_placeholder)) },
            trailingIcon = { PasteIconButton(onPaste = onLinkChange) },
            isError = link.error != null,
            supportingText = link.error?.let { error -> { Text(error.asString()) } },
            enabled = !link.isOpening,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onOpenLink() }),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onOpenLink,
            enabled = link.text.isNotBlank() && !link.isOpening,
        ) {
            if (link.isOpening) {
                CircularProgressIndicator(
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            }
            Text(stringResource(R.string.search_link_open))
        }
    }
}

/** Telegram's search runs through the user's account, so signed out there is nothing to search. */
@Composable
private fun TelegramLoginState(onLoginClick: () -> Unit) {
    EmptyState(
        iconRes = R.drawable.telegram,
        title = stringResource(R.string.search_telegram_login_title),
        subtitle = stringResource(R.string.search_telegram_login_subtitle),
    ) {
        Button(onClick = onLoginClick) {
            Text(stringResource(R.string.search_telegram_login_action))
        }
    }
}

@Composable
private fun GithubResultsList(
    repos: ImmutableList<GithubRepoSummary>,
    query: String,
    paging: SearchPaging,
    onLoadMore: () -> Unit,
    onRepoClick: (GithubRepoSummary) -> Unit,
) {
    PagedResultsList(query = query, paging = paging, onLoadMore = onLoadMore) {
        items(repos, key = { it.slug }) { repo ->
            SearchResultRow(
                title = repo.name,
                subtitle = stringResource(
                    R.string.repo_subtitle,
                    repo.owner,
                    repo.description ?: stringResource(R.string.no_description),
                ),
                leadingContent = {
                    AsyncImage(
                        model = repo.avatarUrl,
                        contentDescription = null,
                        placeholder = painterResource(R.drawable.circle_24px),
                        error = painterResource(R.drawable.circle_24px),
                        fallback = painterResource(R.drawable.circle_24px),
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape),
                    )
                },
                onClick = { onRepoClick(repo) },
            )
        }
    }
}

@Composable
private fun TelegramResultsList(
    chats: ImmutableList<TelegramChatSummary>,
    query: String,
    paging: SearchPaging,
    onLoadMore: () -> Unit,
    downloadPhoto: suspend (Int) -> String?,
    onChatClick: (TelegramChatSummary) -> Unit,
) {
    PagedResultsList(query = query, paging = paging, onLoadMore = onLoadMore) {
        items(chats, key = { it.id }) { chat ->
            SearchResultRow(
                title = chat.title,
                subtitle = stringResource(R.string.channel),
                leadingContent = {
                    TelegramAvatar(
                        fallbackText = chat.title.take(1).uppercase().ifEmpty { "?" },
                        photoFileId = chat.photoFileId,
                        downloadPhoto = downloadPhoto,
                        modifier = Modifier.size(40.dp),
                    )
                },
                onClick = { onChatClick(chat) },
            )
        }
    }
}

/**
 * A result list that asks for its next page once the user scrolls near the end, with a footer for
 * the page on its way or one that failed.
 *
 * The scroll position belongs to one [query]: a new query starts at the top, while coming back
 * from a result restores where the user was.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PagedResultsList(
    query: String,
    paging: SearchPaging,
    onLoadMore: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    val listState = rememberSaveable(query, saver = LazyListState.Saver) { LazyListState() }
    LoadMoreEffect(listState = listState, paging = paging, onLoadMore = onLoadMore)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        content()
        when (paging) {
            SearchPaging.Loading -> item(key = FOOTER_KEY, contentType = FOOTER_KEY) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }
            }

            SearchPaging.Failed -> item(key = FOOTER_KEY, contentType = FOOTER_KEY) {
                LoadMoreFailedRow(onRetry = onLoadMore)
            }

            SearchPaging.MoreAvailable, SearchPaging.Exhausted -> Unit
        }
    }
}

/**
 * Calls [onLoadMore] when the end of the list comes within [LOAD_MORE_THRESHOLD] rows while more
 * is available — straight away, too, when the first page does not fill the screen. It fires once
 * per approach: the next call waits for that page to arrive and the end to come close again.
 */
@Composable
private fun LoadMoreEffect(
    listState: LazyListState,
    paging: SearchPaging,
    onLoadMore: () -> Unit,
) {
    val canLoadMore by rememberUpdatedState(paging == SearchPaging.MoreAvailable)
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)

    LaunchedEffect(listState) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: return@snapshotFlow false
            canLoadMore && lastVisible >= layout.totalItemsCount - LOAD_MORE_THRESHOLD
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { currentOnLoadMore() }
    }
}

@Composable
private fun LoadMoreFailedRow(onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.search_load_more_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.search_load_more_retry))
        }
    }
}

@Composable
private fun TopicResultsList(
    topics: ImmutableList<TelegramTopicSummary>,
    onTopicClick: (TelegramTopicSummary) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(topics, key = { it.id }) { topic ->
            // Telegram gives each topic an accent colour as 0xRRGGBB; opaque it for the avatar.
            val container = if (topic.hasIconColor) {
                Color(OPAQUE_ALPHA or (topic.iconColor and RGB_MASK))
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }
            val onContainer = if (topic.hasIconColor) {
                Color.White
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            }
            SearchResultRow(
                title = topic.name,
                subtitle = null,
                leadingContent = {
                    TelegramAvatar(
                        fallbackText = topic.name.take(1).uppercase().ifEmpty { "#" },
                        modifier = Modifier.size(40.dp),
                        containerColor = container,
                        contentColor = onContainer,
                    )
                },
                onClick = { onTopicClick(topic) },
            )
        }
    }
}

/** One row style, shared by GitHub repos, Telegram channels and forum topics. */
@Composable
private fun SearchResultRow(
    title: String,
    subtitle: String?,
    leadingContent: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        supportingContent = subtitle?.let {
            { Text(text = it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        leadingContent = leadingContent,
        trailingContent = {
            Icon(
                painter = painterResource(R.drawable.chevron_right_24px),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    ) {
        Text(
            text = title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopicListHeader(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painterResource(R.drawable.arrow_back_24px),
                    contentDescription = stringResource(R.string.cd_back),
                )
            }
        },
    )
}

/** Covers both "start searching" and "nothing matched", which differ only in wording. */
@Composable
private fun SearchEmptyState(
    iconRes: Int,
    hasQuery: Boolean,
    emptyTitle: Int,
    emptySubtitle: Int,
    notFoundTitle: Int,
) {
    EmptyState(
        iconRes = iconRes,
        title = stringResource(if (hasQuery) notFoundTitle else emptyTitle),
        subtitle = stringResource(if (hasQuery) R.string.try_different_name else emptySubtitle),
    )
}

private const val FOOTER_KEY = "footer"

/** How close to the end of the list, in rows, the next page is asked for. */
private const val LOAD_MORE_THRESHOLD = 5

private val LINK_FORM_MAX_WIDTH = 480.dp

private const val OPAQUE_ALPHA = 0xFF000000.toInt()
private const val RGB_MASK = 0xFFFFFF
