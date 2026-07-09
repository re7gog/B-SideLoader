package dev.re7gog.b_sideloader.ui.features.search_app

import androidx.annotation.DrawableRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import dev.re7gog.b_sideloader.R

/**
 * A place apps can come from. The search screen renders this list generically — a browser-style
 * source picker plus one body per source — so a new source only needs an entry here, an icon,
 * and its branch in [SearchAppScreen].
 *
 * [searchPlaceholder] doubles as the "is this a query-driven source" flag: sources without one
 * (e.g. [LocalFile]) get no search field at all.
 */
enum class SearchSource(
    val title: String,
    val description: String,
    val searchPlaceholder: String? = null
) {
    GitHub(
        title = "GitHub",
        description = "Releases from public repositories",
        searchPlaceholder = "Search GitHub repositories"
    ),
    Telegram(
        title = "Telegram",
        description = "APKs posted in channels",
        searchPlaceholder = "Search Telegram channels"
    ),
    LocalFile(
        title = "Local file",
        description = "Install an APK stored on this device"
    );

    val isSearchable: Boolean get() = searchPlaceholder != null
}

@DrawableRes
@Composable
@ReadOnlyComposable
fun SearchSource.iconRes(): Int = when (this) {
    SearchSource.GitHub -> if (isSystemInDarkTheme()) R.drawable.github_invertocat_white
                           else R.drawable.github_invertocat_black
    SearchSource.Telegram -> R.drawable.telegram
    SearchSource.LocalFile -> R.drawable.apk_file_24px
}
