package dev.re7gog.b_sideloader.domain.model

/**
 * A GitHub repository named by a link the user pasted, before anything has looked it up.
 *
 * Search is fuzzy and sometimes ranks the wanted repository nowhere near the top, so the search
 * page also takes a direct link. [parse] accepts what people actually copy: the repository page in
 * any form (with or without scheme or `www.`, any sub-page such as `/releases/latest`, a query or a
 * fragment), a clone URL, an SSH remote, or a bare `owner/repo`.
 */
data class GithubRepoLink(val owner: String, val repo: String) {

    companion object {
        /** The link's repository, or null when [text] names no GitHub repository. */
        fun parse(text: String): GithubRepoLink? {
            val match = LINK.matchEntire(text.trim()) ?: return null
            val (owner, repo) = match.destructured
            if (repo == "." || repo == "..") return null
            return GithubRepoLink(owner, repo)
        }

        // Owner: letters, digits and single dashes, at most 39 characters. Repository: letters,
        // digits, `.`, `_` and `-`; a trailing `.git` belongs to the clone URL, not the name.
        private val LINK = Regex(
            """(?:(?:https?://)?(?:www\.|m\.)?github\.com/|git@github\.com:)?""" +
                """([A-Za-z0-9][A-Za-z0-9-]{0,38})/([A-Za-z0-9._-]+?)(?:\.git)?/?(?:[/?#].*)?""",
            RegexOption.IGNORE_CASE,
        )
    }
}
