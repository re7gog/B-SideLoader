package dev.re7gog.b_sideloader.ui.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey

/** Which way the last navigation moved, so the display can animate along the same axis. */
enum class NavDirection { Forward, Backward }

/**
 * The only thing allowed to mutate [NavigationState].
 *
 * Screens receive lambdas that call these methods; they never touch a back stack directly. That
 * keeps navigation unidirectional — events in, state out — and makes the whole policy (what "back"
 * means, what a top-level switch does) reviewable in one small file instead of spread across every
 * `navigate(...) { popUpTo(...) }` call site.
 */
@Stable
class Navigator(val state: NavigationState) {

    /**
     * Which way the most recent navigation went.
     *
     * The display cannot work this out for itself. Navigation 3 infers "is this a pop?" by
     * comparing the old and new back stacks, and every switch between two sibling tabs replaces
     * one entry with another — same length, one differing element — which it can only read as a
     * forward move. That is right going Apps -> Search -> Settings and backwards going the other
     * way. Only the navigator knows the tabs have an order, so it is the navigator that records
     * the direction.
     */
    var direction: NavDirection by mutableStateOf(NavDirection.Forward)
        private set

    /** Pushes a destination, or switches tab when [route] is a top-level one. */
    fun navigate(route: NavKey) {
        if (route in state.backStacks.keys) {
            direction = directionBetween(state.topLevelRoute, route)
            state.topLevelRoute = route
        } else {
            direction = NavDirection.Forward
            state.currentStack.add(route)
        }
    }

    /**
     * Pops the current destination.
     *
     * At the root of a non-start tab this returns to the start tab rather than popping an empty
     * stack, which is what "exit through home" means: back always walks towards the apps list, and
     * only then leaves the app.
     */
    fun goBack() {
        direction = NavDirection.Backward
        if (state.isAtTabRoot) {
            if (state.topLevelRoute != state.startRoute) state.topLevelRoute = state.startRoute
            return
        }
        state.currentStack.removeAt(state.currentStack.lastIndex)
    }

    /** Tabs move along one axis: to a later tab is forward, to an earlier one is backward. */
    private fun directionBetween(from: NavKey, to: NavKey): NavDirection {
        val fromIndex = state.topLevelRoutes.indexOf(from)
        val toIndex = state.topLevelRoutes.indexOf(to)
        return if (toIndex < fromIndex) NavDirection.Backward else NavDirection.Forward
    }

    /**
     * Opens one app's details, replacing whatever details are already open.
     *
     * On a wide screen the list stays visible next to the detail pane, so the user can pick a
     * second app without going back first. Pushing there would grow the stack to
     * `[Apps, appA, appB]`, which no longer looks like a list next to a detail and would collapse
     * the layout back to a single pane. On a phone the top of the stack is never a details screen
     * when a row is tapped, so this behaves exactly like a push.
     */
    fun showAppDetails(appId: Long) {
        direction = NavDirection.Forward
        val appsStack = state.backStacks.getValue(AppsRoute)
        while (appsStack.size > 1 && appsStack.last() is SavedAppRoute) {
            appsStack.removeAt(appsStack.lastIndex)
        }
        appsStack.add(SavedAppRoute(appId))
        state.topLevelRoute = AppsRoute
    }

    /**
     * Returns to the apps list, discarding everything stacked on top of it.
     *
     * Used after a successful install started from search: the app the user just installed is now
     * in the list, and dropping them back into the search results they came from would be a dead
     * end.
     */
    fun goToAppsList() {
        // Unwinding to the list, so it should read as going back even though nothing was popped
        // from the stack the user is looking at.
        direction = NavDirection.Backward
        val appsStack = state.backStacks.getValue(AppsRoute)
        while (appsStack.size > 1) {
            appsStack.removeAt(appsStack.lastIndex)
        }
        // Clear whatever the current tab had stacked so returning to it later starts fresh.
        val current = state.currentStack
        while (current.size > 1) {
            current.removeAt(current.lastIndex)
        }
        state.topLevelRoute = AppsRoute
    }
}
