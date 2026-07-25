package dev.re7gog.b_sideloader.ui.navigation

import androidx.compose.runtime.Stable
import androidx.navigation3.runtime.NavKey

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

    /** Pushes a destination, or switches tab when [route] is a top-level one. */
    fun navigate(route: NavKey) {
        if (route in state.backStacks.keys) {
            state.topLevelRoute = route
        } else {
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
        if (state.isAtTabRoot) {
            if (state.topLevelRoute != state.startRoute) state.topLevelRoute = state.startRoute
            return
        }
        state.currentStack.removeAt(state.currentStack.lastIndex)
    }

    /**
     * Returns to the apps list, discarding everything stacked on top of it.
     *
     * Used after a successful install started from search: the app the user just installed is now
     * in the list, and dropping them back into the search results they came from would be a dead
     * end.
     */
    fun goToAppsList() {
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
