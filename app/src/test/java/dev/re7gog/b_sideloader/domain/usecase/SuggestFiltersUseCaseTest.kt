package dev.re7gog.b_sideloader.domain.usecase

import dev.re7gog.b_sideloader.domain.error.AiFailure
import dev.re7gog.b_sideloader.domain.error.AppError
import dev.re7gog.b_sideloader.domain.model.AiAvailability
import dev.re7gog.b_sideloader.domain.model.AiBackend
import dev.re7gog.b_sideloader.domain.model.AiProvider
import dev.re7gog.b_sideloader.domain.model.AiUnavailableReason
import dev.re7gog.b_sideloader.domain.model.DownloadRef
import dev.re7gog.b_sideloader.domain.model.FilterRule
import dev.re7gog.b_sideloader.domain.model.GithubAsset
import dev.re7gog.b_sideloader.domain.model.GithubRelease
import dev.re7gog.b_sideloader.domain.suggestion.ProposalProblem
import dev.re7gog.b_sideloader.domain.suggestion.SampleFile
import dev.re7gog.b_sideloader.domain.suggestion.SourceSnapshot
import dev.re7gog.b_sideloader.testing.FakeDeviceInfo
import dev.re7gog.b_sideloader.testing.FakeLanguageModelGateway
import dev.re7gog.b_sideloader.testing.githubApp
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SuggestFiltersUseCaseTest {

    private val snapshot = SourceSnapshot.GitHub(
        listOf(
            release("v2.0", "app-foss-arm64-v8a.apk", "app-gplay-arm64-v8a.apk"),
            release("v1.0", "app-foss-arm64-v8a.apk", "app-gplay-arm64-v8a.apk"),
        ),
    )
    private val model = FakeLanguageModelGateway()
    private val suggest = SuggestFiltersUseCase(model, FakeDeviceInfo())

    @Test
    fun `an example alone is solved without asking a model`() = runTest {
        val steps = suggest(request(example = file("v2.0", "app-foss-arm64-v8a.apk"))).toList()

        val suggestion = (steps.single() as SuggestionStep.Suggested).suggestion
        assertEquals(FilterRule(include = "foss"), suggestion.proposal.assetFilter)
        assertNull(suggestion.backend)
        assertTrue(model.prompts.isEmpty())
    }

    @Test
    fun `an example words cannot separate, with no model, ends without a filter`() = runTest {
        model.availability = AiAvailability.Unavailable(AiUnavailableReason.Disabled)
        val numbered = SourceSnapshot.GitHub(listOf(release("v1", "app-100.apk", "app-200.apk")))

        val steps = suggest(request(snapshot = numbered, example = file("v1", "app-100.apk"))).toList()

        assertTrue(steps.single() is SuggestionStep.NoWorkingFilter)
    }

    @Test
    fun `words with AI off are an error that says so`() = runTest {
        model.availability = AiAvailability.Unavailable(AiUnavailableReason.Disabled)

        try {
            suggest(request(instructions = "foss only")).toList()
            fail("Expected an AI error")
        } catch (e: AppError.Ai) {
            assertEquals(AiFailure.Disabled, e.reason)
        }
    }

    @Test
    fun `a reply that fails the check is sent back with its problems, and the next one wins`() = runTest {
        model.reply(
            "Sorry, I can't output JSON",
            """{"mode":"words","apk_include":"gplay","apk_exclude":"","release_include":"","release_exclude":"","prereleases":false,"explanation":"Play builds."}""",
        )

        val steps = suggest(request(instructions = "the play store build")).toList()

        assertEquals(
            listOf(SuggestionStep.Generating(1, 3), SuggestionStep.Generating(2, 3)),
            steps.filterIsInstance<SuggestionStep.Generating>(),
        )
        val suggestion = (steps.last() as SuggestionStep.Suggested).suggestion
        assertEquals(FilterRule(include = "gplay"), suggestion.proposal.assetFilter)
        assertEquals("Play builds.", suggestion.explanation)
        assertEquals(AiBackend.OnDevice, suggestion.backend)
        assertTrue(model.prompts[1].input.contains("did not work"))
    }

    @Test
    fun `every attempt failing ends with the last one's problems`() = runTest {
        val nothing = """{"mode":"words","apk_include":"huawei"}"""
        model.reply(nothing, nothing, nothing)

        val steps = suggest(request(instructions = "huawei build")).toList()

        assertEquals(SuggestFiltersUseCase.MAX_ATTEMPTS, model.prompts.size)
        val last = (steps.last() as SuggestionStep.NoWorkingFilter).last
        assertEquals(listOf(ProposalProblem.NothingMatches), last?.problems)
    }

    @Test
    fun `an example the model must work with arrives first, with its starting point`() = runTest {
        model.availability = AiAvailability.Available(AiBackend.Cloud(AiProvider.OpenAi))
        model.reply("""{"mode":"words","apk_include":"foss"}""")

        suggest(
            request(example = file("v2.0", "app-foss-arm64-v8a.apk"), instructions = "and skip betas"),
        ).toList()

        val input = model.prompts.single().input
        assertTrue(input.contains("exactly this file from release 1: app-foss-arm64-v8a.apk"))
        assertTrue(input.contains("<starting_point>"))
    }

    @Test
    fun `the on-device model is downloaded before it is asked`() = runTest {
        model.availability = AiAvailability.Available(AiBackend.OnDevice, needsDownload = true)
        model.downloadProgress = listOf(0L, 1_000L)
        model.reply("""{"mode":"words","apk_include":"foss"}""")

        val steps = suggest(request(instructions = "foss")).toList()

        assertEquals(
            listOf(SuggestionStep.DownloadingModel(0L), SuggestionStep.DownloadingModel(1_000L)),
            steps.takeWhile { it is SuggestionStep.DownloadingModel },
        )
    }

    private fun request(
        snapshot: SourceSnapshot = this.snapshot,
        example: SampleFile? = null,
        instructions: String = "",
    ) = SuggestionRequest(app = githubApp(), snapshot = snapshot, example = example, instructions = instructions)

    private fun file(tag: String, name: String) = SampleFile(name, DownloadRef.Http(url(tag, name)))

    private fun release(tag: String, vararg names: String) = GithubRelease(
        name = tag,
        assets = names.map { GithubAsset(name = it, downloadUrl = url(tag, it)) },
    )

    private fun url(tag: String, name: String) = "https://example.test/$tag/$name"
}
