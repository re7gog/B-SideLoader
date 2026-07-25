package dev.re7gog.b_sideloader.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.navigation3.runtime.NavKey
import dev.re7gog.b_sideloader.R
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.Serializable

/**
 * Every destination in the app, as a Navigation 3 [NavKey].
 *
 * A key is a value, not a string route with encoded arguments: the arguments *are* the key's
 * properties, type-checked at the call site, and `@Serializable` so the back stack survives
 * process death.
 */

@Serializable
data object AppsRoute : NavKey

@Serializable
data object SearchRoute : NavKey

@Serializable
data object SettingsRoute : NavKey

/** An app already in the database. */
@Serializable
data class SavedAppRoute(val appId: Long) : NavKey

/**
 * A GitHub repository picked from search that is not tracked yet.
 *
 * The summary fields travel with the key so the details screen can render its header immediately,
 * before the (slower) release lookup returns.
 */
@Serializable
data class NewGithubAppRoute(
    val owner: String,
    val repo: String,
    val name: String,
    val description: String? = null,
    val stars: Int = 0,
    val avatarUrl: String? = null,
) : NavKey

/** A Telegram channel/topic picked from search that is not tracked yet. */
@Serializable
data class NewTelegramAppRoute(
    val chatId: Long,
    val topicId: Int,
    val title: String,
) : NavKey

@Serializable
data object TelegramLoginRoute : NavKey

/** Background reliability checklist, reached from settings. */
@Serializable
data object BackgroundSettingsRoute : NavKey

/** One entry in the navigation bar / rail. */
@Immutable
data class TopLevelDestination(
    val route: NavKey,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
)

/**
 * The bar's contents, in order. The first entry is also the start route: the user always exits the
 * app from it ("exit through home"), which is what [NavigationState] encodes.
 */
val topLevelDestinations: ImmutableList<TopLevelDestination> = persistentListOf(
    TopLevelDestination(AppsRoute, R.string.apps, R.drawable.apps_24px),
    TopLevelDestination(SearchRoute, R.string.search, R.drawable.search_24px),
    TopLevelDestination(SettingsRoute, R.string.settings, R.drawable.settings_24px),
)
