package dev.re7gog.b_sideloader.ui.feature.search

import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.core.log.NoopLogger
import dev.re7gog.b_sideloader.domain.error.AppError
import dev.re7gog.b_sideloader.domain.model.GithubRepoSummary
import dev.re7gog.b_sideloader.domain.model.TelegramAuthState
import dev.re7gog.b_sideloader.domain.model.TelegramChatSummary
import dev.re7gog.b_sideloader.testing.FakeGithubRepository
import dev.re7gog.b_sideloader.testing.FakeTelegramRepository
import dev.re7gog.b_sideloader.testing.MainDispatcherRule
import dev.re7gog.b_sideloader.ui.common.text.UiText
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val github = FakeGithubRepository(
        repositories = (1..5).map { GithubRepoSummary(owner = "dev", name = "app$it") },
    ).apply { pageSize = 2 }
    private val telegram = FakeTelegramRepository(
        chats = listOf(TelegramChatSummary(id = -100L, title = "Builds channel")),
    )

    private fun TestScope.viewModel(): SearchViewModel =
        SearchViewModel(github, telegram, NoopLogger).also { vm ->
            backgroundScope.launch { vm.uiState.collect {} }
        }

    private val SearchViewModel.state get() = uiState.value

    @Test
    fun `scrolling for more appends the next page until the source runs out`() = runTest {
        val vm = viewModel()

        vm.onQueryChange("app")
        advanceUntilIdle()
        assertEquals(listOf("app1", "app2"), vm.state.githubResults.map { it.name })
        assertEquals(SearchPaging.MoreAvailable, vm.state.paging)

        vm.loadMore()
        advanceUntilIdle()
        assertEquals(4, vm.state.githubResults.size)

        vm.loadMore()
        advanceUntilIdle()
        assertEquals(5, vm.state.githubResults.size)
        assertEquals(SearchPaging.Exhausted, vm.state.paging)
        assertEquals(listOf(1, 2, 3), github.searchedPages)
    }

    @Test
    fun `a page that fails keeps the rows shown and is asked for again on retry`() = runTest {
        val vm = viewModel()
        vm.onQueryChange("app")
        advanceUntilIdle()

        github.failure = AppError.Network()
        vm.loadMore()
        advanceUntilIdle()
        assertEquals(SearchPaging.Failed, vm.state.paging)
        assertEquals(2, vm.state.githubResults.size)

        github.failure = null
        vm.loadMore()
        advanceUntilIdle()
        assertEquals(4, vm.state.githubResults.size)
        assertEquals(listOf(1, 2, 2), github.searchedPages)
    }

    @Test
    fun `a new query starts again from its first page`() = runTest {
        val vm = viewModel()
        vm.onQueryChange("app")
        advanceUntilIdle()
        vm.loadMore()
        advanceUntilIdle()

        vm.onQueryChange("app5")
        advanceUntilIdle()

        assertEquals(listOf("app5"), vm.state.githubResults.map { it.name })
        assertEquals(SearchPaging.Exhausted, vm.state.paging)
        assertEquals(listOf(1, 2, 1), github.searchedPages)
    }

    @Test
    fun `signed out of Telegram it asks for a login instead of searching`() = runTest {
        telegram.setAuthState(TelegramAuthState.LoggedOut)
        val vm = viewModel()

        vm.onSourceSelected(SearchSource.Telegram)
        vm.onQueryChange("builds")
        advanceUntilIdle()

        assertTrue(vm.state.telegramNeedsLogin)
        assertTrue(telegram.searchedQueries.isEmpty())

        telegram.setAuthState(TelegramAuthState.Ready)
        advanceUntilIdle()

        assertFalse(vm.state.telegramNeedsLogin)
        assertEquals(listOf("Builds channel"), vm.state.telegramChats.map { it.title })
    }

    @Test
    fun `no login note while TDLib is still starting`() = runTest {
        telegram.setAuthState(TelegramAuthState.Initialising)
        val vm = viewModel()

        vm.onSourceSelected(SearchSource.Telegram)
        advanceUntilIdle()

        assertFalse(vm.state.telegramNeedsLogin)
    }

    @Test
    fun `a pasted link opens the repository as GitHub describes it`() = runTest {
        github.repositories = listOf(GithubRepoSummary(owner = "octocat", name = "hello-world", description = "Greets"))
        val vm = viewModel()
        var opened: GithubRepoSummary? = null

        vm.onDirectLinkChange("https://github.com/octocat/hello-world/releases")
        vm.openDirectLink { opened = it }
        advanceUntilIdle()

        assertEquals("Greets", opened?.description)
        assertEquals(DirectLinkState(), vm.state.directLink)
    }

    @Test
    fun `something that is not a repository link is refused without a lookup`() = runTest {
        val vm = viewModel()
        var opened: GithubRepoSummary? = null

        vm.onDirectLinkChange("https://gitlab.com/octocat/hello-world")
        vm.openDirectLink { opened = it }
        advanceUntilIdle()

        assertNull(opened)
        assertEquals(UiText.of(R.string.search_link_invalid), vm.state.directLink.error)
    }

    @Test
    fun `a repository GitHub does not know is reported under the field`() = runTest {
        github.failure = AppError.NotFound()
        val vm = viewModel()
        var opened: GithubRepoSummary? = null

        vm.onDirectLinkChange("octocat/nope")
        vm.openDirectLink { opened = it }
        advanceUntilIdle()

        assertNull(opened)
        assertFalse(vm.state.directLink.isOpening)
        assertEquals(UiText.of(R.string.search_link_not_found), vm.state.directLink.error)

        vm.onDirectLinkChange("octocat/nope2")
        advanceUntilIdle()
        assertNull(vm.state.directLink.error)
    }
}
