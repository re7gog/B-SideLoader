package dev.re7gog.b_sideloader.ui.feature.manualinstall

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.domain.installer.InstallScheduler
import dev.re7gog.b_sideloader.domain.model.InstallOutcome
import dev.re7gog.b_sideloader.domain.model.LocalApk
import dev.re7gog.b_sideloader.testing.FakeApkStagingArea
import dev.re7gog.b_sideloader.testing.FakeInstallerGateway
import dev.re7gog.b_sideloader.testing.FakeSettingsRepository
import dev.re7gog.b_sideloader.ui.theme.BSideLoaderTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * The manual install flow from the button to the snackbar: the system file picker (played by
 * Robolectric), the real [ManualInstallViewModel], and fakes for staging and installing.
 */
@RunWith(AndroidJUnit4::class)
class ManualInstallPaneTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val apk = LocalApk(
        path = "/cache/manual_install/picked.apk",
        packageName = "com.example",
        label = "Example",
        versionName = "2.0",
        versionCode = 20,
        sizeBytes = 4_000_000,
    )
    private val stagingArea = FakeApkStagingArea(staged = apk)
    private val installer = FakeInstallerGateway(outcome = InstallOutcome.Success("com.example"))

    @Test
    fun choosingAFileOpensTheSystemPickerForApks() {
        setContent()

        composeRule.onNodeWithText(string(R.string.choose_apk_file)).performClick()

        val picker = checkNotNull(shadowOf(composeRule.activity).nextStartedActivityForResult).intent
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, picker.action)
        assertTrue(
            picker.getStringArrayExtra(Intent.EXTRA_MIME_TYPES).orEmpty()
                .contains("application/vnd.android.package-archive")
        )
    }

    /** What the archive really is, read from its manifest, before anything is installed. */
    @Test
    fun aPickedApkIsDescribedBeforeItIsInstalled() {
        setContent()

        pickFile()

        composeRule.onNodeWithText("Example").assertIsDisplayed()
        composeRule.onNodeWithText("com.example").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.apk_version_format, "2.0", "20")).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.apk_not_tracked_warning)).assertIsDisplayed()
    }

    @Test
    fun confirmingInstallsItAndSaysSo() {
        setContent()
        pickFile()

        composeRule.onNodeWithText(string(R.string.install)).performClick()

        assertEquals(listOf(apk), installer.installedLocal)
        composeRule.onNodeWithText(string(R.string.installed_app, "Example")).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.choose_apk_file)).assertIsDisplayed()
    }

    /** Android would refuse a downgrade after streaming the whole file; say so up front instead. */
    @Test
    fun aDowngradeIsRefusedBeforeAnythingIsStreamed() {
        stagingArea.staged = apk.copy(installedVersionName = "3.0", installedVersionCode = 30)
        setContent()
        pickFile()

        composeRule.onNodeWithText(string(R.string.apk_downgrade_warning)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.install)).performClick()

        assertTrue(installer.installedLocal.isEmpty())
        composeRule
            .onNodeWithText(string(R.string.error_install_downgrade_versions, "2.0", "3.0"))
            .assertIsDisplayed()
    }

    @Test
    fun cancellingDropsTheStagedCopy() {
        setContent()
        pickFile()

        composeRule.onNodeWithText(string(R.string.cancel)).performClick()
        composeRule.waitForIdle()

        assertEquals(1, stagingArea.cleared)
        assertTrue(installer.installedLocal.isEmpty())
    }

    @Test
    fun aFileThatCannotBeReadIsReported() {
        stagingArea.staged = null // the fake then fails with "nothing staged for <uri>"
        setContent()

        pickFile()

        composeRule.onNodeWithText("nothing staged for $PICKED_URI").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.choose_apk_file)).assertIsDisplayed()
    }

    /** Plays the document picker: open it from the button, then hand a file back. */
    private fun pickFile() {
        composeRule.onNodeWithText(string(R.string.choose_apk_file)).performClick()
        val shadowActivity = shadowOf(composeRule.activity)
        val request = checkNotNull(shadowActivity.nextStartedActivityForResult)
        shadowActivity.receiveResult(
            request.intent,
            Activity.RESULT_OK,
            Intent().setData(Uri.parse(PICKED_URI)),
        )
        composeRule.waitForIdle()
    }

    private fun setContent() {
        val viewModel = ManualInstallViewModel(
            stagingArea,
            installer,
            InstallScheduler(FakeSettingsRepository()),
            CoroutineScope(SupervisorJob()),
        )
        composeRule.setContent {
            BSideLoaderTheme {
                val snackbarHostState = remember { SnackbarHostState() }
                Box {
                    ManualInstallPane(snackbarHostState = snackbarHostState, viewModel = viewModel)
                    SnackbarHost(snackbarHostState)
                }
            }
        }
    }

    private fun string(@StringRes id: Int, vararg args: Any): String =
        composeRule.activity.getString(id, *args)

    private companion object {
        const val PICKED_URI = "content://com.example.documents/document/picked.apk"
    }
}
