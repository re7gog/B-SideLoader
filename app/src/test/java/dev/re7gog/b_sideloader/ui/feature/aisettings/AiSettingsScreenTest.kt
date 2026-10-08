package dev.re7gog.b_sideloader.ui.feature.aisettings

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.domain.model.AiMode
import dev.re7gog.b_sideloader.domain.model.AiProvider
import dev.re7gog.b_sideloader.domain.model.AiSettings
import dev.re7gog.b_sideloader.testing.FakeLanguageModelGateway
import dev.re7gog.b_sideloader.testing.FakeSecretsRepository
import dev.re7gog.b_sideloader.testing.FakeSettingsRepository
import dev.re7gog.b_sideloader.ui.theme.BSideLoaderTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The AI settings page through the real [AiSettingsViewModel] over fakes. */
@RunWith(AndroidJUnit4::class)
class AiSettingsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val settings = FakeSettingsRepository()
    private val secrets = FakeSecretsRepository()

    @Test
    fun choosingAnApiKeyShowsTheProviderFieldsAndStoresTheKey() {
        setContent()
        composeRule.onNodeWithText(string(R.string.ai_settings_api_key)).assertDoesNotExist()

        composeRule.onNodeWithText(string(R.string.ai_mode_api_key)).performClick()
        composeRule.waitForIdle()

        assertEquals(AiMode.ApiKey, current().mode)
        // The OpenAI-compatible provider is the default, with its server address field.
        scrollTo(hasText(string(R.string.ai_settings_base_url)))
        composeRule.onNodeWithText(string(R.string.ai_settings_base_url)).assertExists()
        val keyField = hasSetTextAction() and hasText(string(R.string.ai_settings_api_key))
        scrollTo(keyField)
        composeRule.onNode(keyField).performTextInput("sk-test")
        composeRule.waitForIdle()

        assertEquals("sk-test", secrets.aiKeys[AiProvider.OpenAi])
    }

    @Test
    fun turningAiOffIsStored() {
        setContent()

        composeRule.onNodeWithText(string(R.string.ai_mode_off)).performClick()
        composeRule.waitForIdle()

        assertEquals(AiMode.Off, current().mode)
    }

    private fun scrollTo(matcher: SemanticsMatcher) {
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(matcher)
    }

    private fun current(): AiSettings = runBlocking { settings.current().ai }

    private fun setContent() {
        val viewModel = AiSettingsViewModel(settings, secrets, FakeLanguageModelGateway())
        composeRule.setContent {
            BSideLoaderTheme {
                AiSettingsScreen(onBack = {}, viewModel = viewModel)
            }
        }
        composeRule.waitForIdle()
    }

    private fun string(@StringRes id: Int, vararg args: Any): String =
        composeRule.activity.getString(id, *args)
}
