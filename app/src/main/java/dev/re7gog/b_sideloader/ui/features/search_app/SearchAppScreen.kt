package dev.re7gog.b_sideloader.ui.features.search_app

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.data.remote.dto.GithubRepoDto
import dev.re7gog.b_sideloader.ui.components.TelegramAvatar
import org.drinkless.tdlib.TdApi

/**
 * Unified search screen. GitHub and Telegram share the exact same layout — a pill search
 * field, an explicit source toggle, and one result-row style — so switching source only
 * changes the data, never the shape of the screen. Selecting a Telegram forum channel
 * drills into a topic list rendered with the same rows.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchAppScreen(
    onGhSearchResClick: (repo: GithubRepoDto) -> Unit,
    onTgSearchResClick: (messageList: SelectionState.MessageList) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchAppViewModel = hiltViewModel()
) {
    val searchSource by viewModel.searchSource.collectAsStateWithLifecycle()
    val selectionState by viewModel.selectionState.collectAsStateWithLifecycle()
    val topics by viewModel.topics.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val ghResult by viewModel.ghSearchResult.collectAsStateWithLifecycle()
    val tgResults by viewModel.tgSearchResults.collectAsStateWithLifecycle()
    val ghLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val tgLoading by viewModel.tgIsLoading.collectAsStateWithLifecycle()

    LaunchedEffect(selectionState) {
        val state = selectionState
        if (state is SelectionState.MessageList) {
            onTgSearchResClick(state)
            // Reset so navigating back here shows the list again instead of
            // immediately re-opening the details page
            viewModel.onBackToChats()
        }
    }

    val inTopicList = selectionState is SelectionState.TopicList
    val isLoading = when (searchSource) {
        SearchSource.GitHub -> ghLoading
        SearchSource.Telegram -> tgLoading
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (inTopicList) {
            TopicListHeader(
                title = (selectionState as SelectionState.TopicList).chatTitle,
                onBack = viewModel::onBackToChats
            )
        } else {
            SearchHeader(
                query = searchQuery,
                onQueryChange = viewModel::onQueryChange,
                onClear = viewModel::clearQuery,
                source = searchSource,
                onSourceSelected = viewModel::onSourceSelected
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (!inTopicList && isLoading) {
                    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                when {
                    inTopicList -> TopicResultsList(
                        topics = topics,
                        onTopicClick = { viewModel.onTopicSelected(it.info.forumTopicId) }
                    )

                    searchSource == SearchSource.GitHub -> {
                        if (ghResult.isEmpty() && !isLoading) {
                            SearchEmptyState(
                                iconRes = githubIconRes(),
                                title = if (searchQuery.isBlank()) "Search GitHub"
                                        else "No repositories found",
                                subtitle = if (searchQuery.isBlank()) "Find apps from public repositories"
                                           else "Try a different name"
                            )
                        } else {
                            GithubResultsList(ghResult, onGhSearchResClick)
                        }
                    }

                    else -> { // Telegram chat list
                        if (tgResults.isEmpty() && !isLoading) {
                            SearchEmptyState(
                                iconRes = R.drawable.telegram,
                                title = if (searchQuery.isBlank()) "Search Telegram"
                                        else "No channels found",
                                subtitle = if (searchQuery.isBlank()) "Find channels that publish APKs"
                                           else "Try a different name"
                            )
                        } else {
                            TelegramResultsList(
                                chats = tgResults,
                                downloadPhoto = viewModel::downloadPhoto,
                                onChatClick = viewModel::onChatSelected
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Persistent search field + source toggle shown for both GitHub and Telegram roots. */
@Composable
fun SearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    source: SearchSource,
    onSourceSelected: (SearchSource) -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SearchInputField(
                query = query,
                onQueryChange = onQueryChange,
                onClear = onClear,
                placeholder = when (source) {
                    SearchSource.GitHub -> "Search GitHub repositories"
                    SearchSource.Telegram -> "Search Telegram channels"
                }
            )
            SourceSegmentedToggle(
                currentSource = source,
                onSourceSelected = onSourceSelected
            )
        }
    }
}

