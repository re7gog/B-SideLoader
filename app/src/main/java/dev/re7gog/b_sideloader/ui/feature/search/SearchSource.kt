package dev.re7gog.b_sideloader.ui.feature.search

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import dev.re7gog.b_sideloader.R

/**
 * A place apps can come from.
 *
 * The search screen renders this list generically — one browser-style picker plus one body per
 * source — so a new source needs an entry here, an icon, and its branch in `SearchScreen`.
 *
 * [searchPlaceholderRes] doubles as the "is this query-driven" flag: a source without one (e.g.
 * [LocalFile]) gets no search field at all.
 */
enum class SearchSource(
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
    @param:StringRes val searchPlaceholderRes: Int? = null,
) {
    GitHub(
        titleRes = R.string.source_github_title,
        descriptionRes = R.string.source_github_description,
        searchPlaceholderRes = R.string.source_github_placeholder,
    ),
    Telegram(
        titleRes = R.string.source_telegram_title,
        descriptionRes = R.string.source_telegram_description,
        searchPlaceholderRes = R.string.source_telegram_placeholder,
    ),
    LocalFile(
        titleRes = R.string.source_localfile_title,
        descriptionRes = R.string.source_localfile_description,
    ),
    ;

    val isSearchable: Boolean get() = searchPlaceholderRes != null
}

/** GitHub's mark has to invert with the theme; the others are colour-fixed. */
@DrawableRes
@Composable
@ReadOnlyComposable
fun SearchSource.iconRes(): Int = when (this) {
    SearchSource.GitHub -> githubIconRes()
    SearchSource.Telegram -> R.drawable.telegram
    SearchSource.LocalFile -> R.drawable.apk_file_24px
}

@DrawableRes
@Composable
@ReadOnlyComposable
fun githubIconRes(): Int =
    if (isSystemInDarkTheme()) R.drawable.github_invertocat_white else R.drawable.github_invertocat_black
