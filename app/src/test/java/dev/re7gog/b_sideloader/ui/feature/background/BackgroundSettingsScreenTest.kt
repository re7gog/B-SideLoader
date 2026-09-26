package dev.re7gog.b_sideloader.ui.feature.background

import android.app.Application
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.core.log.NoopLogger
import dev.re7gog.b_sideloader.data.background.AndroidBackgroundRestrictions
import dev.re7gog.b_sideloader.domain.model.BackgroundMode
import dev.re7gog.b_sideloader.domain.usecase.ObserveBackgroundHealthUseCase
import dev.re7gog.b_sideloader.domain.usecase.SyncBackgroundWorkUseCase
import dev.re7gog.b_sideloader.testing.FakeBackgroundWorkScheduler
import dev.re7gog.b_sideloader.testing.FakeDeviceInfo
import dev.re7gog.b_sideloader.testing.FakeSettingsRepository
import dev.re7gog.b_sideloader.ui.theme.BSideLoaderTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * The background-reliability checklist over the real [AndroidBackgroundRestrictions], with the
 * device state set through Robolectric's system-service shadows: what the screen reports, and
 * which system screen each fix actually opens.
 */
@RunWith(AndroidJUnit4::class)
class BackgroundSettingsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val application: Application = ApplicationProvider.getApplicationContext()
    private val settings = FakeSettingsRepository()
    private val scheduler = FakeBackgroundWorkScheduler()

    @Test
    fun aStockPhoneWithTheExemptionIsHealthy() {
        exemptFromBatteryOptimizations()
        setContent(manufacturer = "google")

        composeRule.onNodeWithText(string(R.string.background_summary_ok)).assertIsDisplayed()
        composeRule
            .onNodeWithText(string(R.string.background_summary_device, string(R.string.vendor_generic)))
            .assertIsDisplayed()
    }

    @Test
    fun theBatteryFixOpensTheSystemExemptionPrompt() {
        setContent(manufacturer = "google")
        composeRule.onNodeWithText(string(R.string.background_summary_issues)).assertIsDisplayed()

        composeRule.onNodeWithText(string(R.string.background_battery_action)).performClick()

        val opened = checkNotNull(shadowOf(application).nextStartedActivity)
        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, opened.action)
    }

    /** No autostart screen resolves here, so the fix falls back to the app's own settings page. */
    @Test
    fun withoutAnAutostartScreenTheAppSettingsPageIsOffered() {
        exemptFromBatteryOptimizations()
        setContent(manufacturer = "xiaomi")

        scrollTo(hasText(string(R.string.background_autostart_title)))
        composeRule.onNodeWithText(string(R.string.background_open_app_settings)).performClick()

        val opened = checkNotNull(shadowOf(application).nextStartedActivity)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, opened.action)
    }

    /** On a ROM that kills deferred jobs the one fix that always works is recommended — and applied. */
    @Test
    fun aJobKillingRomIsSwitchedToThePersistentService() {
        exemptFromBatteryOptimizations()
        setContent(manufacturer = "xiaomi")

        scrollTo(hasText(string(R.string.background_persistent_action)))
        composeRule.onNodeWithText(string(R.string.background_persistent_action)).performClick()
        composeRule.waitForIdle()

        assertEquals(BackgroundMode.Persistent, runBlocking { settings.current() }.backgroundMode)
        assertEquals(BackgroundMode.Persistent, scheduler.synced.last().backgroundMode)
        assertFalse(exists(hasText(string(R.string.background_persistent_action))))
    }

    private fun exemptFromBatteryOptimizations() {
        val power = application.getSystemService(Context.POWER_SERVICE) as PowerManager
        shadowOf(power).setIgnoringBatteryOptimizations(application.packageName, true)
    }

    private fun setContent(manufacturer: String) {
        val restrictions = AndroidBackgroundRestrictions(
            application,
            FakeDeviceInfo(manufacturer = manufacturer),
            NoopLogger,
        )
        val viewModel = BackgroundSettingsViewModel(
            observeBackgroundHealth = ObserveBackgroundHealthUseCase(settings, restrictions),
            restrictions = restrictions,
            settingsRepository = settings,
            syncBackgroundWork = SyncBackgroundWorkUseCase(settings, scheduler),
        )
        composeRule.setContent {
            BSideLoaderTheme {
                BackgroundSettingsScreen(onBack = {}, viewModel = viewModel)
            }
        }
        composeRule.waitForIdle()
        // Launching the test activity is itself a started activity; start from a clean slate.
        shadowOf(application).clearNextStartedActivities()
    }

    private fun scrollTo(matcher: SemanticsMatcher) {
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(matcher)
    }

    private fun exists(matcher: SemanticsMatcher): Boolean =
        composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    private fun string(@StringRes id: Int, vararg args: Any): String =
        composeRule.activity.getString(id, *args)
}
