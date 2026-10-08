package dev.re7gog.b_sideloader.domain.suggestion

import dev.re7gog.b_sideloader.domain.model.AppSource
import dev.re7gog.b_sideloader.domain.model.FilterMode
import dev.re7gog.b_sideloader.domain.model.FilterRule
import dev.re7gog.b_sideloader.domain.model.TrackedApp

/**
 * Every filter value of one app, as a single value: what a suggestion proposes and what applying
 * it writes into the details page's draft.
 *
 * Whole rather than a diff because [mode] is shared by both rules: switching it reinterprets every
 * field, so a proposal that changed the mode but kept an old field would mean something nobody
 * wrote.
 */
data class FilterProposal(
    val mode: FilterMode,
    /** Applied to APK file names. */
    val assetFilter: FilterRule,
    /** The release-title filter of a GitHub app, the message filter of a Telegram one. */
    val sourceFilter: FilterRule,
    /** GitHub only: whether pre-releases count. Carried through untouched for Telegram. */
    val usePrereleases: Boolean,
) {
    /** [app] with these filters and everything else as it was. */
    fun applyTo(app: TrackedApp): TrackedApp = app.copy(
        filterMode = mode,
        assetFilter = assetFilter,
        source = when (val source = app.source) {
            is AppSource.GitHub -> source.copy(releaseFilter = sourceFilter, usePrereleases = usePrereleases)
            is AppSource.Telegram -> source.copy(messageFilter = sourceFilter)
        },
    )

    /**
     * The same filters expressed in [target] mode. Word lists convert to regular expressions
     * losslessly; the other way round is not possible in general, so a regex proposal asked for
     * words stays as it is.
     */
    fun inMode(target: FilterMode): FilterProposal = when {
        target == mode -> this
        mode == FilterMode.Words && target == FilterMode.Regex -> copy(
            mode = FilterMode.Regex,
            assetFilter = assetFilter.wordsToRegex(),
            sourceFilter = sourceFilter.wordsToRegex(),
        )

        else -> this
    }

    companion object {
        /** What [app] is filtered by now. */
        fun of(app: TrackedApp): FilterProposal = FilterProposal(
            mode = app.filterMode,
            assetFilter = app.assetFilter,
            sourceFilter = when (val source = app.source) {
                is AppSource.GitHub -> source.releaseFilter
                is AppSource.Telegram -> source.messageFilter
            },
            usePrereleases = (app.source as? AppSource.GitHub)?.usePrereleases ?: false,
        )
    }
}

/**
 * A word rule as the regex rule that accepts exactly the same names: every include word must
 * appear (one lookahead each), and any exclude word rejects.
 */
internal fun FilterRule.wordsToRegex(): FilterRule {
    val include = include.filterWords()
    val exclude = exclude.filterWords()
    return FilterRule(
        include = when (include.size) {
            0 -> ""
            1 -> include.single().escapeRegex()
            else -> include.joinToString(separator = "", prefix = "^") { "(?=.*${it.escapeRegex()})" }
        },
        exclude = exclude.joinToString(separator = "|") { it.escapeRegex() },
    )
}

/** The words of a [FilterMode.Words] field, as [dev.re7gog.b_sideloader.domain.selection.NameMatcher] splits them. */
internal fun String.filterWords(): List<String> = trim().split(WHITESPACE).filter { it.isNotBlank() }

/**
 * Backslash-escapes regex metacharacters. Not [Regex.escape], whose `\Q...\E` quoting is correct but
 * unreadable in a filter field the user may want to edit afterwards.
 */
internal fun String.escapeRegex(): String = buildString {
    for (char in this@escapeRegex) {
        if (char in REGEX_METACHARACTERS) append('\\')
        append(char)
    }
}

private const val REGEX_METACHARACTERS = "\\.[]{}()<>*+-=!?^$|"

private val WHITESPACE = "\\s+".toRegex()
