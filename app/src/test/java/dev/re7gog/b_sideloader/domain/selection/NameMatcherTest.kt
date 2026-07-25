package dev.re7gog.b_sideloader.domain.selection

import dev.re7gog.b_sideloader.domain.model.FilterMode
import dev.re7gog.b_sideloader.domain.model.FilterRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NameMatcherTest {

    @Test
    fun `empty rule matches everything`() {
        assertTrue(matchesWords("anything at all", FilterRule.None))
    }

    @Test
    fun `every include word must appear`() {
        val rule = FilterRule(include = "app release")
        assertTrue(matchesWords("my-app-release.apk", rule))
        assertFalse(matchesWords("my-app-debug.apk", rule))
    }

    @Test
    fun `include matching is case insensitive`() {
        assertTrue(matchesWords("MyApp-RELEASE.apk", FilterRule(include = "myapp release")))
    }

    @Test
    fun `no exclude word may appear`() {
        val rule = FilterRule(exclude = "debug beta")
        assertTrue(matchesWords("app-release.apk", rule))
        assertFalse(matchesWords("app-debug.apk", rule))
        assertFalse(matchesWords("app-beta.apk", rule))
    }

    @Test
    fun `exclude wins over include`() {
        val rule = FilterRule(include = "app", exclude = "debug")
        assertFalse(matchesWords("app-debug.apk", rule))
    }

    @Test
    fun `extra whitespace between words is ignored`() {
        assertTrue(matchesWords("app-release.apk", FilterRule(include = "  app   release  ")))
    }

    @Test
    fun `regex include must match somewhere`() {
        val rule = FilterRule(include = """v\d+\.\d+""")
        assertTrue(matchesRegex("app-v1.2.apk", rule))
        assertFalse(matchesRegex("app-nightly.apk", rule))
    }

    @Test
    fun `regex exclude must not match`() {
        val rule = FilterRule(exclude = """(debug|beta)""")
        assertTrue(matchesRegex("app-release.apk", rule))
        assertFalse(matchesRegex("app-beta.apk", rule))
    }

    @Test
    fun `regex matching is case insensitive`() {
        assertTrue(matchesRegex("APP-RELEASE.apk", FilterRule(include = "release")))
    }

    /**
     * A half-typed pattern is the normal state while the user is editing the field, so it must
     * degrade predictably rather than crash: an unusable include rejects everything…
     */
    @Test
    fun `invalid include regex rejects everything`() {
        assertFalse(matchesRegex("app-release.apk", FilterRule(include = "app(")))
    }

    /** …and an unusable exclude excludes nothing, which keeps the preview list populated. */
    @Test
    fun `invalid exclude regex excludes nothing`() {
        assertTrue(matchesRegex("app-release.apk", FilterRule(exclude = "app(")))
    }

    @Test
    fun `word mode does not interpret regex metacharacters`() {
        // "a.b" is a literal substring in word mode, not "a, any char, b".
        assertFalse(matchesWords("axb", FilterRule(include = "a.b")))
        assertTrue(matchesWords("a.b", FilterRule(include = "a.b")))
    }

    private fun matchesWords(target: String, rule: FilterRule) =
        NameMatcher.matches(target, rule, FilterMode.Words)

    private fun matchesRegex(target: String, rule: FilterRule) =
        NameMatcher.matches(target, rule, FilterMode.Regex)
}
