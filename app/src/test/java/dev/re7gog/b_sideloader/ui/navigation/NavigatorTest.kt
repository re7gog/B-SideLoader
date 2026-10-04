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

    // ---- page or tab -------------------------------------------------------------------------
    //
    // A page slides in from the right and back out to it; a tab switch only shifts sideways.
    // Switching to a tab whose top is an app page looks just like opening that page to Navigation
    // 3, so the navigator says which one happened.

    @Test
    fun `opening a page from the main list or search is not a tab switch`() {
        val navigator = navigator()

        navigator.showAppDetails(7L)
        assertFalse(navigator.isTabSwitch)

        navigator.navigate(SearchRoute)
        navigator.navigate(NewGithubAppRoute(owner = "octocat", repo = "example", name = "Example"))
        assertFalse(navigator.isTabSwitch)
    }

    @Test
    fun `closing a page is not a tab switch`() {
        val navigator = navigator()
        navigator.showAppDetails(7L)

        navigator.goBack()

        assertFalse(navigator.isTabSwitch)
    }

    @Test
    fun `switching tabs, by the bar or by back from a tab root, is a tab switch`() {
        val navigator = navigator()

        navigator.navigate(SearchRoute)
        assertTrue(navigator.isTabSwitch)

        navigator.navigate(NewGithubAppRoute(owner = "octocat", repo = "example", name = "Example"))
        navigator.navigate(AppsRoute)
        assertTrue(navigator.isTabSwitch)

        navigator.navigate(SettingsRoute)
        navigator.goBack()
        assertTrue(navigator.isTabSwitch)
    }
}
