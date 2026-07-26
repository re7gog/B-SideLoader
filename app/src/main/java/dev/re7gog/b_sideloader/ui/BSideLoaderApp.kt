package dev.re7gog.b_sideloader.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.ui.common.component.EmptyState
import dev.re7gog.b_sideloader.ui.feature.appdetails.AppDetailsArgs
import dev.re7gog.b_sideloader.ui.feature.appdetails.AppDetailsScreen
import dev.re7gog.b_sideloader.ui.feature.apps.AppsListScreen
import dev.re7gog.b_sideloader.ui.feature.background.BackgroundSettingsScreen
import dev.re7gog.b_sideloader.ui.feature.search.SearchScreen
import dev.re7gog.b_sideloader.ui.feature.settings.SettingsScreen
import dev.re7gog.b_sideloader.ui.feature.telegramlogin.TelegramLoginScreen
import dev.re7gog.b_sideloader.ui.navigation.AppsRoute
import dev.re7gog.b_sideloader.ui.navigation.BackgroundSettingsRoute
import dev.re7gog.b_sideloader.ui.navigation.ListDetailPane
import dev.re7gog.b_sideloader.ui.navigation.ListDetailSceneStrategy
import dev.re7gog.b_sideloader.ui.navigation.NavDirection
import dev.re7gog.b_sideloader.ui.navigation.NavigationState
import dev.re7gog.b_sideloader.ui.navigation.Navigator
import dev.re7gog.b_sideloader.ui.navigation.NewGithubAppRoute
import dev.re7gog.b_sideloader.ui.navigation.NewTelegramAppRoute
import dev.re7gog.b_sideloader.ui.navigation.SavedAppRoute
import dev.re7gog.b_sideloader.ui.navigation.SearchRoute
import dev.re7gog.b_sideloader.ui.navigation.SettingsRoute
import dev.re7gog.b_sideloader.ui.navigation.TelegramLoginRoute
import dev.re7gog.b_sideloader.ui.navigation.rememberNavigationState
import dev.re7gog.b_sideloader.ui.navigation.topLevelDestinations
import kotlinx.collections.immutable.toImmutableList

/**
 * The app shell: navigation bar plus the Navigation 3 display.
 *
 * The whole navigation graph is one `entryProvider` block. Every screen receives lambdas rather
 * than a controller, so no screen can reach into the back stack — which is what makes them
 * previewable and testable in isolation.
 */
@Composable
fun BSideLoaderApp(modifier: Modifier = Modifier) {
    val navigationState = rememberNavigationState(
        topLevelRoutes = remember { topLevelDestinations.map { it.route }.toImmutableList() },
    )
    val navigator = remember(navigationState) { Navigator(navigationState) }
    val haptics = LocalHapticFeedback.current

    // `supportLargeAndXLargeWidth` is what makes the 1200 dp breakpoint exist at all: with the
    // default breakpoint set the reported width saturates at 840 dp, so a large-window check would
    // silently never fire.
    val adaptiveInfo = currentWindowAdaptiveInfo(supportLargeAndXLargeWidth = true)
    val sizeClass = adaptiveInfo.windowSizeClass

    // Two panes as soon as two panes fit: an unfolded foldable, a tablet, a desktop window.
    val twoPane = sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    val expandedWidth =
        sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

    // The rail that spells its labels out costs 220–360 dp, and it competes with those two panes
    // for the same width. A foldable's inner display reports as *expanded* — around 850 dp on
    // current hardware — which is nowhere near enough to pay for a wide rail, a list and a
    // readable details pane at once. So it waits for the large breakpoint, which in practice means
    // a tablet in landscape or a desktop window, and stands down on anything with a hinge even if
    // that device does get big enough.
    val wideRail = sizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_LARGE_LOWER_BOUND) &&
        adaptiveInfo.windowPosture.hingeList.isEmpty()

    val listDetailStrategy = remember(twoPane, expandedWidth) {
        ListDetailSceneStrategy<NavKey>(
            enabled = twoPane,
            // A fold is barely wider than two phones side by side. Material's 360 dp list pane
            // would leave the details narrower than the single-pane layout it replaces, so the
            // list gives some width back below the expanded breakpoint.
            listPaneWidth = if (expandedWidth) {
                ListDetailSceneStrategy.DEFAULT_LIST_PANE_WIDTH
            } else {
                ListDetailSceneStrategy.NARROW_LIST_PANE_WIDTH
            },
            placeholder = { NoAppSelectedPane() },
        )
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            topLevelDestinations.forEach { destination ->
                val selected = destination.route == navigationState.topLevelRoute
                item(
                    selected = selected,
                    onClick = {
                        // Only buzz on an actual switch: re-tapping the current tab is a no-op and
                        // should feel like one.
                        if (!selected) {
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            navigator.navigate(destination.route)
                        }
                    },
                    icon = {
                        Icon(
                            painter = painterResource(destination.icon),
                            contentDescription = stringResource(destination.label),
                        )
                    },
                    label = { Text(stringResource(destination.label)) },
                )
            }
        },
        layoutType = navigationSuiteType(
            sizeClass = sizeClass,
            isTabletop = adaptiveInfo.windowPosture.isTabletop,
            allowWideRail = wideRail,
        ),
        modifier = modifier,
    ) {
        NavDisplay(
            entries = navigationState.toDecoratedEntries(
                rememberEntryProvider(navigator, navigationState, twoPane = twoPane)
            ),
            onBack = { navigator.goBack() },
            sceneStrategies = listOf(listDetailStrategy),
            // Both specs ask the navigator rather than trusting which one NavDisplay picked: its
            // pop/push guess is derived from back-stack shape, and a sibling-tab switch looks
            // identical in both directions to it. See Navigator.direction.
            transitionSpec = { sharedAxisX(navigator.direction) },
            popTransitionSpec = { sharedAxisX(navigator.direction) },
        )
    }
}

