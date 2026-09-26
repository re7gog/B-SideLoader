package dev.re7gog.b_sideloader.ui.feature.telegramlogin

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.domain.model.TelegramAuthState
import dev.re7gog.b_sideloader.testing.FakeTelegramRepository
import dev.re7gog.b_sideloader.ui.theme.BSideLoaderTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Sign-in, driven by the real [TelegramLoginViewModel] over a fake repository standing in for
 * TDLib: the screen follows whatever step the repository reports, and sends what the user typed
 * in the form TDLib expects.
 */
@RunWith(AndroidJUnit4::class)
class TelegramLoginScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val telegram = FakeTelegramRepository().apply {
        setAuthState(TelegramAuthState.WaitingForPhoneNumber)
    }
    private var signedIn = 0
    private var exited = 0

    /** TDLib wants `+<digits>`; people type spaces, dashes, brackets and `00`. */
    @Test
    fun aPhoneNumberIsSentInTheFormTdlibRequires() {
        setContent()

        composeRule.onNodeWithText(string(R.string.auth_phone_placeholder)).performTextInput("00 7 (912) 345-67-89")
        composeRule.onNodeWithText(string(R.string.auth_continue)).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("+79123456789"), telegram.sentPhoneNumbers)
    }

    @Test
    fun nothingCanBeSubmittedBeforeSomethingIsTyped() {
        setContent()

        composeRule.onNodeWithText(string(R.string.auth_continue)).assertIsNotEnabled()
    }

    @Test
    fun theScreenFollowsTheStepTdlibAsksFor() {
        setContent()

        telegram.setAuthState(TelegramAuthState.WaitingForCode)
        composeRule.onNodeWithText(string(R.string.auth_code_title)).assertIsDisplayed()

        telegram.setAuthState(TelegramAuthState.WaitingForPassword)
        composeRule.onNodeWithText(string(R.string.auth_password_title)).assertIsDisplayed()
    }

    @Test
    fun theCodeIsSentTrimmed() {
        telegram.setAuthState(TelegramAuthState.WaitingForCode)
        setContent()

        composeRule.onNodeWithText(string(R.string.auth_code_placeholder)).performTextInput(" 12345 ")
        composeRule.onNodeWithText(string(R.string.auth_continue)).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("12345"), telegram.sentCodes)
    }

    /** A rejected code must be visible, and the form usable again for a second try. */
    @Test
    fun anErrorFromTdlibIsShownAndTheFormUnlocks() {
        telegram.setAuthState(TelegramAuthState.WaitingForCode)
        setContent()
        composeRule.onNodeWithText(string(R.string.auth_code_placeholder)).performTextInput("00000")
        composeRule.onNodeWithText(string(R.string.auth_continue)).performClick()

        telegram.emitAuthError("PHONE_CODE_INVALID")

        composeRule.onNodeWithText("PHONE_CODE_INVALID").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.auth_continue)).assertIsEnabled()
    }

    @Test
    fun aWrongNumberCanBeCorrectedFromTheCodeStep() {
        telegram.setAuthState(TelegramAuthState.WaitingForCode)
        setContent()

        composeRule.onNodeWithText(string(R.string.auth_change_phone)).performClick()

        composeRule.onNodeWithText(string(R.string.auth_phone_title)).assertIsDisplayed()
    }

    /** Back from the code step returns to the phone number instead of abandoning the sign-in. */
    @Test
    fun backFromTheCodeStepGoesToThePhoneStep() {
        telegram.setAuthState(TelegramAuthState.WaitingForCode)
        setContent()

        composeRule.onNodeWithContentDescription(string(R.string.cd_back)).performClick()

        composeRule.onNodeWithText(string(R.string.auth_phone_title)).assertIsDisplayed()
        assertEquals(0, exited)
    }

    @Test
    fun backFromThePhoneStepLeaves() {
        setContent()

        composeRule.onNodeWithContentDescription(string(R.string.cd_back)).performClick()

        assertEquals(1, exited)
    }

    @Test
    fun signingInCompletesTheFlow() {
        setContent()

        telegram.setAuthState(TelegramAuthState.Ready)
        composeRule.waitForIdle()

        assertEquals(1, signedIn)
    }

    private fun setContent() {
        val viewModel = TelegramLoginViewModel(telegram)
        composeRule.setContent {
            BSideLoaderTheme {
                TelegramLoginScreen(
                    onSignedIn = { signedIn++ },
                    onExit = { exited++ },
                    viewModel = viewModel,
                )
            }
        }
    }

    private fun string(@StringRes id: Int): String = composeRule.activity.getString(id)
}
