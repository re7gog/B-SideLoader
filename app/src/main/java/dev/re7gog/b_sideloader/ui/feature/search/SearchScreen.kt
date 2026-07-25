package dev.re7gog.b_sideloader.ui.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import dev.re7gog.b_sideloader.ui.common.component.SnackbarMessages
import dev.re7gog.b_sideloader.ui.common.component.TelegramAvatar
import dev.re7gog.b_sideloader.ui.feature.manualinstall.ManualInstallPane
import kotlinx.collections.immutable.ImmutableList

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
                onTelegramTargetClick(chat.id, topic.id, topic.name)
            }
        },
        onBackToChats = viewModel::onBackToChats,
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
    downloadPhoto: suspend (Int) -> String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
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

                    uiState.source == SearchSource.GitHub ->
                        if (uiState.githubResults.isEmpty() && !uiState.isLoading) {
                            SearchEmptyState(
                                iconRes = githubIconRes(),
                                hasQuery = uiState.query.isNotBlank(),
                                emptyTitle = R.string.search_github_empty_title,
                                emptySubtitle = R.string.search_github_empty_subtitle,
                                notFoundTitle = R.string.no_repositories_found,
                            )
                        } else {
                            GithubResultsList(uiState.githubResults, onGithubRepoClick)
                        }

                    else ->
                        if (uiState.telegramChats.isEmpty() && !uiState.isLoading) {
                            SearchEmptyState(
                                iconRes = R.drawable.telegram,
                                hasQuery = uiState.query.isNotBlank(),
                                emptyTitle = R.string.search_telegram_empty_title,
                                emptySubtitle = R.string.search_telegram_empty_subtitle,
                                notFoundTitle = R.string.no_channels_found,
                            )
                        } else {
                            TelegramResultsList(uiState.telegramChats, downloadPhoto, onChatClick)
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

@Composable
private fun GithubResultsList(
    repos: ImmutableList<GithubRepoSummary>,
    onRepoClick: (GithubRepoSummary) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
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
    downloadPhoto: suspend (Int) -> String?,
    onChatClick: (TelegramChatSummary) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
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

private const val OPAQUE_ALPHA = 0xFF000000.toInt()
private const val RGB_MASK = 0xFFFFFF