/**
 * Which navigation component the suite shows.
 *
 * The only departure from `NavigationSuiteScaffoldDefaults.navigationSuiteType` is the top case:
 * the default stops at a collapsed rail, which is a fixed 96 dp strip where a label longer than
 * the icon is simply clipped. The *expanded* wide rail measures its widest item and grows to fit
 * it (bounded to 220–360 dp by the Material spec), so a long translation gets the room it needs
 * plus the rail's own horizontal padding instead of being truncated.
 *
 * Whether that upgrade is affordable is [allowWideRail]'s call, not this function's — it depends
 * on what else is competing for the width, which is why it is decided next to the two-pane
 * threshold rather than here.
 */
private fun navigationSuiteType(
    sizeClass: WindowSizeClass,
    isTabletop: Boolean,
    allowWideRail: Boolean,
): NavigationSuiteType = when {
    !sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) ->
        NavigationSuiteType.ShortNavigationBarCompact

    // A folded-flat or short window has no vertical room for a rail's worth of items.
    isTabletop || !sizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND) ->
        NavigationSuiteType.ShortNavigationBarMedium

    allowWideRail -> NavigationSuiteType.WideNavigationRailExpanded

    else -> NavigationSuiteType.WideNavigationRailCollapsed
}

/**
 * Material's shared X-axis transition: the incoming page slides in from the direction of travel
 * while the outgoing one slides away, both cross-fading. The offset is a fraction of the width
 * rather than the whole width, so a tab switch reads as a shift rather than a full page swipe.
 */
private fun AnimatedContentTransitionScope<Scene<NavKey>>.sharedAxisX(
    direction: NavDirection,
): ContentTransform {
    val sign = if (direction == NavDirection.Forward) 1 else -1
    return (
        slideInHorizontally(tween(ENTER_MILLIS)) { width -> sign * width / SLIDE_FRACTION } +
            fadeIn(tween(ENTER_MILLIS))
        ) togetherWith (
        slideOutHorizontally(tween(EXIT_MILLIS)) { width -> -sign * width / SLIDE_FRACTION } +
            fadeOut(tween(EXIT_MILLIS))
        )
}

/** The right-hand pane before the user has picked anything. */
@Composable
private fun NoAppSelectedPane() {
    EmptyState(
        iconRes = R.drawable.apps_24px,
        title = stringResource(R.string.no_app_selected_title),
        subtitle = stringResource(R.string.no_app_selected_subtitle),
        tinted = true,
    )
}

