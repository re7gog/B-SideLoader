package dev.re7gog.b_sideloader.ui.feature.search

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.domain.model.GithubRepoSummary
import dev.re7gog.b_sideloader.domain.model.TelegramChatSummary
import dev.re7gog.b_sideloader.domain.model.TelegramTopicSummary
import dev.re7gog.b_sideloader.ui.theme.BSideLoaderTheme
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The search screen through its stateless overload. The local-file source is left out: its pane
 * brings its own Hilt ViewModel, which is not what these cases are about.
 */
@RunWith(AndroidJUnit4::class)
class SearchScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val repo = GithubRepoSummary(owner = "octocat", name = "hello-world", description = "Greets")
    private val channel = TelegramChatSummary(id = -100L, title = "Builds channel")

    @Test
    fun beforeSearchingItExplainsWhatToSearchFor() {
        setContent(SearchUiState())

        composeRule.onNodeWithText(string(R.string.search_github_empty_title)).assertIsDisplayed()
    }

    /** "Start typing" and "nothing matched" are different situations and say so. */
    @Test
    fun aQueryWithNoResultsSaysNothingWasFound() {
        setContent(SearchUiState(query = "zzzz"))

        composeRule.onNodeWithText(string(R.string.no_repositories_found)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.try_different_name)).assertIsDisplayed()
    }

    @Test
    fun typingReportsTheQuery() {
        val typed = mutableListOf<String>()
        setContent(SearchUiState(), onQueryChange = { typed += it })

        composeRule.onNodeWithText(string(R.string.source_github_placeholder)).performTextInput("fdroid")

        assertEquals("fdroid", typed.last())
    }

    @Test
    fun clearingIsOfferedOnlyOnceThereIsAQuery() {
        var cleared = false
        setContent(SearchUiState(query = "fdroid"), onClearQuery = { cleared = true })

        composeRule.onNodeWithContentDescription(string(R.string.cd_clear)).performClick()

        assertTrue(cleared)
    }

    @Test
    fun githubResultsShowOwnerAndDescriptionAndOpenTheRepository() {
        var opened: GithubRepoSummary? = null
        setContent(
            SearchUiState(query = "hello", githubResults = persistentListOf(repo)),
            onGithubRepoClick = { opened = it },
        )

        composeRule.onNodeWithText(string(R.string.repo_subtitle, "octocat", "Greets")).assertIsDisplayed()
        composeRule.onNodeWithText("hello-world").performClick()

        assertEquals(repo, opened)
    }

    @Test
    fun telegramResultsOpenTheChat() {
        var opened: TelegramChatSummary? = null
        setContent(
            SearchUiState(
                query = "builds",
                source = SearchSource.Telegram,
                telegramChats = persistentListOf(channel),
            ),
            onChatClick = { opened = it },
        )

        composeRule.onNodeWithText("Builds channel").performClick()

        assertEquals(channel, opened)
    }

    /** Inside a forum the header becomes the forum itself, with a way back to the chat list. */
    @Test
    fun aForumsTopicListHasItsOwnHeaderAndBackButton() {
        var backToChats = false
        var openedTopic: TelegramTopicSummary? = null
        val topic = TelegramTopicSummary(id = 7, name = "Releases", iconColor = 0x3390EC)
        setContent(
            SearchUiState(
                source = SearchSource.Telegram,
                topicsOf = channel.copy(isForum = true),
                topics = persistentListOf(topic),
            ),
            onTopicClick = { openedTopic = it },
            onBackToChats = { backToChats = true },
        )

        composeRule.onNodeWithText("Builds channel").assertIsDisplayed()
        composeRule.onNodeWithText("Releases").performClick()
        composeRule.onNodeWithContentDescription(string(R.string.cd_back)).performClick()

        assertEquals(topic, openedTopic)
        assertTrue(backToChats)
    }

    @Test
    fun theSourcePickerSwitchesSource() {
        var selected: SearchSource? = null
        setContent(SearchUiState(), onSourceSelected = { selected = it })

        composeRule.onNodeWithContentDescription(string(R.string.cd_change_source)).performClick()
        composeRule.onNodeWithText(string(R.string.source_telegram_description)).performClick()

        assertEquals(SearchSource.Telegram, selected)
    }

    private fun setContent(
        uiState: SearchUiState,
        onQueryChange: (String) -> Unit = {},
        onClearQuery: () -> Unit = {},
        onSourceSelected: (SearchSource) -> Unit = {},
        onGithubRepoClick: (GithubRepoSummary) -> Unit = {},
        onChatClick: (TelegramChatSummary) -> Unit = {},
        onTopicClick: (TelegramTopicSummary) -> Unit = {},
        onBackToChats: () -> Unit = {},
    ) {
        composeRule.setContent {
            BSideLoaderTheme {
                SearchScreen(
                    uiState = uiState,
                    snackbarHostState = remember { SnackbarHostState() },
                    onQueryChange = onQueryChange,
                    onClearQuery = onClearQuery,
                    onSourceSelected = onSourceSelected,
                    onGithubRepoClick = onGithubRepoClick,
                    onChatClick = onChatClick,
                    onTopicClick = onTopicClick,
                    onBackToChats = onBackToChats,
                    downloadPhoto = { null },
                )
            }
        }
    }

    private fun string(@StringRes id: Int, vararg args: Any): String =
        composeRule.activity.getString(id, *args)
}
