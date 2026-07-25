package dev.re7gog.b_sideloader.domain.selection

import dev.re7gog.b_sideloader.domain.model.FilterMode
import dev.re7gog.b_sideloader.domain.model.FilterRule

/**
 * Decides whether a name (release title, APK file name, Telegram caption, ...) passes a
 * [FilterRule]. Shared by every source and by both the UI preview and the background updater, so
 * what the user sees on the details screen is exactly what the updater will install.
 *
 * [FilterMode.Words]
 *  - `include`: whitespace-separated words that must *all* appear (case-insensitive substring).
 *  - `exclude`: words, *none* of which may appear.
 *
 * [FilterMode.Regex]
 *  - `include`: a regex the target must match somewhere.
 *  - `exclude`: a regex the target must not match anywhere.
 *  - A blank side imposes no constraint. An invalid (half-typed) pattern never matches, so an
 *    invalid `include` rejects everything while an invalid `exclude` excludes nothing — the UI
 *    stays responsive while the user is still typing the pattern.
 */
object NameMatcher {

    fun matches(target: String, rule: FilterRule, mode: FilterMode): Boolean = when (mode) {
        FilterMode.Words -> matchesWords(target, rule)
        FilterMode.Regex -> matchesRegex(target, rule)
    }

    private fun matchesWords(target: String, rule: FilterRule): Boolean {
        val haystack = target.lowercase()
        return rule.include.words().all { haystack.contains(it) } &&
            rule.exclude.words().none { haystack.contains(it) }
    }

    private fun matchesRegex(target: String, rule: FilterRule): Boolean {
        val includeOk = rule.include.isBlank() ||
            rule.include.toRegexOrNull()?.containsMatchIn(target) == true
        val excludeOk = rule.exclude.isBlank() ||
            rule.exclude.toRegexOrNull()?.containsMatchIn(target) != true
        return includeOk && excludeOk
    }

    private fun String.toRegexOrNull(): Regex? =
        try {
            Regex(this, RegexOption.IGNORE_CASE)
        } catch (_: IllegalArgumentException) {
            null // invalid or half-typed pattern
        }

    private fun String.words(): List<String> =
        trim().split(WHITESPACE).filter { it.isNotBlank() }.map { it.lowercase() }

    private val WHITESPACE = "\\s+".toRegex()
}
