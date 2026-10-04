package dev.re7gog.b_sideloader.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GithubRepoLinkTest {

    private val expected = GithubRepoLink("octocat", "Hello-World")

    @Test
    fun `the repository page in every form people copy it`() {
        listOf(
            "https://github.com/octocat/Hello-World",
            "http://github.com/octocat/Hello-World",
            "https://www.github.com/octocat/Hello-World",
            "https://m.github.com/octocat/Hello-World",
            "github.com/octocat/Hello-World",
            "HTTPS://GitHub.com/octocat/Hello-World",
            "https://github.com/octocat/Hello-World/",
            "  https://github.com/octocat/Hello-World  ",
        ).forEach { assertEquals(it, expected, GithubRepoLink.parse(it)) }
    }

    @Test
    fun `a sub-page, query or fragment still names the repository`() {
        listOf(
            "https://github.com/octocat/Hello-World/releases",
            "https://github.com/octocat/Hello-World/releases/tag/v1.0",
            "https://github.com/octocat/Hello-World/tree/main/app",
            "https://github.com/octocat/Hello-World?tab=readme-ov-file",
            "https://github.com/octocat/Hello-World#install",
        ).forEach { assertEquals(it, expected, GithubRepoLink.parse(it)) }
    }

    @Test
    fun `clone URLs, SSH remotes and a bare owner-slash-repo`() {
        listOf(
            "https://github.com/octocat/Hello-World.git",
            "git@github.com:octocat/Hello-World.git",
            "octocat/Hello-World",
        ).forEach { assertEquals(it, expected, GithubRepoLink.parse(it)) }
    }

    @Test
    fun `dots, underscores and dashes stay part of the name`() {
        assertEquals(
            GithubRepoLink("some-owner", "my.app_v2"),
            GithubRepoLink.parse("https://github.com/some-owner/my.app_v2"),
        )
        assertEquals(
            GithubRepoLink("owner", "repo.github.io"),
            GithubRepoLink.parse("https://github.com/owner/repo.github.io"),
        )
    }

    @Test
    fun `anything that is not a GitHub repository is rejected`() {
        listOf(
            "",
            "fdroid",
            "https://github.com/octocat",
            "https://github.com/",
            "https://gitlab.com/octocat/Hello-World",
            "https://example.com/octocat/Hello-World",
            "https://github.com/-octocat/Hello-World",
            "https://github.com/octocat/..",
            "octocat/Hello World",
        ).forEach { assertNull(it, GithubRepoLink.parse(it)) }
    }
}
