package dev.re7gog.b_sideloader.ui.feature.apps

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.domain.model.AppSourceKind
import dev.re7gog.b_sideloader.ui.common.text.UiText
import dev.re7gog.b_sideloader.ui.theme.BSideLoaderTheme
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Behaviour of the apps list.
 *
 * The screen takes a plain [AppsListUiState] and callbacks, so every case here is a literal — no
 * ViewModel, no database, no `PackageManager`. That is what makes a UI test about the UI.
 */
@RunWith(AndroidJUnit4::class)
class AppsListScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val alpha = AppListItemUi(
        id = 1,
        name = "Alpha",
        packageName = "com.alpha",
        subtitle = UiText.of(R.string.by_owner, "octocat"),
        isInstalled = true,
        isSelected = false,
        sourceKind = AppSourceKind.GitHub,
    )
    private val beta = alpha.copy(id = 2, name = "Beta", packageName = "com.beta", isInstalled = false)

    @Test
    fun showsEmptyStateWhenThereAreNoApps() {
        setContent(AppsListUiState(isLoading = false))

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.no_apps_title))
            .assertIsDisplayed()
    }

    @Test
    fun listsEveryTrackedAppWithItsSubtitle() {
        setContent(AppsListUiState(apps = persistentListOf(alpha, beta), isLoading = false))

        composeRule.onNodeWithText("Alpha").assertIsDisplayed()
        composeRule.onNodeWithText("Beta").assertIsDisplayed()
        composeRule.onNodeWithText("octocat", substring = true).assertIsDisplayed()
    }

    @Test
    fun tappingAnAppOpensIt() {
        var opened: Long? = null
        setContent(
            uiState = AppsListUiState(apps = persistentListOf(alpha), isLoading = false),
            onAppClick = { opened = it },
        )

        composeRule.onNodeWithText("Alpha").performClick()

        assertEquals(1L, opened)
    }

    @Test
    fun longPressingAnAppStartsSelectionInsteadOfOpeningIt() {
        var opened: Long? = null
        var longPressed: Long? = null
        setContent(
            uiState = AppsListUiState(apps = persistentListOf(alpha), isLoading = false),
            onAppClick = { opened = it },
            onLongPress = { longPressed = it },
        )

        composeRule.onNode(hasText("Alpha")).performTouchInput { longClick() }

        assertEquals(1L, longPressed)
        assertNull(opened)
    }

    @Test
    fun selectionModeShowsTheCount() {
        setContent(
            AppsListUiState(
                apps = persistentListOf(alpha.copy(isSelected = true), beta),
                isLoading = false,
                selectedCount = 1,
            )
        )

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.selection_count, 1))
            .assertIsDisplayed()
    }

    /** Selecting must not navigate — the bug multi-select lists usually ship with. */
    @Test
    fun tappingWhileSelectingTogglesRatherThanOpens() {
        var opened: Long? = null
        var toggled: Long? = null
        setContent(
            uiState = AppsListUiState(
                apps = persistentListOf(alpha.copy(isSelected = true)),
                isLoading = false,
                selectedCount = 1,
            ),
            onAppClick = { opened = it },
            onToggleSelection = { toggled = it },
        )

        composeRule.onNodeWithText("Alpha").performClick()

        assertEquals(1L, toggled)
        assertNull(opened)
    }

    /** The per-row button only exists for a row that actually has something newer waiting. */
    @Test
    fun showsAnUpdateButtonOnlyForAppsWithAnUpdate() {
        var updated: Long? = null
        setContent(
            uiState = AppsListUiState(
                apps = persistentListOf(alpha.copy(updateState = AppUpdateState.Available), beta),
                isLoading = false,
            ),
            onUpdateApp = { updated = it },
        )

        composeRule
            .onAllNodesWithContentDescription(
                composeRule.activity.getString(R.string.cd_update_app, "Alpha")
            )
            .assertCountEquals(1)
        composeRule
            .onAllNodesWithContentDescription(
                composeRule.activity.getString(R.string.cd_update_app, "Beta")
            )
            .assertCountEquals(0)

        composeRule
            .onNodeWithContentDescription(
                composeRule.activity.getString(R.string.cd_update_app, "Alpha")
            )
            .performClick()

        assertEquals(1L, updated)
    }

    @Test
    fun updateAllIsOfferedWhenSomethingCanBeUpdated() {
        var updatedAll = false
        setContent(
            uiState = AppsListUiState(
                apps = persistentListOf(alpha.copy(updateState = AppUpdateState.Available)),
                isLoading = false,
            ),
            onUpdateAll = { updatedAll = true },
        )

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.update_all, 1))
            .performClick()

        assertTrue(updatedAll)
    }

    /** Selecting takes the row over; an update button there would be a mis-tap waiting to happen. */
    @Test
    fun updateAffordancesDisappearInSelectionMode() {
        setContent(
            AppsListUiState(
                apps = persistentListOf(
                    alpha.copy(updateState = AppUpdateState.Available, isSelected = true),
                ),
                isLoading = false,
                selectedCount = 1,
            )
        )

        composeRule
            .onAllNodesWithText(composeRule.activity.getString(R.string.update_all, 1))
            .assertCountEquals(0)
    }

    @Test
    fun theLongPressHintCanBeDismissed() {
        var dismissed = false
        setContent(
            uiState = AppsListUiState(
                apps = persistentListOf(alpha, beta),
                isLoading = false,
                showLongPressHint = true,
            ),
            onDismissLongPressHint = { dismissed = true },
        )

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.hint_long_press))
            .assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(composeRule.activity.getString(R.string.hint_dismiss))
            .performClick()

        assertTrue(dismissed)
    }

    private fun setContent(
        uiState: AppsListUiState,
        onAppClick: (Long) -> Unit = {},
        onToggleSelection: (Long) -> Unit = {},
        onLongPress: (Long) -> Unit = {},
        onUpdateApp: (Long) -> Unit = {},
        onUpdateAll: () -> Unit = {},
        onDismissLongPressHint: () -> Unit = {},
    ) {
        composeRule.setContent {
            BSideLoaderTheme {
                AppsListScreen(
                    uiState = uiState,
                    snackbarHostState = remember { SnackbarHostState() },
                    onAppClick = onAppClick,
                    onToggleSelection = onToggleSelection,
                    onLongPress = onLongPress,
                    onClearSelection = {},
                    onSelectAll = {},
                    onRemoveSelected = {},
                    onUninstallSelected = {},
                    onRefresh = {},
                    onUpdateApp = onUpdateApp,
                    onUpdateAll = onUpdateAll,
                    onDismissLongPressHint = onDismissLongPressHint,
                )
            }
        }
    }
}
