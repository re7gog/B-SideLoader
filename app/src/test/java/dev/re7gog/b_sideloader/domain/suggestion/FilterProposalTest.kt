package dev.re7gog.b_sideloader.domain.suggestion

import dev.re7gog.b_sideloader.domain.model.AppSource
import dev.re7gog.b_sideloader.domain.model.FilterMode
import dev.re7gog.b_sideloader.domain.model.FilterRule
import dev.re7gog.b_sideloader.domain.selection.NameMatcher
import dev.re7gog.b_sideloader.testing.githubApp
import dev.re7gog.b_sideloader.testing.telegramApp
import org.junit.Assert.assertEquals
import org.junit.Test

class FilterProposalTest {

    @Test
    fun `words convert to a regex that accepts exactly the same names`() {
        val names = listOf(
            "app-foss-release.apk", "app-foss-debug.apk", "app-play-release.apk",
            "app-release.apk", "FOSS.Release.apk", "app-foss+release.apk",
        )
        val rules = listOf(
            FilterRule(include = "foss"),
            FilterRule(include = "foss release"),
            FilterRule(exclude = "debug play"),
            FilterRule(include = "release", exclude = "debug"),
            FilterRule(include = "foss+release"),
        )
        for (rule in rules) {
            val regex = rule.wordsToRegex()
            for (name in names) {
                assertEquals(
                    "$rule -> $regex on $name",
                    NameMatcher.matches(name, rule, FilterMode.Words),
                    NameMatcher.matches(name, regex, FilterMode.Regex),
                )
            }
        }
    }

    @Test
    fun `regex metacharacters are escaped with backslashes, not quoted`() {
        assertEquals("""a\.b\+c""", "a.b+c".escapeRegex())
    }

    @Test
    fun `a proposal round-trips through a GitHub app`() {
        val app = githubApp(assetInclude = "foss", releaseExclude = "beta", usePrereleases = true)

        val proposal = FilterProposal.of(app)

        assertEquals(FilterRule(exclude = "beta"), proposal.sourceFilter)
        assertEquals(true, proposal.usePrereleases)
        assertEquals(app, proposal.applyTo(app))
    }

    @Test
    fun `a Telegram proposal writes the message filter`() {
        val app = telegramApp()
        val proposal = FilterProposal(FilterMode.Regex, FilterRule("arm64"), FilterRule(exclude = "beta"), false)

        val applied = proposal.applyTo(app)

        assertEquals(FilterMode.Regex, applied.filterMode)
        assertEquals(FilterRule("arm64"), applied.assetFilter)
        assertEquals(FilterRule(exclude = "beta"), (applied.source as AppSource.Telegram).messageFilter)
    }
}