/**
 * Resolves a [NavKey] to the screen that renders it.
 *
 * Kept as a function of the navigator so every destination's callbacks are declared in one place;
 * a screen never decides where "back" or "done" goes, the graph does.
 */
@Composable
private fun rememberEntryProvider(
    navigator: Navigator,
    navigationState: NavigationState,
    twoPane: Boolean,
): (NavKey) -> androidx.navigation3.runtime.NavEntry<NavKey> =
    remember(navigator, navigationState, twoPane) {
        entryProvider {
            entry<AppsRoute>(metadata = ListDetailPane.list) {
                // Reading the stack here is a snapshot read, so picking another app re-highlights
                // the list without the list having to know anything about navigation.
                val openAppId = navigationState.backStacks.getValue(AppsRoute)
                    .lastOrNull()
                    .let { it as? SavedAppRoute }
                    ?.appId

                AppsListScreen(
                    onAppClick = { appId -> navigator.showAppDetails(appId) },
                    highlightedAppId = openAppId.takeIf { twoPane },
                )
            }

            entry<SearchRoute> {
                SearchScreen(
                    onGithubRepoClick = { repo ->
                        navigator.navigate(
                            NewGithubAppRoute(
                                owner = repo.owner,
                                repo = repo.name,
                                name = repo.name,
                                description = repo.description,
                                stars = repo.stars,
                                avatarUrl = repo.avatarUrl,
                            )
                        )
                    },
                    onTelegramTargetClick = { chatId, topicId, title ->
                        navigator.navigate(NewTelegramAppRoute(chatId, topicId, title))
                    },
                )
            }

            entry<SettingsRoute> {
                SettingsScreen(
                    onTelegramLoginClick = { navigator.navigate(TelegramLoginRoute) },
                    onBackgroundSettingsClick = { navigator.navigate(BackgroundSettingsRoute) },
                )
            }

            entry<SavedAppRoute>(metadata = ListDetailPane.detail) { key ->
                AppDetailsScreen(
                    args = AppDetailsArgs.Saved(key.appId),
                    onBack = { navigator.goBack() },
                    // Already in the list; there is nowhere else to send the user.
                    onFinishedFromSearch = { navigator.goBack() },
                    // In two panes the list beside it already *is* the way back.
                    showBackAffordance = !twoPane,
                )
            }

            entry<NewGithubAppRoute> { key ->
                AppDetailsScreen(
                    args = AppDetailsArgs.NewGithub(
                        owner = key.owner,
                        repo = key.repo,
                        name = key.name,
                        description = key.description,
                        stars = key.stars,
                        avatarUrl = key.avatarUrl,
                    ),
                    onBack = { navigator.goBack() },
                    onFinishedFromSearch = { navigator.goToAppsList() },
                )
            }

            entry<NewTelegramAppRoute> { key ->
                AppDetailsScreen(
                    args = AppDetailsArgs.NewTelegram(
                        chatId = key.chatId,
                        topicId = key.topicId,
                        title = key.title,
                    ),
                    onBack = { navigator.goBack() },
                    onFinishedFromSearch = { navigator.goToAppsList() },
                )
            }

            entry<TelegramLoginRoute> {
                TelegramLoginScreen(
                    onSignedIn = { navigator.goBack() },
                    onExit = { navigator.goBack() },
                )
            }

            entry<BackgroundSettingsRoute> {
                BackgroundSettingsScreen(onBack = { navigator.goBack() })
            }
        }
    }

/**
 * Lower bound of the "large" window width class.
 *
 * Spelled out rather than referenced as `WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND`, which only
 * exists from window-core 1.5.0 while the compile classpath resolves 1.4.0 (1.5.0 arrives at
 * runtime via a newer transitive constraint). The *value* is still honoured, because
 * material3-adaptive carries its own copy of this breakpoint and uses it when
 * `currentWindowAdaptiveInfo(supportLargeAndXLargeWidth = true)` computes the size class.
 */
private const val WIDTH_DP_LARGE_LOWER_BOUND = 1200

private const val ENTER_MILLIS = 320
private const val EXIT_MILLIS = 220

/** Slide distance as a fraction of the pane width. A full-width slide reads as a page swipe. */
private const val SLIDE_FRACTION = 6
