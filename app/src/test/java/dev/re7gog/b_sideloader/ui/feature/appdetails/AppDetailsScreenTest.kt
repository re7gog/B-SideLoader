package dev.re7gog.b_sideloader.ui.feature.appdetails

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.domain.model.AppVersion
import dev.re7gog.b_sideloader.domain.model.DownloadRef
import dev.re7gog.b_sideloader.domain.model.FilterMode
import dev.re7gog.b_sideloader.domain.model.InstallProgress
import dev.re7gog.b_sideloader.domain.model.TrackedApp
import dev.re7gog.b_sideloader.domain.model.UpdateCandidate
import dev.re7gog.b_sideloader.domain.model.UpdateStatus
import dev.re7gog.b_sideloader.testing.githubApp
import dev.re7gog.b_sideloader.testing.telegramApp
import dev.re7gog.b_sideloader.ui.theme.BSideLoaderTheme
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The details screen through its stateless overload: every case is a literal [AppDetailsUiState]
 * and a set of callbacks, so what is under test is the screen and nothing behind it.
 */
@RunWith(AndroidJUnit4::class)
class AppDetailsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val candidate = UpdateCandidate(
        version = AppVersion("v2.0"),
        download = DownloadRef.Http("https://example.test/app.apk"),
        fileName = "app-arm64-v8a.apk",
        sizeBytes = 5L * 1024 * 1024,
    )

    private val saved = githubApp(id = 1, name = "Example", version = AppVersion("v1.0"))
    private val unsaved = saved.copy(id = TrackedApp.NEW_APP_ID)

    @Test
    fun showsTheAppsNameOwnerAndInstalledVersion() {
        setContent(state(saved, headline = HeadlineUi.GitHub(owner = "octocat", stars = 42)))

        composeRule.onNodeWithText("Example").assertIsDisplayed()
        composeRule.onNodeWithText("octocat").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.version_label, "v1.0")).assertIsDisplayed()
    }

    /** Opened from search, the first tap both saves and installs — once there is something to install. */
    @Test
    fun anUnsavedAppOffersSaveAndInstallOnlyOnceATargetIsKnown() {
        setContent(state(unsaved, target = null))
        composeRule.onNodeWithText(string(R.string.save_and_install)).assertIsNotEnabled()
    }

    @Test
    fun anUnsavedAppWithATargetCanBeInstalled() {
        var tapped = false
        setContent(state(unsaved, target = candidate), onPrimaryAction = { tapped = true })

        composeRule.onNodeWithText(string(R.string.save_and_install)).assertIsEnabled().performClick()

        assertTrue(tapped)
    }

    @Test
    fun aSavedAppWithANewerReleaseOffersUpdate() {
        setContent(
            state(
                saved,
                isInstalled = true,
                updateStatus = UpdateStatus.UpdateAvailable,
                target = candidate,
            )
        )

        composeRule.onNodeWithText(string(R.string.update)).assertIsEnabled()
    }

    @Test
    fun anUpToDateInstalledAppOffersOpen() {
        setContent(state(saved, isInstalled = true, updateStatus = UpdateStatus.UpToDate))

        composeRule.onNodeWithText(string(R.string.open)).assertIsEnabled()
    }

    /** Unsaved edits win over everything else: the button must not install over them. */
    @Test
    fun unsavedEditsTurnThePrimaryActionIntoSave() {
        setContent(
            state(
                saved,
                isInstalled = true,
                updateStatus = UpdateStatus.UpdateAvailable,
                target = candidate,
                hasUnsavedChanges = true,
            )
        )

        composeRule.onNodeWithText(string(R.string.save_changes)).assertIsEnabled()
    }

    @Test
    fun anInstallInProgressReplacesTheButtonWithItsProgress() {
        setContent(state(saved, target = candidate, install = InstallProgress.Downloading(0.4f)))

        composeRule.onNodeWithText(string(R.string.downloading_percent, 40)).assertIsDisplayed()
        assertFalse(exists(hasText(string(R.string.install))))
    }

    @Test
    fun removingAsksForConfirmationFirst() {
        var deleted = 0
        setContent(state(saved), onDelete = { deleted++ })

        composeRule.onNodeWithText(string(R.string.remove)).performClick()
        composeRule.onNodeWithText(string(R.string.remove_app_message, "Example")).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.cancel)).performClick()
        assertEquals(0, deleted)

        composeRule.onNodeWithText(string(R.string.remove)).performClick()
        confirmDialog()

        assertEquals(1, deleted)
    }

    @Test
    fun renamingReportsTheTrimmedNewName() {
        var renamed: String? = null
        setContent(state(saved), onNameChange = { renamed = it })

        composeRule.onNodeWithContentDescription(string(R.string.cd_edit_name)).performClick()
        composeRule.onNode(hasSetTextAction() and hasText("Example")).performTextReplacement("  Renamed ")
        composeRule.onNodeWithText(string(R.string.save)).performClick()

        assertEquals("Renamed", renamed)
    }

    /** A nameless row in the apps list would be unusable, so a blank name cannot be confirmed. */
    @Test
    fun aBlankNameCannotBeSaved() {
        var renamed: String? = null
        setContent(state(saved), onNameChange = { renamed = it })

        composeRule.onNodeWithContentDescription(string(R.string.cd_edit_name)).performClick()
        composeRule.onNode(hasSetTextAction() and hasText("Example")).performTextReplacement("   ")

        composeRule.onNodeWithText(string(R.string.app_name_required)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.save)).assertIsNotEnabled()
        assertEquals(null, renamed)
    }

    @Test
    fun aGithubAppFiltersOnReleaseNames() {
        setContent(state(saved))

        scrollTo(hasText(string(R.string.release_must_contain)))
        assertFalse(exists(hasText(string(R.string.message_must_contain))))
    }

    @Test
    fun aTelegramAppFiltersOnMessageText() {
        setContent(state(telegramApp(id = 1, name = "Channel"), headline = HeadlineUi.Telegram()))

        composeRule.onNodeWithText(string(R.string.telegram_channel)).assertIsDisplayed()
        scrollTo(hasText(string(R.string.message_must_contain)))
        assertFalse(exists(hasText(string(R.string.release_must_contain))))
    }

    @Test
    fun advancedModeRelabelsEveryFilterAsARegex() {
        setContent(state(githubApp(id = 1, filterMode = FilterMode.Regex)))

        scrollTo(hasText(string(R.string.release_regex_include)))
        scrollTo(hasText(string(R.string.apk_regex_include)))
        assertFalse(exists(hasText(string(R.string.apk_must_contain))))
    }

    @Test
    fun listsTheMatchingApks() {
        setContent(state(saved, candidates = listOf(candidate), target = candidate))

        scrollTo(hasText(candidate.fileName))
    }

    @Test
    fun saysWhetherTheApkListIsStillLoadingOrReallyEmpty() {
        setContent(state(saved, isResolving = true))
        scrollTo(hasText(string(R.string.checking_for_updates)))
    }

    @Test
    fun anEmptyApkListSaysNothingMatched() {
        setContent(state(saved, isResolving = false))
        scrollTo(hasText(string(R.string.no_matching_apks)))
    }

    /** After installing from search, the search results are a dead end; back goes to the list. */
    @Test
    fun backAfterASuccessfulInstallLeavesForTheAppsList() {
        var back = 0
        var finished = 0
        setContent(
            state(saved, installSucceeded = true),
            onBack = { back++ },
            onFinishedFromSearch = { finished++ },
        )

        composeRule.onNodeWithContentDescription(string(R.string.cd_back)).performClick()

        assertEquals(0, back)
        assertEquals(1, finished)
    }

    @Test
    fun backOtherwiseJustGoesBack() {
        var back = 0
        setContent(state(saved), onBack = { back++ })

        composeRule.onNodeWithContentDescription(string(R.string.cd_back)).performClick()

        assertEquals(1, back)
    }

    // ---- helpers ----

    private fun state(
        app: TrackedApp,
        headline: HeadlineUi? = HeadlineUi.GitHub(owner = "octocat"),
        isInstalled: Boolean = false,
        hasUnsavedChanges: Boolean = false,
        updateStatus: UpdateStatus = UpdateStatus.NoCandidate,
        isResolving: Boolean = false,
        candidates: List<UpdateCandidate> = emptyList(),
        target: UpdateCandidate? = null,
        install: InstallProgress? = null,
        installSucceeded: Boolean = false,
    ) = AppDetailsUiState(
        isLoading = false,
        app = app,
        headline = headline,
        isInstalled = isInstalled,
        hasUnsavedChanges = hasUnsavedChanges,
        updateStatus = updateStatus,
        isResolving = isResolving,
        candidates = persistentListOf(*candidates.toTypedArray()),
        target = target,
        install = install,
        installSucceeded = installSucceeded,
    )

    private fun setContent(
        uiState: AppDetailsUiState,
        onBack: () -> Unit = {},
        onFinishedFromSearch: () -> Unit = {},
        onPrimaryAction: () -> Unit = {},
        onDelete: () -> Unit = {},
        onNameChange: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            BSideLoaderTheme {
                AppDetailsScreen(
                    uiState = uiState,
                    snackbarHostState = remember { SnackbarHostState() },
                    onBack = onBack,
                    onFinishedFromSearch = onFinishedFromSearch,
                    onPrimaryAction = onPrimaryAction,
                    onUninstall = {},
                    onDelete = onDelete,
                    onNameChange = onNameChange,
                    onAutoUpdateChange = {},
                    onFilterModeChange = {},
                    onAssetIncludeChange = {},
                    onAssetExcludeChange = {},
                    onReleaseIncludeChange = {},
                    onReleaseExcludeChange = {},
                    onPrereleasesChange = {},
                    onMessageIncludeChange = {},
                    onMessageExcludeChange = {},
                    downloadPhoto = { null },
                )
            }
        }
    }

    /** The dialog's confirm button, as opposed to the Remove button on the page behind it. */
    private fun confirmDialog() =
        composeRule.onNode(hasText(string(R.string.remove)) and !hasScrollAncestor()).performClick()

    private fun hasScrollAncestor() = hasAnyAncestor(hasScrollToNodeAction())

    private fun scrollTo(matcher: SemanticsMatcher) {
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(matcher)
        composeRule.onNode(matcher).assertIsDisplayed()
    }

    private fun exists(matcher: SemanticsMatcher): Boolean =
        composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    private fun string(@StringRes id: Int, vararg args: Any): String =
        composeRule.activity.getString(id, *args)
}
