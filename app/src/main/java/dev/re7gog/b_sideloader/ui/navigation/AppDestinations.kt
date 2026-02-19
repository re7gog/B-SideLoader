package dev.re7gog.b_sideloader.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import dev.re7gog.b_sideloader.R
import kotlinx.serialization.Serializable

@Serializable
object AppsListRoute
@Serializable
object AddAppRoute
@Serializable
object SettingsRoute

@Serializable
data class AppDetailsRoute(val appId: Int)

data class NavMenuItem<T : Any>(
    val route: T,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int
)

val topLevelDestinations = listOf(
    NavMenuItem(AppsListRoute, R.string.apps, R.drawable.apps_24px),
    NavMenuItem(AddAppRoute, R.string.add, R.drawable.add_24px),
    NavMenuItem(SettingsRoute, R.string.settings, R.drawable.settings_24px)
)

/*
Old
sealed class AppDestination(
    val route: String,
    @param:StringRes val label: Int? = null,
    @param:DrawableRes val icon: Int? = null,
) {
    object AppsList : AppDestination("list", R.string.apps, R.drawable.apps_24px)
    object AddApp : AppDestination("add", R.string.add, R.drawable.add_24px)
    object Settings : AppDestination("settings", R.string.settings, R.drawable.settings_24px)

    object Details : AppDestination("details/{appId}") {
        fun createRoute(appId: Int) = "details/$appId"
    }
}*/