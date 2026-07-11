package dev.re7gog.b_sideloader.data.filter

/**
 * Decides whether a name (release title, APK file name, Telegram message text, ...) passes an
 * include/exclude filter. Shared by the GitHub and Telegram selection logic so both sources — and
 * both the UI preview and the background updater — filter identically.
 *
 * Two modes, selected per app by [AppEntity.advancedMode]:
 *  - Simple: [include] is whitespace-separated words that must all appear and [exclude] words none
 *    of which may appear; matching is case-insensitive substring.
 *  - Advanced: [include] is a regex the target must match and [exclude] a regex it must not match
 *    (case-insensitive). A blank field imposes no constraint. An invalid regex matches nothing, so
 *    an invalid include rejects everything while an invalid exclude excludes nothing.
 */
object NameFilter {
    fun matches(target: String, include: String, exclude: String, advanced: Boolean): Boolean =
        if (advanced) matchesRegex(target, include, exclude)
        else matchesWords(target, include, exclude)

    private fun matchesWords(target: String, include: String, exclude: String): Boolean {
        val t = target.lowercase()
        return include.words().all { t.contains(it) } && exclude.words().none { t.contains(it) }
    }

    private fun matchesRegex(target: String, include: String, exclude: String): Boolean {
        val includeOk = if (include.isBlank()) true
        else regexOrNull(include)?.containsMatchIn(target) ?: false
        val excludeOk = if (exclude.isBlank()) true
        else regexOrNull(exclude)?.containsMatchIn(target)?.not() ?: true
        return includeOk && excludeOk
    }

    private fun regexOrNull(pattern: String): Regex? =
        try {
            Regex(pattern, RegexOption.IGNORE_CASE)
        } catch (e: Exception) {  // Invalid/half-typed pattern
            null
        }

    private fun String.words(): List<String> =
        trim().split("\\s+".toRegex()).filter { it.isNotBlank() }.map { it.lowercase() }
}
