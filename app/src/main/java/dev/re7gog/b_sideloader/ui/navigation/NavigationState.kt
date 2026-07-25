package dev.re7gog.b_sideloader.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import kotlinx.collections.immutable.ImmutableList

/**
 * Navigation state: one back stack per top-level destination, plus which one is showing.
 *
 * This is the piece Navigation 3 deliberately does not provide — in Nav3 the back stack is *your*
 * state, which is why this file exists at all and why it is a plain, testable class rather than a
 * `NavController` the UI has to ask questions of.
 *
 * The current tab is stored as an index in `rememberSaveable` rather than as a serialized key: an
 * `Int` needs no custom serializer, and the index is meaningful only against [topLevelRoutes],
 * which is a compile-time constant.
 */
class NavigationState(
    val topLevelRoutes: List<NavKey>,
    private val topLevelIndex: MutableIntState,
    val backStacks: Map<NavKey, NavBackStack<NavKey>>,
) {
    init {
        require(topLevelRoutes.isNotEmpty()) { "At least one top-level route is required" }
    }

    /** The route the user exits the app through. */
    val startRoute: NavKey get() = topLevelRoutes.first()

    var topLevelRoute: NavKey
        get() = topLevelRoutes[topLevelIndex.intValue.coerceIn(topLevelRoutes.indices)]
        set(value) {
            val index = topLevelRoutes.indexOf(value)
            if (index >= 0) topLevelIndex.intValue = index
        }

    /** The stack the user is currently in. */
    val currentStack: NavBackStack<NavKey>
        get() = backStacks.getValue(topLevelRoute)

    /** The destination on screen right now. */
    val currentRoute: NavKey get() = currentStack.last()

    /** True when the current stack is at its root, i.e. back leaves this tab. */
    val isAtTabRoot: Boolean get() = currentStack.size <= 1

    /**
     * Stacks whose entries are currently composed.
     *
     * The start route is always present so the user exits through it, and at most one other tab is
     * kept alive. The tabs that are not listed keep their state — their `SaveableStateHolder` and
     * `ViewModelStore` survive — they are simply not rendered.
     */
    val stacksInUse: List<NavKey>
        get() = if (topLevelRoute == startRoute) {
            listOf(startRoute)
        } else {
            listOf(startRoute, topLevelRoute)
        }

    /**
     * Turns the state into the entries [androidx.navigation3.ui.NavDisplay] renders.
     *
     * Each stack gets its *own* decorators, which is what keeps per-tab state (scroll positions,
     * ViewModels) separate. The `ViewModelStore` decorator is what scopes a `hiltViewModel` to its
     * `NavEntry`, so navigating to two different apps really does create two ViewModels.
     */
    @Composable
    fun toDecoratedEntries(
        entryProvider: (NavKey) -> NavEntry<NavKey>,
    ): List<NavEntry<NavKey>> {
        val decoratedByStack = backStacks.mapValues { (_, stack) ->
            rememberDecoratedNavEntries(
                backStack = stack,
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                entryProvider = entryProvider,
            )
        }
        return stacksInUse.flatMap { decoratedByStack[it].orEmpty() }
    }
}

/** Creates a [NavigationState] that survives configuration changes and process death. */
@Composable
fun rememberNavigationState(
    topLevelRoutes: ImmutableList<NavKey>,
): NavigationState {
    val topLevelIndex = rememberSaveable { mutableIntStateOf(0) }
    val backStacks = topLevelRoutes.associateWith { route -> rememberNavBackStack(route) }
    return remember(topLevelRoutes) {
        NavigationState(
            topLevelRoutes = topLevelRoutes,
            topLevelIndex = topLevelIndex,
            backStacks = backStacks,
        )
    }
}
