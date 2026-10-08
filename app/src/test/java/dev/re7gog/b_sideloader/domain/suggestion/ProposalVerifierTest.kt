package dev.re7gog.b_sideloader.domain.suggestion

import dev.re7gog.b_sideloader.domain.model.DownloadRef
import dev.re7gog.b_sideloader.domain.model.FilterMode
import dev.re7gog.b_sideloader.domain.model.FilterRule
import dev.re7gog.b_sideloader.domain.model.GithubAsset
import dev.re7gog.b_sideloader.domain.model.GithubRelease
import dev.re7gog.b_sideloader.testing.ARM64_ABIS
import dev.re7gog.b_sideloader.testing.githubApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProposalVerifierTest {

    private val snapshot = SourceSnapshot.GitHub(
        listOf(
            release("v2.0", "app-foss-arm64-v8a.apk", "app-foss-armeabi-v7a.apk", "app-gplay-arm64-v8a.apk"),
            release("v1.0", "app-foss-arm64-v8a.apk", "app-gplay-arm64-v8a.apk"),
        ),
    )
    private val app = githubApp()

    @Test
    fun `a filter that installs the example passes`() {
        val verification = verify(words(include = "gplay"), example = file("v2.0", "app-gplay-arm64-v8a.apk"))

        assertTrue(verification.problems.toString(), verification.passed)
    }

    @Test
    fun `an invalid regex is reported with its field`() {
        val proposal = FilterProposal(FilterMode.Regex, FilterRule(include = "(foss"), FilterRule.None, false)

        val problems = verify(proposal).problems

        assertTrue(problems.toString(), problems.any { it is ProposalProblem.InvalidRegex && it.field == FilterField.ApkInclude })
    }

    @Test
    fun `a filter that accepts nothing fails`() {
        assertEquals(listOf(ProposalProblem.NothingMatches), verify(words(include = "huawei")).problems)
    }

    @Test
    fun `dropping the example file fails`() {
        val problems = verify(words(include = "foss"), example = file("v2.0", "app-gplay-arm64-v8a.apk")).problems

        assertEquals(listOf(ProposalProblem.ExampleFileRejected("app-gplay-arm64-v8a.apk")), problems)
    }

    @Test
    fun `dropping the example's release fails, told apart from dropping the file`() {
        val proposal = words().copy(sourceFilter = FilterRule(exclude = "v2"))

        val problems = verify(proposal, example = file("v2.0", "app-gplay-arm64-v8a.apk")).problems

        assertEquals(listOf(ProposalProblem.ExampleGroupRejected("app-gplay-arm64-v8a.apk")), problems)
    }

    @Test
    fun `installing a sibling instead of the example fails`() {
        // Both run on a 64-bit phone, and the selector takes the first that does.
        val listedFirst = SourceSnapshot.GitHub(listOf(release("v1.0", "app-armeabi-v7a.apk", "app-arm64-v8a.apk")))

        val problems = verify(words(), snapshot = listedFirst, example = file("v1.0", "app-arm64-v8a.apk")).problems

        assertEquals(listOf(ProposalProblem.OtherFileChosen("app-arm64-v8a.apk", "app-armeabi-v7a.apk")), problems)
    }

    @Test
    fun `the preview shows each release before and after, and what an update installs`() {
        val preview = verify(words(include = "gplay")).preview

        assertEquals(listOf("v2.0", "v1.0"), preview.map { it.label })
        assertEquals(listOf("app-foss-arm64-v8a.apk", "app-foss-arm64-v8a.apk"), preview.map { it.before })
        assertEquals(listOf("app-gplay-arm64-v8a.apk", "app-gplay-arm64-v8a.apk"), preview.map { it.after })
        assertEquals(listOf(true, false), preview.map { it.isTarget })
    }

    private fun verify(
        proposal: FilterProposal,
        example: SampleFile? = null,
        snapshot: SourceSnapshot = this.snapshot,
    ): Verification = ProposalVerifier.verify(proposal, app, snapshot, example, ARM64_ABIS)

    private fun words(include: String = "", exclude: String = "") =
        FilterProposal(FilterMode.Words, FilterRule(include, exclude), FilterRule.None, usePrereleases = false)

    private fun file(tag: String, name: String) = SampleFile(name, DownloadRef.Http(url(tag, name)))

    private fun release(tag: String, vararg names: String) = GithubRelease(
        name = tag,
        assets = names.map { GithubAsset(name = it, downloadUrl = url(tag, it)) },
    )

    /** Per release, so same-named files of two releases are still two files. */
    private fun url(tag: String, name: String) = "https://example.test/$tag/$name"
}
