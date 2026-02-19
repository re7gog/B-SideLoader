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
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.re7gog.b_sideloader.ui.features.add_app.AddAppScreen
import dev.re7gog.b_sideloader.ui.features.apps_list.AppsListScreen
import dev.re7gog.b_sideloader.ui.features.settings.SettingsScreen
import dev.re7gog.b_sideloader.ui.navigation.AddAppRoute
import dev.re7gog.b_sideloader.ui.navigation.AppsListRoute
import dev.re7gog.b_sideloader.ui.navigation.SettingsRoute
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

@PreviewScreenSizes
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
                            // Clear navigation stack but save state
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
                    //onAppClick = { id ->
                    //navController.navigate(AppDetailsRoute(appId = id))
                    //}
                )
            }

            composable<AddAppRoute> {
                AddAppScreen(onSuccess = {
                    navController.navigate(AppsListRoute) {
                        popUpTo<AppsListRoute> { inclusive = true /* Remove screen from stack */ }
                    }
                })
            }

            composable<SettingsRoute> {
                SettingsScreen()
            }

            //composable<AppDetailsRoute> { backStackEntry ->
            //    val args: AppDetailsRoute = backStackEntry.toRoute()
            //    AppsDetailsScreen(appId = args.appId)
            //}
        }
    }
}
