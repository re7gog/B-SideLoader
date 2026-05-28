package dev.re7gog.b_sideloader.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import dev.re7gog.b_sideloader.R
import kotlinx.serialization.Serializable

@Serializable
object AppsListRoute

@Serializable
object SearchAppRoute

@Serializable
object SettingsRoute

@Serializable
data class AppGhDetailsFromDbRoute(val appId: Long, val installed: Boolean)

@Serializable
data class AppTgDetailsFromDbRoute(val appId: Long, val installed: Boolean)

@Serializable
data class AppGhDetailsFromSearchRoute(
    val name: String,
    val owner: String,
    val repo: String,
    val description: String?,
    val stars: Int,
    val iconUrl: String?
)

@Serializable
data class AppTgDetailsFromSearchRoute(
    val chatId: Long,
    val chatTitle: String,
    val topicId: Int?
)

@Serializable
object TgLoginRoute

data class NavMenuItem<T : Any>(
    val route: T,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int
)

val topLevelDestinations = listOf(
    NavMenuItem(AppsListRoute, R.string.apps, R.drawable.apps_24px),
    NavMenuItem(SearchAppRoute, R.string.search, R.drawable.search_24px),
    NavMenuItem(SettingsRoute, R.string.settings, R.drawable.settings_24px)
)
