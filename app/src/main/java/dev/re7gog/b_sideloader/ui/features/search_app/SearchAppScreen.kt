package dev.re7gog.b_sideloader.ui.features.search_app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import dev.re7gog.b_sideloader.data.remote.dto.GithubRepoDto
import dev.re7gog.b_sideloader.ui.components.TelegramAvatar
import org.drinkless.tdlib.TdApi

@OptIn(ExperimentalMaterial3Api::class)
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

    LaunchedEffect(selectionState) {
        val state = selectionState
        if (state is SelectionState.MessageList) {
            onTgSearchResClick(state)
            // Reset so navigating back here shows the list again instead of
            // immediately re-opening the details page
            viewModel.onBackToChats()
        }
    }
    Box(
        modifier = modifier
    ) {
        if (searchSource == SearchSource.GitHub ||
            searchSource == SearchSource.Telegram && selectionState is SelectionState.ChatList) {
            SearchAppSearchBar(
                onGhSearchResClick = onGhSearchResClick,
                searchSource = searchSource,
                viewModel = viewModel,
                modifier = Modifier.fillMaxWidth()
            )
        } else if (selectionState is SelectionState.TopicList) {
            Column {
                TopAppBar(
                    title = { Text((selectionState as SelectionState.TopicList).chatTitle) },
                    navigationIcon = {
                        IconButton(onClick = viewModel::onBackToChats) {
                            Icon(
                                painterResource(R.drawable.arrow_back_24px),
                                "Back"
                            )
                        }
                    }
                )
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(topics, key = { it.info.forumTopicId }) { topic ->
                        TelegramTopicRow(topic = topic) {
                            viewModel.onTopicSelected(topic.info.forumTopicId)
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchAppSearchBar(
    onGhSearchResClick: (repo: GithubRepoDto) -> Unit,
    searchSource: SearchSource,
    viewModel: SearchAppViewModel,
    modifier: Modifier = Modifier
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSearchExpanded by viewModel.isSearchExpanded.collectAsStateWithLifecycle()
    val ghResult by viewModel.ghSearchResult.collectAsStateWithLifecycle()
    val tgResults by viewModel.tgSearchResults.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    SearchBar(
        modifier = modifier,
        inputField = {
            SearchBarDefaults.InputField(
                query = searchQuery,
                onQueryChange = viewModel::onQueryChange,
                onSearch = { viewModel.changeSearchExpanded(false) },
                expanded = isSearchExpanded,
                onExpandedChange = viewModel::changeSearchExpanded,
                placeholder = { Text(stringResource(R.string.search)) },
                leadingIcon = {
                    SearchSourceSelector(
                        currentSource = searchSource,
                        onSourceSelected = viewModel::onSourceSelected
                    )
                },
                trailingIcon = {
                    if (isSearchExpanded) {
                        IconButton(onClick = viewModel::closeSearch) {
                            Icon(
                                painterResource(R.drawable.close_24px),
                                contentDescription = null
                            )
                        }
                    }
                }
            )
        },
        expanded = isSearchExpanded,
        onExpandedChange = viewModel::changeSearchExpanded
    ) {
        if (searchSource == SearchSource.GitHub) {
            if (ghResult.isEmpty() && !isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Enter repo name",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            SearchResults(
                onSearchResClick = onGhSearchResClick,
                isLoading = isLoading,
                ghSearchRes = ghResult,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            if (tgResults.isEmpty()) {
                EmptyResultsPlaceholder(searchQuery.isEmpty())
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(tgResults, key = { it.id }) { chat ->
                        TelegramChatRow(
                            chat = chat,
                            downloadPhoto = viewModel::downloadPhoto
                        ) {
                            viewModel.onChatSelected(chat)
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResults(
    onSearchResClick: (repo: GithubRepoDto) -> Unit,
    isLoading: Boolean,
    ghSearchRes: List<GithubRepoDto>,
    modifier: Modifier = Modifier
) {
    if (isLoading) {
        repeat(5) {
            Box(modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(16.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.height(8.dp))
        }
    } else {
        LazyColumn(modifier = modifier) {
            items(ghSearchRes) { repo ->
                ListItem(
                    headlineContent = { Text(repo.name + " by " + repo.owner.login)  },
                    supportingContent = { Text(repo.description ?: "No description", maxLines = 1) },
                    leadingContent = {
                        AsyncImage(
                            model = repo.owner.avatarUrl,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp).clip(CircleShape)
                        )
                    },
                    modifier = Modifier.clickable { onSearchResClick(repo) }
                )
            }
        }
    }
}

@Composable
fun TelegramChatRow(
    chat: TdApi.Chat,
    downloadPhoto: suspend (Int) -> String?,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable { onClick() },
        headlineContent = {
            Text(
                text = chat.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge
            )
        },
        supportingContent = {
            Text("Channel", style = MaterialTheme.typography.bodySmall)
        },
        leadingContent = {
            TelegramAvatar(
                fallbackText = chat.title.take(1).uppercase(),
                photoFileId = chat.photo?.small?.id,
                downloadPhoto = downloadPhoto,
                modifier = Modifier.size(40.dp)
            )
        },
        trailingContent = {
            Icon(
                painter = painterResource(R.drawable.chevron_right_24px),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }
    )
}

@Composable
fun TelegramTopicRow(
    topic: TdApi.ForumTopic,
    onClick: () -> Unit
) {
    val iconColor = topic.info.icon.color
    val hasColor = iconColor != 0
    val container = if (hasColor) {
        Color(0xFF000000.toInt() or (iconColor and 0xFFFFFF))
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val onContainer = if (hasColor) Color.White else MaterialTheme.colorScheme.onSecondaryContainer

    ListItem(
        modifier = Modifier.clickable { onClick() },
        headlineContent = { Text(topic.info.name) },
        leadingContent = {
            TelegramAvatar(
                fallbackText = topic.info.name.take(1).uppercase().ifEmpty { "#" },
                modifier = Modifier.size(40.dp),
                containerColor = container,
                contentColor = onContainer
            )
        },
        trailingContent = {
            Icon(painterResource(R.drawable.chevron_right_24px), null)
        }
    )
}

@Composable
fun EmptyResultsPlaceholder(isInitial: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = if (isInitial) "Try to search channel" else "Nothing found",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
fun SearchSourceSelector(
    currentSource: SearchSource,
    onSourceSelected: (SearchSource) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val isDark = isSystemInDarkTheme()
    val githubIcon = if (isDark) R.drawable.github_invertocat_white else R.drawable.github_invertocat_black
    val telegramIcon = R.drawable.telegram

    Box {
        IconButton(onClick = { expanded = !expanded }) {
            Image(
                painter = painterResource(
                    id = when (currentSource) {
                        SearchSource.GitHub -> githubIcon
                        SearchSource.Telegram -> telegramIcon
                    }
                ),
                contentDescription = "Search source",
                modifier = Modifier.size(24.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(16.dp)
        ) {
            DropdownMenuItem(
                text = { Text("GitHub") },
                onClick = {
                    onSourceSelected(SearchSource.GitHub)
                    expanded = false
                },
                leadingIcon = {
                    Image(
                        painter = painterResource(id = githubIcon),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )
            DropdownMenuItem(
                text = { Text("Telegram") },
                onClick = {
                    onSourceSelected(SearchSource.Telegram)
                    expanded = false
                },
                leadingIcon = {
                    Image(
                        painter = painterResource(id = telegramIcon),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )
        }
    }
}