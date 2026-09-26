package dev.re7gog.b_sideloader.ui.feature.settings

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.domain.error.AppError
import dev.re7gog.b_sideloader.domain.error.PrivilegedFailure
import dev.re7gog.b_sideloader.domain.model.AppSettings
import dev.re7gog.b_sideloader.domain.model.BackgroundMode
import dev.re7gog.b_sideloader.domain.model.InstallerMode
import dev.re7gog.b_sideloader.domain.model.PrivilegedAccess
import dev.re7gog.b_sideloader.domain.model.PrivilegedIdentity
import dev.re7gog.b_sideloader.domain.model.TelegramAuthState
import dev.re7gog.b_sideloader.domain.usecase.SyncBackgroundWorkUseCase
import dev.re7gog.b_sideloader.testing.FakeBackgroundWorkScheduler
import dev.re7gog.b_sideloader.testing.FakeInstallerGateway
import dev.re7gog.b_sideloader.testing.FakeSecretsRepository
import dev.re7gog.b_sideloader.testing.FakeSettingsRepository
import dev.re7gog.b_sideloader.testing.FakeTelegramRepository
import dev.re7gog.b_sideloader.ui.theme.BSideLoaderTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Settings through the real [SettingsViewModel] over fakes. The point of most cases is the second
 * half of each toggle: the value is stored *and* background work is re-synced right away, instead
 * of at the next cold start.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val settings = FakeSettingsRepository()
    private val secrets = FakeSecretsRepository()
    private val telegram = FakeTelegramRepository().apply { setAuthState(TelegramAuthState.LoggedOut) }
    private val installer = FakeInstallerGateway()
    private val scheduler = FakeBackgroundWorkScheduler()

    @Test
    fun turningAutoUpdatesOffHidesItsOptionsAndStopsBackgroundWork() {
        setContent()
        composeRule.onNodeWithText(string(R.string.settings_metered)).assertIsDisplayed()

        composeRule.onNodeWithText(string(R.string.settings_autoupdates)).performClick()
        composeRule.waitForIdle()

        assertFalse(exists(hasText(string(R.string.settings_metered))))
        assertFalse(exists(hasText(string(R.string.settings_persistent_service))))
        assertEquals(false, scheduler.synced.last().autoUpdate)
    }

    @Test
    fun thePersistentServiceSwitchStoresTheModeAndReschedules() {
        setContent()

        composeRule.onNodeWithText(string(R.string.settings_persistent_service)).performClick()
        composeRule.waitForIdle()

        assertEquals(BackgroundMode.Persistent, current().backgroundMode)
        assertEquals(BackgroundMode.Persistent, scheduler.synced.last().backgroundMode)
    }

    /** A privileged mode that cannot work is never stored — every install would silently fail. */
    @Test
    fun anUnavailablePrivilegedInstallerIsNotSelected() {
        installer.privilegedAccess =
            PrivilegedAccess.Unavailable(AppError.Privileged(PrivilegedFailure.ServiceNotFound))
        setContent()

        chooseInstaller(R.string.installer_shizuku)

        assertEquals(InstallerMode.Session, current().installerMode)
        composeRule.onNodeWithText(string(R.string.error_privileged_not_found)).assertIsDisplayed()
    }

    @Test
    fun anAvailablePrivilegedInstallerIsSelectedAndConfirmed() {
        installer.privilegedAccess = PrivilegedAccess.Granted(PrivilegedIdentity.Adb)
        setContent()

        chooseInstaller(R.string.installer_shizuku)

        assertEquals(InstallerMode.Shizuku, current().installerMode)
        composeRule
            .onNodeWithText(string(R.string.installer_enabled, InstallerMode.Shizuku.name))
            .assertIsDisplayed()
    }

    @Test
    fun theBackgroundReliabilityRowOpensItsScreen() {
        var opened = false
        setContent(onBackgroundSettingsClick = { opened = true })

        scrollTo(hasText(string(R.string.settings_background_reliability)))
        composeRule.onNodeWithText(string(R.string.settings_background_reliability)).performClick()

        assertTrue(opened)
    }

    @Test
    fun signedOutOfTelegramTheRowStartsTheLogin() {
        var loginOpened = false
        setContent(onTelegramLoginClick = { loginOpened = true })

        scrollTo(hasText(string(R.string.telegram_login_subtitle)))
        composeRule.onNodeWithText(string(R.string.telegram_login_subtitle)).performClick()

        assertTrue(loginOpened)
    }

    @Test
    fun aSignedInAccountCanBeLoggedOut() {
        telegram.setAuthState(TelegramAuthState.Ready)
        setContent()

        scrollTo(hasText("Tester"))
        composeRule.onNodeWithText("@tester").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.log_out)).performClick()

        scrollTo(hasText(string(R.string.telegram_login_subtitle)))
        composeRule.onNodeWithText(string(R.string.logging_out_telegram)).assertIsDisplayed()
    }

    @Test
    fun aTypedGithubTokenIsStoredAsASecret() {
        setContent()

        scrollTo(hasText(string(R.string.settings_github_token_label)))
        composeRule.onNodeWithText(string(R.string.settings_github_token_label)).performTextInput("ghp_abc")
        composeRule.waitForIdle()

        assertEquals("ghp_abc", secrets.githubToken)
    }

    private fun chooseInstaller(@StringRes label: Int) {
        composeRule.onNodeWithText(string(R.string.settings_installation_method)).performClick()
        composeRule.onNodeWithText(string(label)).performClick()
        composeRule.waitForIdle()
    }

    private fun current(): AppSettings = runBlocking { settings.current() }

    private fun setContent(
        onTelegramLoginClick: () -> Unit = {},
        onBackgroundSettingsClick: () -> Unit = {},
    ) {
        val viewModel = SettingsViewModel(
            settingsRepository = settings,
            secretsRepository = secrets,
            telegramRepository = telegram,
            installerGateway = installer,
            syncBackgroundWork = SyncBackgroundWorkUseCase(settings, scheduler),
        )
        composeRule.setContent {
            BSideLoaderTheme {
                SettingsScreen(
                    onTelegramLoginClick = onTelegramLoginClick,
                    onBackgroundSettingsClick = onBackgroundSettingsClick,
                    viewModel = viewModel,
                )
            }
        }
    }

    private fun scrollTo(matcher: SemanticsMatcher) {
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(matcher)
    }

    private fun exists(matcher: SemanticsMatcher): Boolean =
        composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    private fun string(@StringRes id: Int, vararg args: Any): String =
        composeRule.activity.getString(id, *args)
}
