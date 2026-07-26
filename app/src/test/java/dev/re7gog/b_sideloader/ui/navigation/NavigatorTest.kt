package dev.re7gog.b_sideloader.ui.navigation

import androidx.compose.runtime.mutableIntStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Navigation policy, tested without Compose or an Activity.
 *
 * This is the point of Navigation 3 putting the back stack in your own state: what "back" means,
 * and what a top-level switch does, is now plain Kotlin that can be asserted directly instead of
 * behaviour hidden inside a `NavController` and a pile of `navOptions`.
 */
class NavigatorTest {

    private fun navigator(): Navigator {
        val topLevel = listOf<NavKey>(AppsRoute, SearchRoute, SettingsRoute)
        val state = NavigationState(
            topLevelRoutes = topLevel,
            topLevelIndex = mutableIntStateOf(0),
            backStacks = topLevel.associateWith { NavBackStack(it) },
        )
        return Navigator(state)
    }

    @Test
    fun `starts on the apps list`() {
        val navigator = navigator()

        assertEquals(AppsRoute, navigator.state.topLevelRoute)
        assertEquals(AppsRoute, navigator.state.currentRoute)
        assertTrue(navigator.state.isAtTabRoot)
    }

    @Test
    fun `navigating to a top level route switches tab instead of pushing`() {
        val navigator = navigator()

        navigator.navigate(SearchRoute)

        assertEquals(SearchRoute, navigator.state.topLevelRoute)
        assertEquals(1, navigator.state.currentStack.size)
    }

    @Test
    fun `navigating to a child pushes onto the current tab`() {
        val navigator = navigator()
        navigator.navigate(SearchRoute)

        navigator.navigate(NewGithubAppRoute(owner = "octocat", repo = "example", name = "Example"))

        assertEquals(2, navigator.state.currentStack.size)
        assertFalse(navigator.state.isAtTabRoot)
    }

    @Test
    fun `each tab keeps its own stack`() {
        val navigator = navigator()
        navigator.navigate(SavedAppRoute(1))
        navigator.navigate(SearchRoute)

        assertEquals(1, navigator.state.currentStack.size)
        assertEquals(2, navigator.state.backStacks.getValue(AppsRoute).size)
    }

    @Test
    fun `back pops the current tab's stack`() {
        val navigator = navigator()
        navigator.navigate(SavedAppRoute(1))

        navigator.goBack()

        assertEquals(AppsRoute, navigator.state.currentRoute)
    }

    /**
     * "Exit through home": at the root of a non-start tab, back returns to the apps list rather
     * than leaving the app, so the user always exits from one predictable place.
     */
    @Test
    fun `back at the root of another tab returns to the start route`() {
        val navigator = navigator()
        navigator.navigate(SettingsRoute)

        navigator.goBack()

        assertEquals(AppsRoute, navigator.state.topLevelRoute)
    }

    @Test
    fun `back at the start route root is a no-op so the system can exit`() {
        val navigator = navigator()

        navigator.goBack()

        assertEquals(AppsRoute, navigator.state.topLevelRoute)
        assertEquals(1, navigator.state.currentStack.size)
    }

    /**
     * After installing an app found in search, returning to the search results is a dead end —
     * the app is now in the list, which is where the user is sent.
     */
    @Test
    fun `goToAppsList clears both stacks and switches tab`() {
        val navigator = navigator()
        navigator.navigate(SearchRoute)
        navigator.navigate(NewGithubAppRoute(owner = "octocat", repo = "example", name = "Example"))

        navigator.goToAppsList()

        assertEquals(AppsRoute, navigator.state.topLevelRoute)
        assertEquals(1, navigator.state.currentStack.size)
        assertEquals(1, navigator.state.backStacks.getValue(SearchRoute).size)
    }

    @Test
    fun `only the start route is composed while on it`() {
        val navigator = navigator()

        assertEquals(listOf(AppsRoute), navigator.state.stacksInUse)
    }

    @Test
    fun `the start route stays composed alongside another tab`() {
        val navigator = navigator()
        navigator.navigate(SettingsRoute)

        assertEquals(listOf(AppsRoute, SettingsRoute), navigator.state.stacksInUse)
    }

    // ---- transition direction ----------------------------------------------------------------
    //
    // Navigation 3 decides "push or pop?" by comparing back-stack shapes, and switching between
    // two sibling tabs swaps one entry for another in both directions — indistinguishable to it,
    // so it calls every such move a push. Only the navigator knows the tabs are ordered, so these
    // tests pin down the direction the animation follows.

    @Test
    fun `moving to a later tab is forward`() {
        val navigator = navigator()

        navigator.navigate(SettingsRoute)

        assertEquals(NavDirection.Forward, navigator.direction)
    }

    /** The regression: Settings -> Search moves left in the bar and must animate that way. */
    @Test
    fun `moving to an earlier tab is backward`() {
        val navigator = navigator()
        navigator.navigate(SettingsRoute)

        navigator.navigate(SearchRoute)

        assertEquals(NavDirection.Backward, navigator.direction)
    }

    @Test
    fun `pushing a child route is forward`() {
        val navigator = navigator()
        navigator.navigate(SettingsRoute)

        navigator.navigate(TelegramLoginRoute)

        assertEquals(NavDirection.Forward, navigator.direction)
    }

    @Test
    fun `going back is backward`() {
        val navigator = navigator()
        navigator.navigate(SearchRoute)
        navigator.navigate(TelegramLoginRoute)

        navigator.goBack()

        assertEquals(NavDirection.Backward, navigator.direction)
    }

    /** Leaving a non-start tab through back walks towards the apps list, so it reads as backward. */
    @Test
    fun `leaving a tab root through back is backward`() {
        val navigator = navigator()
        navigator.navigate(SettingsRoute)

        navigator.goBack()

        assertEquals(AppsRoute, navigator.state.topLevelRoute)
        assertEquals(NavDirection.Backward, navigator.direction)
    }

    @Test
    fun `opening app details is forward`() {
        val navigator = navigator()
        navigator.navigate(SettingsRoute)

        navigator.showAppDetails(7L)

        assertEquals(NavDirection.Forward, navigator.direction)
    }

    /** Unwinding to the list after an install is a retreat, even though nothing was popped here. */
    @Test
    fun `returning to the apps list is backward`() {
        val navigator = navigator()
        navigator.navigate(SearchRoute)
        navigator.navigate(NewGithubAppRoute(owner = "octocat", repo = "example", name = "Example"))

        navigator.goToAppsList()

        assertEquals(NavDirection.Backward, navigator.direction)
    }
}
