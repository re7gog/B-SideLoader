package dev.re7gog.b_sideloader.ui.features.search_app

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import dev.re7gog.b_sideloader.R

/**
 * A place apps can come from. The search screen renders this list generically — a browser-style
 * source picker plus one body per source — so a new source only needs an entry here, an icon,
 * and its branch in [SearchAppScreen].
 *
 * [searchPlaceholderRes] doubles as the "is this a query-driven source" flag: sources without one
 * (e.g. [LocalFile]) get no search field at all.
 */
enum class SearchSource(
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
    @param:StringRes val searchPlaceholderRes: Int? = null
) {
    GitHub(
        titleRes = R.string.source_github_title,
        descriptionRes = R.string.source_github_description,
        searchPlaceholderRes = R.string.source_github_placeholder
    ),
    Telegram(
        titleRes = R.string.source_telegram_title,
        descriptionRes = R.string.source_telegram_description,
        searchPlaceholderRes = R.string.source_telegram_placeholder
    ),
    LocalFile(
        titleRes = R.string.source_localfile_title,
        descriptionRes = R.string.source_localfile_description
    );

    val isSearchable: Boolean get() = searchPlaceholderRes != null
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
