package dev.re7gog.b_sideloader.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import dev.re7gog.b_sideloader.ui.feature.appdetails.AppDetailsArgs
import dev.re7gog.b_sideloader.ui.feature.appdetails.AppDetailsScreen
import dev.re7gog.b_sideloader.ui.feature.apps.AppsListScreen
import dev.re7gog.b_sideloader.ui.feature.background.BackgroundSettingsScreen
import dev.re7gog.b_sideloader.ui.feature.search.SearchScreen
import dev.re7gog.b_sideloader.ui.feature.settings.SettingsScreen
import dev.re7gog.b_sideloader.ui.feature.telegramlogin.TelegramLoginScreen
import dev.re7gog.b_sideloader.ui.navigation.AppsRoute
import dev.re7gog.b_sideloader.ui.navigation.BackgroundSettingsRoute
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

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            topLevelDestinations.forEach { destination ->
                item(
                    selected = destination.route == navigationState.topLevelRoute,
                    onClick = { navigator.navigate(destination.route) },
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
        modifier = modifier,
    ) {
        NavDisplay(
            entries = navigationState.toDecoratedEntries(rememberEntryProvider(navigator, navigationState)),
            onBack = { navigator.goBack() },
        )
    }
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
): (NavKey) -> androidx.navigation3.runtime.NavEntry<NavKey> = remember(navigator, navigationState) {
    entryProvider {
        entry<AppsRoute> {
            AppsListScreen(
                onAppClick = { appId -> navigator.navigate(SavedAppRoute(appId)) },
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

        entry<SavedAppRoute> { key ->
            AppDetailsScreen(
                args = AppDetailsArgs.Saved(key.appId),
                onBack = { navigator.goBack() },
                // Already in the list; there is nowhere else to send the user.
                onFinishedFromSearch = { navigator.goBack() },
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
