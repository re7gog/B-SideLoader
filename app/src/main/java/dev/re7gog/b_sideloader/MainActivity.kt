package dev.re7gog.b_sideloader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.re7gog.b_sideloader.ui.features.app_details.AppGhDetailsScreen
import dev.re7gog.b_sideloader.ui.features.app_details.AppTgDetailsScreen
import dev.re7gog.b_sideloader.ui.features.apps_list.AppsListScreen
import dev.re7gog.b_sideloader.ui.features.search_app.SearchAppScreen
import dev.re7gog.b_sideloader.ui.features.settings.SettingsScreen
import dev.re7gog.b_sideloader.ui.features.telegram_login.AuthScreen
import dev.re7gog.b_sideloader.ui.navigation.AppGhDetailsFromDbRoute
import dev.re7gog.b_sideloader.ui.navigation.AppGhDetailsFromSearchRoute
import dev.re7gog.b_sideloader.ui.navigation.AppTgDetailsFromDbRoute
import dev.re7gog.b_sideloader.ui.navigation.AppTgDetailsFromSearchRoute
import dev.re7gog.b_sideloader.ui.navigation.AppsListRoute
import dev.re7gog.b_sideloader.ui.navigation.SearchAppRoute
import dev.re7gog.b_sideloader.ui.navigation.SettingsRoute
import dev.re7gog.b_sideloader.ui.navigation.TgLoginRoute
import dev.re7gog.b_sideloader.ui.navigation.topLevelDestinations
import dev.re7gog.b_sideloader.ui.theme.BSideLoaderTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BSideLoaderTheme {
                BSideLoaderApp()
            }
        }
    }
}

@Composable
fun BSideLoaderApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            topLevelDestinations.forEach {
                item(
                    selected = currentDestination?.hasRoute(it.route::class) == true,
                    onClick = {
                        navController.navigate(it.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true // Do not create copies of same screen
                            restoreState = true
                        }
                    },
                    icon = {
                        Icon(
                            painter = painterResource(it.icon),
                            contentDescription = stringResource(it.label)
                        )
                    },
                    label = { Text(stringResource(it.label)) }
                )
            }
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = AppsListRoute
        ) {
            composable<AppsListRoute> {
                AppsListScreen(
                    onGhAppClick = { id ->
                        navController.navigate(AppGhDetailsFromDbRoute(appId = id))
                    },
                    onTgAppClick = { id ->
                        navController.navigate(AppTgDetailsFromDbRoute(appId = id))
                    }
                )
            }
            composable<SearchAppRoute> {
                SearchAppScreen(
                    onGhSearchResClick = { repo ->
                        navController.navigate(
                            AppGhDetailsFromSearchRoute(
                                name = repo.name,
                                owner = repo.owner.login,
                                repo = repo.name,
                                description = repo.description,
                                stars = repo.stars,
                                iconUrl = repo.owner.avatarUrl
                            )

                        )
                    },
                    onTgSearchResClick = { messageList ->
                        navController.navigate(
                            AppTgDetailsFromSearchRoute(
                                name = messageList.name,
                                chatId = messageList.chatId,
                                topicId = messageList.topicId
                            )
                        )
                    }
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    onTgLoginClick = { navController.navigate(TgLoginRoute) }
                )
            }
            composable<AppGhDetailsFromDbRoute> {
                AppGhDetailsScreen(
                    onBackClick = { navController.navigate(AppsListRoute) }
                )
            }
            composable<AppGhDetailsFromSearchRoute> {
                AppGhDetailsScreen(
                    onBackClick = { navController.navigate(SearchAppRoute) }
                )
            }
            composable<AppTgDetailsFromDbRoute> {
                AppTgDetailsScreen(
                    onBackClick = { navController.navigate(AppsListRoute) }
                )
            }
            composable<AppTgDetailsFromSearchRoute> {
                AppTgDetailsScreen(
                    onBackClick = { navController.navigate(SearchAppRoute) }
                )
            }
            composable<TgLoginRoute> {
                AuthScreen(
                    onAuthSuccess = {
                        navController.navigate(SettingsRoute) {
                            popUpTo<SettingsRoute> { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
