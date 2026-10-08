package dev.re7gog.b_sideloader.domain.suggestion

import dev.re7gog.b_sideloader.domain.model.DownloadRef
import dev.re7gog.b_sideloader.domain.model.FilterMode
import dev.re7gog.b_sideloader.domain.model.FilterRule
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterPromptTest {

    private val current = FilterProposal(
        mode = FilterMode.Words,
        assetFilter = FilterRule(include = "old"),
        sourceFilter = FilterRule(exclude = "nightly"),
        usePrereleases = false,
    )

    // ---- parsing -------------------------------------------------------------------------------

    @Test
    fun `a reply wrapped in a code fence and chatter is still read`() {
        val reply = """
            Sure! Here are the filters:
            ```json
            {"mode":"words","apk_include":"foss","apk_exclude":"","release_include":"","release_exclude":"beta","prereleases":true,"explanation":"FOSS builds only."}
            ```
        """.trimIndent()

        val parsed = FilterPrompt.parse(reply, SourceKind.GitHub, current)

        assertEquals(FilterRule(include = "foss"), parsed?.proposal?.assetFilter)
        assertEquals(FilterRule(exclude = "beta"), parsed?.proposal?.sourceFilter)
        assertEquals(true, parsed?.proposal?.usePrereleases)
        assertEquals("FOSS builds only.", parsed?.explanation)
    }

    @Test
    fun `a field the model left out keeps its current value`() {
        val parsed = FilterPrompt.parse("""{"mode":"words","apk_include":"foss"}""", SourceKind.GitHub, current)

        assertEquals(FilterRule(exclude = "nightly"), parsed?.proposal?.sourceFilter)
    }

    @Test
    fun `a field left out is cleared when the mode changes, since its meaning would`() {
        val parsed = FilterPrompt.parse("""{"mode":"regex","apk_include":"^app-foss"}""", SourceKind.GitHub, current)

        assertEquals(FilterMode.Regex, parsed?.proposal?.mode)
        assertEquals(FilterRule.None, parsed?.proposal?.sourceFilter)
    }

    @Test
    fun `booleans written as strings are understood`() {
        val parsed = FilterPrompt.parse("""{"prereleases":"true"}""", SourceKind.GitHub, current)

        assertEquals(true, parsed?.proposal?.usePrereleases)
    }

    @Test
    fun `Telegram uses message fields and never touches pre-releases`() {
        val parsed = FilterPrompt.parse(
            """{"message_include":"stable","prereleases":true}""",
            SourceKind.Telegram,
            current,
        )

        assertEquals(FilterRule(include = "stable", exclude = "nightly"), parsed?.proposal?.sourceFilter)
        assertEquals(false, parsed?.proposal?.usePrereleases)
    }

    @Test
    fun `anything that is not a JSON object is unreadable`() {
        assertNull(FilterPrompt.parse("I cannot help with that.", SourceKind.GitHub, current))
        assertNull(FilterPrompt.parse("{not json}", SourceKind.GitHub, current))
        assertNull(FilterPrompt.parse("[1, 2]", SourceKind.GitHub, current))
    }

    // ---- building ------------------------------------------------------------------------------

    @Test
    fun `the prompt carries the files, the example, the request and what failed before`() {
        val example = file("app-foss.apk")
        val prompt = FilterPrompt.build(
            PromptContext(
                kind = SourceKind.GitHub,
                samples = listOf(group("v2.0", "app-foss.apk", "app-play.apk")),
                current = current,
                example = example,
                request = "skip betas",
                attempts = listOf(
                    FailedAttempt(current, listOf(ProposalProblem.OtherFileChosen("app-foss.apk", "app-play.apk"))),
                ),
            ),
        )

        assertTrue(prompt.input.contains("app-play.apk"))
        assertTrue(prompt.input.contains("exactly this file from release 1: app-foss.apk"))
        assertTrue(prompt.input.contains("<request>skip betas</request>"))
        assertTrue(prompt.input.contains("app-play.apk also passes and would be installed instead"))
        assertTrue(prompt.instructions.contains("\"release_include\""))
    }

    @Test
    fun `the on-device budget keeps the prompt small`() {
        val samples = (1..10).map { group("v$it.0", "app-$it-first.apk") }

        val prompt = FilterPrompt.build(
            PromptContext(SourceKind.GitHub, samples, current, budget = PromptBudget.OnDevice),
        )

        assertTrue(prompt.input.contains("app-5-first.apk"))
        assertFalse(prompt.input.contains("app-6-first.apk"))
    }

    @Test
    fun `the schema requires every field of the source's kind and nothing else`() {
        val prompt = FilterPrompt.build(PromptContext(SourceKind.Telegram, emptyList(), current))

        val schema = Json.parseToJsonElement(prompt.jsonSchema!!).jsonObject
        val required = schema["required"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet()

        assertEquals(
            setOf("mode", "apk_include", "apk_exclude", "message_include", "message_exclude", "explanation"),
            required,
        )
        assertEquals("false", schema["additionalProperties"]!!.jsonPrimitive.content)
    }

    private fun group(title: String, vararg names: String) = SampleGroup(
        label = title,
        filterTarget = title,
        isPrerelease = false,
        files = names.map(::file),
    )

    private fun file(name: String) = SampleFile(name, DownloadRef.Http("https://example.test/$name"))
}
