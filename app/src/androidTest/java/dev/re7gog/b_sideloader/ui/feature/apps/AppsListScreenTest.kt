package dev.re7gog.b_sideloader.ui.feature.apps

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
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

    private fun setContent(
        uiState: AppsListUiState,
        onAppClick: (Long) -> Unit = {},
        onToggleSelection: (Long) -> Unit = {},
        onLongPress: (Long) -> Unit = {},
    ) {
        composeRule.setContent {
            BSideLoaderTheme {
                AppsListScreen(
                    uiState = uiState,
                    onAppClick = onAppClick,
                    onToggleSelection = onToggleSelection,
                    onLongPress = onLongPress,
                    onClearSelection = {},
                    onSelectAll = {},
                    onRemoveSelected = {},
                    onUninstallSelected = {},
                )
            }
        }
    }
}
