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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.data.remote.dto.GithubRepoDto
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
    if (selectionState is SelectionState.MessageList) {
        onTgSearchResClick(selectionState as SelectionState.MessageList)
    }
    Box(
        modifier = modifier
    ) {
        if (searchSource == SearchSource.GitHub ||
            searchSource == SearchSource.Telegram && selectionState is SelectionState.ChatList) {
            SearchAppSearchBar(
                viewModel = viewModel,
                onSearchResClick = { onGhSearchResClick(it) },
                searchSource = searchSource,
                modifier = Modifier.fillMaxWidth()
            )
        } else if (selectionState is SelectionState.TopicList) {
            val topics by viewModel.topics.collectAsStateWithLifecycle()
            val state = selectionState as SelectionState.TopicList
            Column {
                TopAppBar(
                    title = { Text(state.chatTitle) },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.onBackToChats() }) {
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
                            viewModel.onTopicSelected(topic.info.chatId, topic.info.forumTopicId, "TODO ME!") // TODO: Chat label, not topic
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
    onSearchResClick: (repo: GithubRepoDto) -> Unit,
    viewModel: SearchAppViewModel,
    searchSource: SearchSource,
    modifier: Modifier = Modifier
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val tgResults by viewModel.telegramSearchResults.collectAsState()

    SearchBar(
        modifier = modifier,
        inputField = {
            SearchBarDefaults.InputField(
                query = searchQuery,
                onQueryChange = { viewModel.onQueryChange(it) },
                onSearch = { viewModel.isSearchExpanded = false },
                expanded = viewModel.isSearchExpanded,
                onExpandedChange = { viewModel.isSearchExpanded = it },
                placeholder = { Text(stringResource(R.string.search)) },
                leadingIcon = {
                    SearchSourceSelector(
                        currentSource = searchSource,
                        onSourceSelected = { viewModel.onSourceSelected(it) }
                    )
                },
                trailingIcon = {
                    if (viewModel.isSearchExpanded) {
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
        expanded = viewModel.isSearchExpanded,
        onExpandedChange = { viewModel.isSearchExpanded = it }
    ) {
        if (searchSource == SearchSource.GitHub) {
            if (viewModel.searchResult.isEmpty() && !viewModel.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Enter repo name",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (viewModel.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            SearchResults(
                viewModel = viewModel,
                onSearchResClick = onSearchResClick,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            if (tgResults.isEmpty()) {
                EmptyResultsPlaceholder(searchQuery.isEmpty())
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(tgResults, key = { it.id }) { chat ->
                        TelegramChatRow(chat = chat) {
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
    viewModel: SearchAppViewModel,
    modifier: Modifier = Modifier
) {
    if (viewModel.isLoading) {
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
            items(viewModel.searchResult) { repo ->
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
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = chat.title.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
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
    ListItem(
        modifier = Modifier.clickable { onClick() },
        headlineContent = { Text(topic.info.name) },
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.forum_24px),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary
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