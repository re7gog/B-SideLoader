package dev.re7gog.b_sideloader.domain.suggestion

import dev.re7gog.b_sideloader.domain.model.DownloadRef
import dev.re7gog.b_sideloader.domain.model.FilterMode
import dev.re7gog.b_sideloader.domain.model.FilterRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleFilterDeriverTest {

    private val noFilters = FilterProposal(FilterMode.Words, FilterRule.None, FilterRule.None, usePrereleases = false)

    @Test
    fun `the word that sets the flavor apart becomes an include`() {
        val group = group(
            "app-foss-arm64-v8a-release.apk",
            "app-foss-armeabi-v7a-release.apk",
            "app-gplay-arm64-v8a-release.apk",
            "app-gplay-armeabi-v7a-release.apk",
        )

        val proposal = derive(group, example = "app-gplay-arm64-v8a-release.apk")

        assertEquals(FilterRule(include = "gplay"), proposal?.assetFilter)
        assertEquals(FilterMode.Words, proposal?.mode)
    }

    @Test
    fun `builds that differ only by architecture need no filter`() {
        val group = group("app-arm64-v8a.apk", "app-armeabi-v7a.apk", "app-x86_64.apk", "app-x86.apk")

        val proposal = derive(group, example = "app-armeabi-v7a.apk")

        assertEquals(FilterRule.None, proposal?.assetFilter)
    }

    @Test
    fun `a lone file keeps an APK filter that accepts it and drops one that does not`() {
        val group = group("app-foss.apk")
        val accepting = noFilters.copy(assetFilter = FilterRule(include = "foss"))
        val rejecting = noFilters.copy(assetFilter = FilterRule(include = "play"))

        assertEquals(accepting.assetFilter, derive(group, example = "app-foss.apk", current = accepting)?.assetFilter)
        assertEquals(FilterRule.None, derive(group, example = "app-foss.apk", current = rejecting)?.assetFilter)
    }

    @Test
    fun `pinning the architecture keeps the example's marker`() {
        val group = group("app-arm64-v8a.apk", "app-armeabi-v7a.apk")

        val proposal = derive(group, example = "app-arm64-v8a.apk", pinAbi = true)

        assertEquals(FilterRule(include = "arm64-v8a"), proposal?.assetFilter)
    }

    @Test
    fun `an example with no word of its own excludes the others' words`() {
        val group = group("app-1.4.0.apk", "app-1.4.0-debug.apk")

        val proposal = derive(group, example = "app-1.4.0.apk")

        assertEquals(FilterRule(exclude = "debug"), proposal?.assetFilter)
    }

    @Test
    fun `version numbers never become filter words`() {
        val group = group("app-v2.3.1-foss.apk", "app-v2.3.1-play.apk")

        val proposal = derive(group, example = "app-v2.3.1-foss.apk")

        val words = proposal!!.assetFilter.include + " " + proposal.assetFilter.exclude
        assertTrue(words, words.none(Char::isDigit))
    }

    @Test
    fun `names that differ only by numbers cannot be told apart`() {
        val group = group("app-100.apk", "app-200.apk")

        assertNull(derive(group, example = "app-100.apk"))
    }

    @Test
    fun `a regex-mode app gets a regex`() {
        val group = group("app-foss.apk", "app-play.apk")

        val proposal = derive(group, example = "app-foss.apk", current = noFilters.copy(mode = FilterMode.Regex))

        assertEquals(FilterMode.Regex, proposal?.mode)
        assertEquals(FilterRule(include = "foss"), proposal?.assetFilter)
    }

    @Test
    fun `picking from a pre-release turns pre-releases on`() {
        val group = group("app.apk", prerelease = true)

        assertEquals(true, derive(group, example = "app.apk")?.usePrereleases)
    }

    @Test
    fun `a release filter that rejects the example's release is cleared, any other is kept`() {
        val beta = group("app.apk", title = "v2.0-beta")
        val stable = group("app.apk", title = "v2.0")
        val skipBetas = noFilters.copy(sourceFilter = FilterRule(exclude = "beta"))

        assertEquals(FilterRule.None, derive(beta, example = "app.apk", current = skipBetas)?.sourceFilter)
        assertEquals(skipBetas.sourceFilter, derive(stable, example = "app.apk", current = skipBetas)?.sourceFilter)
    }

    private fun derive(
        group: SampleGroup,
        example: String,
        current: FilterProposal = noFilters,
        pinAbi: Boolean = false,
    ): FilterProposal? =
        ExampleFilterDeriver.derive(group.files.single { it.name == example }, group, current, pinAbi)

    private fun group(vararg names: String, title: String = "v1.0", prerelease: Boolean = false) = SampleGroup(
        label = title,
        filterTarget = title,
        isPrerelease = prerelease,
        files = names.map { SampleFile(it, DownloadRef.Http("https://example.test/$title/$it")) },
    )
}