@Composable
fun SearchInputField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        leadingIcon = {
            Icon(painterResource(R.drawable.search_24px), contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(painterResource(R.drawable.close_24px), contentDescription = "Clear")
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
            errorIndicatorColor = Color.Transparent
        )
    )
}

/** Explicit, always-visible source selector replacing the old hidden dropdown. */
@Composable
fun SourceSegmentedToggle(
    currentSource: SearchSource,
    onSourceSelected: (SearchSource) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val sources = SearchSource.entries
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        sources.forEachIndexed { index, source ->
            SegmentedButton(
                selected = currentSource == source,
                onClick = { onSourceSelected(source) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = sources.size),
                icon = {
                    val iconRes = when (source) {
                        SearchSource.GitHub -> if (isDark) R.drawable.github_invertocat_white
                                               else R.drawable.github_invertocat_black
                        SearchSource.Telegram -> R.drawable.telegram
                    }
                    Image(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                label = {
                    Text(
                        when (source) {
                            SearchSource.GitHub -> "GitHub"
                            SearchSource.Telegram -> "Telegram"
                        }
                    )
                }
            )
        }
    }
}

@Composable
fun GithubResultsList(
    repos: List<GithubRepoDto>,
    onRepoClick: (GithubRepoDto) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(repos, key = { it.owner.login + "/" + it.name }) { repo ->
            SearchResultRow(
                title = repo.name,
                subtitle = "${repo.owner.login} · ${repo.description ?: "No description"}",
                leadingContent = {
                    AsyncImage(
                        model = repo.owner.avatarUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                    )
                },
                onClick = { onRepoClick(repo) }
            )
        }
    }
}

@Composable
fun TelegramResultsList(
    chats: List<TdApi.Chat>,
    downloadPhoto: suspend (Int) -> String?,
    onChatClick: (TdApi.Chat) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(chats, key = { it.id }) { chat ->
            SearchResultRow(
                title = chat.title,
                subtitle = "Channel",
                leadingContent = {
                    TelegramAvatar(
                        fallbackText = chat.title.take(1).uppercase(),
                        photoFileId = chat.photo?.small?.id,
                        downloadPhoto = downloadPhoto,
                        modifier = Modifier.size(40.dp)
                    )
                },
                onClick = { onChatClick(chat) }
            )
        }
    }
}

@Composable
fun TopicResultsList(
    topics: List<TdApi.ForumTopic>,
    onTopicClick: (TdApi.ForumTopic) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(topics, key = { it.info.forumTopicId }) { topic ->
            val iconColor = topic.info.icon.color
            val hasColor = iconColor != 0
            val container = if (hasColor) {
                Color(0xFF000000.toInt() or (iconColor and 0xFFFFFF))
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }
            val onContainer = if (hasColor) Color.White
                              else MaterialTheme.colorScheme.onSecondaryContainer

            SearchResultRow(
                title = topic.info.name,
                subtitle = null,
                leadingContent = {
                    TelegramAvatar(
                        fallbackText = topic.info.name.take(1).uppercase().ifEmpty { "#" },
                        modifier = Modifier.size(40.dp),
                        containerColor = container,
                        contentColor = onContainer
                    )
                },
                onClick = { onTopicClick(topic) }
            )
        }
    }
}

/** Single row style shared by GitHub repos, Telegram channels and forum topics. */
@Composable
fun SearchResultRow(
    title: String,
    subtitle: String?,
    leadingContent: @Composable () -> Unit,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        supportingContent = subtitle?.let {
            {
                Text(
                    text = it,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        leadingContent = leadingContent,
        trailingContent = {
            Icon(
                painter = painterResource(R.drawable.chevron_right_24px),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    ) {
        Text(
            text = title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicListHeader(
    title: String,
    onBack: () -> Unit
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.arrow_back_24px), contentDescription = "Back")
            }
        }
    )
}

/** Shared placeholder for both the initial ("start searching") and no-results states. */
@Composable
fun SearchEmptyState(
    iconRes: Int,
    title: String,
    subtitle: String
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun githubIconRes(): Int =
    if (isSystemInDarkTheme()) R.drawable.github_invertocat_white
    else R.drawable.github_invertocat_black
