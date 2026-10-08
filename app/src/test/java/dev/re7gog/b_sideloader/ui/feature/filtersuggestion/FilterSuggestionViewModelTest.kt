package dev.re7gog.b_sideloader.ui.feature.filtersuggestion

import dev.re7gog.b_sideloader.core.log.NoopLogger
import dev.re7gog.b_sideloader.domain.error.AiFailure
import dev.re7gog.b_sideloader.domain.error.AppError
import dev.re7gog.b_sideloader.domain.model.AiAvailability
import dev.re7gog.b_sideloader.domain.model.AiUnavailableReason
import dev.re7gog.b_sideloader.domain.model.FilterRule
import dev.re7gog.b_sideloader.domain.model.GithubAsset
import dev.re7gog.b_sideloader.domain.model.GithubRelease
import dev.re7gog.b_sideloader.domain.usecase.LoadSuggestionSourceUseCase
import dev.re7gog.b_sideloader.domain.usecase.SuggestFiltersUseCase
import dev.re7gog.b_sideloader.testing.FakeDeviceInfo
import dev.re7gog.b_sideloader.testing.FakeGithubRepository
import dev.re7gog.b_sideloader.testing.FakeLanguageModelGateway
import dev.re7gog.b_sideloader.testing.FakeTelegramRepository
import dev.re7gog.b_sideloader.testing.MainDispatcherRule
import dev.re7gog.b_sideloader.testing.githubApp
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FilterSuggestionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val github = FakeGithubRepository(
        releases = listOf(
            release("v2.0", "app-foss-arm64-v8a.apk", "app-gplay-arm64-v8a.apk", "app-foss-x86_64.apk"),
            release("v1.0", "app-foss-arm64-v8a.apk", "app-gplay-arm64-v8a.apk"),
        ),
    )
    private val model = FakeLanguageModelGateway()

    private fun viewModel() = FilterSuggestionViewModel(
        loadSource = LoadSuggestionSourceUseCase(github, FakeTelegramRepository()),
        suggestFilters = SuggestFiltersUseCase(model, FakeDeviceInfo()),
        languageModel = model,
        deviceInfo = FakeDeviceInfo(),
        logger = NoopLogger,
    )

    @Test
    fun `opening lists the newest release's files as examples`() = runTest {
        val viewModel = viewModel()

        viewModel.start(githubApp())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SuggestionPhase.Input, state.phase)
        assertEquals("v2.0", state.exampleGroupLabel)
        assertEquals(
            listOf("app-foss-arm64-v8a.apk", "app-gplay-arm64-v8a.apk", "app-foss-x86_64.apk"),
            state.exampleFiles.map { it.name },
        )
        assertEquals(listOf(true, true, false), state.exampleFiles.map { it.runsOnDevice })
        assertFalse(state.canSuggest)
    }

    @Test
    fun `picking an example suggests filters with no model involved`() = runTest {
        val viewModel = viewModel()
        viewModel.start(githubApp())
        advanceUntilIdle()

        viewModel.onExampleSelect(1)
        viewModel.onSuggest()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SuggestionPhase.Result, state.phase)
        assertEquals(FilterRule(include = "gplay"), state.suggestion?.proposal?.assetFilter)
        assertNull(state.suggestion?.backend)
        assertTrue(model.prompts.isEmpty())
    }

    @Test
    fun `picking the same example again un-picks it`() = runTest {
        val viewModel = viewModel()
        viewModel.start(githubApp())
        advanceUntilIdle()

        viewModel.onExampleSelect(0)
        viewModel.onExampleSelect(0)

        assertNull(viewModel.uiState.value.selectedExample)
    }

    @Test
    fun `words need a model, so with AI off only an example can be suggested`() = runTest {
        model.availability = AiAvailability.Unavailable(AiUnavailableReason.Disabled)
        val viewModel = viewModel()
        viewModel.start(githubApp())
        advanceUntilIdle()

        viewModel.onRequestChange("foss please")

        assertFalse(viewModel.uiState.value.canSuggest)
        assertFalse(viewModel.uiState.value.canRetry)
    }

    @Test
    fun `trying again tells the model what the user turned down and why`() = runTest {
        model.reply(
            """{"mode":"words","apk_include":"foss"}""",
            """{"mode":"words","apk_include":"gplay"}""",
        )
        val viewModel = viewModel()
        viewModel.start(githubApp())
        advanceUntilIdle()
        viewModel.onRequestChange("the right build")
        viewModel.onSuggest()
        advanceUntilIdle()

        viewModel.onFeedbackChange("I meant the Play Store one")
        viewModel.onRetry()
        advanceUntilIdle()

        assertEquals(FilterRule(include = "gplay"), viewModel.uiState.value.suggestion?.proposal?.assetFilter)
        val secondPrompt = model.prompts[1].input
        assertTrue(secondPrompt.contains("<rejected_by_user>"))
        assertTrue(secondPrompt.contains("I meant the Play Store one"))
    }

    @Test
    fun `a model error ends the run with its message`() = runTest {
        model.failure = AppError.Ai(AiFailure.QuotaExceeded)
        val viewModel = viewModel()
        viewModel.start(githubApp())
        advanceUntilIdle()
        viewModel.onRequestChange("foss")

        viewModel.onSuggest()
        advanceUntilIdle()

        assertEquals(SuggestionPhase.Failed, viewModel.uiState.value.phase)
        assertTrue(viewModel.uiState.value.error != null)
    }

    @Test
    fun `a source that cannot be reached can be retried`() = runTest {
        github.failure = AppError.Network()
        val viewModel = viewModel()
        viewModel.start(githubApp())
        advanceUntilIdle()
        assertEquals(SuggestionPhase.LoadFailed, viewModel.uiState.value.phase)

        github.failure = null
        viewModel.retryLoad()
        advanceUntilIdle()

        assertEquals(SuggestionPhase.Input, viewModel.uiState.value.phase)
    }

    private fun release(tag: String, vararg names: String) = GithubRelease(
        name = tag,
        assets = names.map { GithubAsset(name = it, downloadUrl = "https://example.test/$tag/$it") },
    )
}
