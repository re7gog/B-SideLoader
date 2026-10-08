package dev.re7gog.b_sideloader.ui.feature.filtersuggestion

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.domain.model.AiAvailability
import dev.re7gog.b_sideloader.domain.model.AiBackend
import dev.re7gog.b_sideloader.domain.model.AiUnavailableReason
import dev.re7gog.b_sideloader.domain.model.FilterMode
import dev.re7gog.b_sideloader.domain.model.FilterRule
import dev.re7gog.b_sideloader.domain.suggestion.FilterProposal
import dev.re7gog.b_sideloader.domain.suggestion.PreviewRow
import dev.re7gog.b_sideloader.ui.common.text.UiText
import dev.re7gog.b_sideloader.ui.theme.BSideLoaderTheme
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The sheet's stateless content: what each phase shows and which buttons it lets through. */
@RunWith(AndroidJUnit4::class)
class FilterSuggestionContentTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val input = FilterSuggestionUiState(
        phase = SuggestionPhase.Input,
        ai = AiAvailability.Available(AiBackend.OnDevice),
        exampleGroupLabel = "v2.0",
        exampleFiles = persistentListOf(
            ExampleFileUi("app-foss-arm64-v8a.apk", runsOnDevice = true),
            ExampleFileUi("app-foss-x86_64.apk", runsOnDevice = false),
        ),
    )

    @Test
    fun suggestStaysDisabledUntilThereIsSomethingToGoOn() {
        var picked: Int? = null
        setContent(input, onExampleSelect = { picked = it })

        composeRule.onNodeWithText(string(R.string.suggest_action)).assertIsNotEnabled()
        composeRule.onNodeWithText("app-foss-arm64-v8a.apk").performClick()
        assertEquals(0, picked)

        setContent(input.copy(selectedExample = 0))
        composeRule.onNodeWithText(string(R.string.suggest_action)).assertIsEnabled()
    }

    @Test
    fun withAiOffTheRequestFieldSaysWhyAndIsDisabled() {
        setContent(input.copy(ai = AiAvailability.Unavailable(AiUnavailableReason.Disabled)))

        composeRule.onNodeWithText(string(R.string.suggest_ai_off)).assertExists()
        composeRule.onNodeWithText(string(R.string.suggest_intro_example_only)).assertExists()
    }

    @Test
    fun aResultShowsTheChangesAndWhatWouldBeInstalled() {
        var applied = false
        val proposal = FilterProposal(FilterMode.Words, FilterRule(include = "foss"), FilterRule.None, false)
        val result = input.copy(
            phase = SuggestionPhase.Result,
            suggestion = SuggestionUi(
                proposal = proposal,
                changes = persistentListOf(
                    FilterChangeUi(UiText.of(R.string.apk_must_contain), before = null, after = UiText.Raw("foss")),
                ),
                explanation = "Only the FOSS flavor.",
                backend = AiBackend.OnDevice,
                preview = persistentListOf(
                    PreviewRow("v2.0", false, before = "app-gplay.apk", after = "app-foss.apk", isTarget = true),
                ),
            ),
        )
        setContent(result, onApply = { applied = true })

        composeRule.onNodeWithText("Only the FOSS flavor.").assertExists()
        composeRule.onNodeWithText("foss").assertExists()
        scrollTo(string(R.string.suggest_apply))
        composeRule.onNodeWithText("app-foss.apk").assertExists()
        composeRule.onNodeWithText(string(R.string.suggest_was, "app-gplay.apk")).assertExists()
        composeRule.onNodeWithText(string(R.string.suggest_apply)).performClick()
        assertTrue(applied)
    }

    @Test
    fun aFailureListsWhatWentWrong() {
        setContent(
            input.copy(
                phase = SuggestionPhase.Failed,
                error = UiText.of(R.string.suggest_no_working_filter),
                problems = persistentListOf(UiText.of(R.string.suggest_problem_nothing_matches)),
            ),
        )

        composeRule.onNodeWithText(string(R.string.suggest_no_working_filter)).assertExists()
        composeRule.onNodeWithText("• " + string(R.string.suggest_problem_nothing_matches)).assertExists()
    }

    /** The screen under test, re-rendered when a test sets another state; set once per test. */
    private val shown = mutableStateOf(Shown(input))
    private var composed = false

    private class Shown(
        val state: FilterSuggestionUiState,
        val onExampleSelect: (Int) -> Unit = {},
        val onApply: () -> Unit = {},
    )

    private fun setContent(
        state: FilterSuggestionUiState,
        onExampleSelect: (Int) -> Unit = {},
        onApply: () -> Unit = {},
    ) {
        shown.value = Shown(state, onExampleSelect, onApply)
        if (!composed) {
            composed = true
            composeRule.setContent {
                val current = shown.value
                BSideLoaderTheme {
                    FilterSuggestionContent(
                        uiState = current.state,
                        onExampleSelect = current.onExampleSelect,
                        onRequestChange = {},
                        onSuggest = {},
                        onFeedbackChange = {},
                        onRetry = {},
                        onBackToInput = {},
                        onRetryLoad = {},
                        onApply = current.onApply,
                        onDismiss = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun scrollTo(text: String) {
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
    }

    private fun string(@StringRes id: Int, vararg args: Any): String =
        composeRule.activity.getString(id, *args)
}
