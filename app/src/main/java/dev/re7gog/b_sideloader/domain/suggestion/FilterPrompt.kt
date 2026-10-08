package dev.re7gog.b_sideloader.domain.suggestion

import dev.re7gog.b_sideloader.domain.model.AiPrompt
import dev.re7gog.b_sideloader.domain.model.FilterMode
import dev.re7gog.b_sideloader.domain.model.FilterRule
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * The filter-suggestion prompt, and the reading of its reply. One prompt for every backend, so a
 * fix to it helps all of them; only its size changes, through [PromptBudget], because Gemini Nano
 * accepts about 4000 tokens.
 *
 * The reply is a flat JSON object rather than anything nested: the smallest model has to produce
 * it without a schema to hold it to, and is read leniently (code fences, text around it).
 */
object FilterPrompt {

    fun build(context: PromptContext): AiPrompt = AiPrompt(
        instructions = instructions(context.kind, context.language),
        input = input(context),
        jsonSchema = schema(context.kind),
    )

    /**
     * The proposal in [reply], or null when it is not the JSON object asked for. A field the
     * model left out keeps its [current] value — unless the mode changed, which would make the
     * old value mean something else.
     */
    fun parse(reply: String, kind: SourceKind, current: FilterProposal): ParsedReply? {
        val start = reply.indexOf('{')
        val end = reply.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val json = try {
            LENIENT_JSON.parseToJsonElement(reply.substring(start, end + 1)).jsonObject
        } catch (_: IllegalArgumentException) {
            return null // not JSON (a SerializationException), or JSON but not an object
        }

        val mode = when (json.string(KEY_MODE)?.trim()?.lowercase()) {
            "regex" -> FilterMode.Regex
            "words" -> FilterMode.Words
            else -> current.mode
        }
        val keep = mode == current.mode
        fun field(key: String, currentValue: String): String =
            json.string(key)?.trim() ?: if (keep) currentValue else ""

        val proposal = FilterProposal(
            mode = mode,
            assetFilter = FilterRule(
                include = field(KEY_APK_INCLUDE, current.assetFilter.include),
                exclude = field(KEY_APK_EXCLUDE, current.assetFilter.exclude),
            ),
            sourceFilter = FilterRule(
                include = field(kind.includeKey, current.sourceFilter.include),
                exclude = field(kind.excludeKey, current.sourceFilter.exclude),
            ),
            usePrereleases = if (kind == SourceKind.GitHub) {
                json.boolean(KEY_PRERELEASES) ?: current.usePrereleases
            } else {
                current.usePrereleases
            },
        )
        return ParsedReply(proposal, json.string(KEY_EXPLANATION)?.trim()?.takeIf { it.isNotEmpty() })
    }

    // ---- prompt text ----------------------------------------------------------------------------

    private fun instructions(kind: SourceKind, language: String): String = buildString {
        appendLine("You write filters for an Android app updater.")
        when (kind) {
            SourceKind.GitHub -> appendLine(
                "The source is a GitHub repository. Each release has a title and some APK files.",
            )

            SourceKind.Telegram -> appendLine(
                "The source is a Telegram channel. Each message has a caption and some APK files.",
            )
        }
        appendLine(
            "The updater installs from the newest ${kind.groupNoun} that passes the filters. Among its " +
                "APKs it picks the one built for the phone's CPU by itself, so filter by variant " +
                "(flavor, store, build type), not by CPU architecture, unless asked to.",
        )
        appendLine()
        appendLine("Fields:")
        appendLine("- apk_include / apk_exclude: matched against APK file names.")
        appendLine("- ${kind.includeKey} / ${kind.excludeKey}: matched against ${kind.targetDescription}.")
        if (kind == SourceKind.GitHub) appendLine("- prereleases: whether pre-releases are considered.")
        appendLine("Modes:")
        appendLine("- \"words\": include = space-separated words that must ALL appear; exclude = space-separated words, NONE of which may appear.")
        appendLine("- \"regex\": include = a regex that must match somewhere; exclude = a regex that must not match anywhere.")
        appendLine("Matching is case-insensitive. Prefer \"words\"; use \"regex\" only when words cannot do it.")
        appendLine("Use \"\" for a field that is not needed. Never put version numbers in a filter.")
        appendLine()
        appendLine("Reply with only this JSON object, nothing else:")
        appendLine(template(kind))
        append("Write \"explanation\" as one short sentence in $language.")
    }

    private fun template(kind: SourceKind): String = buildJsonObject {
        put(KEY_MODE, "words")
        put(KEY_APK_INCLUDE, "")
        put(KEY_APK_EXCLUDE, "")
        put(kind.includeKey, "")
        put(kind.excludeKey, "")
        if (kind == SourceKind.GitHub) put(KEY_PRERELEASES, false)
        put(KEY_EXPLANATION, "")
    }.toString()

    private fun input(context: PromptContext): String = buildString {
        val budget = context.budget
        appendLine("<${context.kind.groupNoun}s>")
        context.samples.take(budget.maxGroups).forEachIndexed { index, group ->
            append(index + 1).append(". ").append(context.kind.groupNoun)
            val label = group.filterTarget.ifBlank { null }?.let { budget.clip(it) }
            if (label != null) append(" ").append(JsonPrimitive(label))
            if (group.isPrerelease) append(" (pre-release)")
            appendLine()
            group.files.take(budget.maxFilesPerGroup).forEach { appendLine("   ${it.name}") }
            if (group.files.size > budget.maxFilesPerGroup) {
                appendLine("   (${group.files.size - budget.maxFilesPerGroup} more)")
            }
        }
        appendLine("</${context.kind.groupNoun}s>")
        appendLine()
        appendLine("<current_filters>${context.current.toJson(context.kind)}</current_filters>")

        context.example?.let { example ->
            appendLine()
            appendLine("<example>The user wants exactly this file from ${context.kind.groupNoun} 1: ${example.name}</example>")
        }
        context.startingPoint?.let { start ->
            appendLine("<starting_point>${start.toJson(context.kind)}</starting_point>")
        }
        if (context.request.isNotBlank()) {
            appendLine()
            appendLine("<request>${context.request.trim()}</request>")
        }
        context.rejected.forEach { rejected ->
            appendLine()
            appendLine("<rejected_by_user>${rejected.proposal.toJson(context.kind)}</rejected_by_user>")
            if (rejected.feedback.isNotBlank()) appendLine("<user_feedback>${rejected.feedback.trim()}</user_feedback>")
        }
        if (context.attempts.isNotEmpty()) {
            appendLine()
            appendLine("These earlier proposals did not work. Fix their problems:")
            context.attempts.forEachIndexed { index, attempt ->
                append("Attempt ").append(index + 1).append(": ")
                appendLine(attempt.proposal?.toJson(context.kind) ?: "(no JSON)")
                attempt.problems.forEach { appendLine("- ${it.describe(context.kind)}") }
            }
        }
    }

    private fun FilterProposal.toJson(kind: SourceKind): String = buildJsonObject {
        put(KEY_MODE, if (mode == FilterMode.Regex) "regex" else "words")
        put(KEY_APK_INCLUDE, assetFilter.include)
        put(KEY_APK_EXCLUDE, assetFilter.exclude)
        put(kind.includeKey, sourceFilter.include)
        put(kind.excludeKey, sourceFilter.exclude)
        if (kind == SourceKind.GitHub) put(KEY_PRERELEASES, usePrereleases)
    }.toString()

    private fun ProposalProblem.describe(kind: SourceKind): String = when (this) {
        is ProposalProblem.InvalidRegex -> "${field.key(kind)} is not a valid regex ($error)."
        ProposalProblem.NothingMatches -> "No ${kind.groupNoun} passes these filters, so nothing would ever be installed."
        is ProposalProblem.ExampleGroupRejected ->
            "The ${kind.groupNoun} with the example file is filtered out by ${kind.includeKey}/${kind.excludeKey}" +
                (if (kind == SourceKind.GitHub) " or prereleases." else ".")

        is ProposalProblem.ExampleFileRejected -> "apk_include/apk_exclude reject the example file $example."
        is ProposalProblem.OtherFileChosen ->
            "$chosen also passes and would be installed instead of the example $example. Make the apk filters reject it."

        is ProposalProblem.Unreadable -> "The reply was not the JSON object asked for."
    }

    private fun FilterField.key(kind: SourceKind): String = when (this) {
        FilterField.ApkInclude -> KEY_APK_INCLUDE
        FilterField.ApkExclude -> KEY_APK_EXCLUDE
        FilterField.SourceInclude -> kind.includeKey
        FilterField.SourceExclude -> kind.excludeKey
    }

    /** Lets a backend that can enforce a schema do so; the instructions say the same in words. */
    private fun schema(kind: SourceKind): String {
        val strings = listOf(KEY_APK_INCLUDE, KEY_APK_EXCLUDE, kind.includeKey, kind.excludeKey, KEY_EXPLANATION)
        val properties = buildJsonObject {
            put(KEY_MODE, buildJsonObject {
                put("type", "string")
                put("enum", JsonArray(listOf(JsonPrimitive("words"), JsonPrimitive("regex"))))
            })
            strings.forEach { key -> put(key, buildJsonObject { put("type", "string") }) }
            if (kind == SourceKind.GitHub) put(KEY_PRERELEASES, buildJsonObject { put("type", "boolean") })
        }
        return buildJsonObject {
            put("type", "object")
            put("properties", properties)
            put("required", JsonArray(properties.keys.map(::JsonPrimitive)))
            put("additionalProperties", false)
        }.toString()
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.boolean(key: String): Boolean? = (this[key] as? JsonPrimitive)?.let {
        it.booleanOrNull ?: it.contentOrNull?.trim()?.lowercase()?.toBooleanStrictOrNull()
    }

    private val LENIENT_JSON = Json { isLenient = true }

    private const val KEY_MODE = "mode"
    private const val KEY_APK_INCLUDE = "apk_include"
    private const val KEY_APK_EXCLUDE = "apk_exclude"
    private const val KEY_PRERELEASES = "prereleases"
    private const val KEY_EXPLANATION = "explanation"
}

/** Which source a prompt is about; it decides the field names the model sees. */
enum class SourceKind(
    val groupNoun: String,
    val includeKey: String,
    val excludeKey: String,
    val targetDescription: String,
) {
    GitHub("release", "release_include", "release_exclude", "release titles"),
    Telegram("message", "message_include", "message_exclude", "message captions"),
}

val SourceSnapshot.kind: SourceKind
    get() = when (this) {
        is SourceSnapshot.GitHub -> SourceKind.GitHub
        is SourceSnapshot.Telegram -> SourceKind.Telegram
    }

/** Everything one prompt is built from. */
data class PromptContext(
    val kind: SourceKind,
    /** Newest first. The example, when there is one, is in the first. */
    val samples: List<SampleGroup>,
    val current: FilterProposal,
    val example: SampleFile? = null,
    /** What the example alone suggested, when it was not enough. */
    val startingPoint: FilterProposal? = null,
    /** The user's own words. */
    val request: String = "",
    /** Suggestions the user looked at and turned down, newest last. */
    val rejected: List<RejectedSuggestion> = emptyList(),
    /** This run's replies that failed the check, oldest first. */
    val attempts: List<FailedAttempt> = emptyList(),
    /** English name of the language the explanation should be in. */
    val language: String = "English",
    val budget: PromptBudget = PromptBudget.Cloud,
)

/** How much of the source a prompt shows: Gemini Nano takes about 4000 tokens in all. */
data class PromptBudget(val maxGroups: Int, val maxFilesPerGroup: Int, val maxLabelChars: Int) {
    fun clip(text: String): String {
        val oneLine = text.replace(NEWLINES, " ").trim()
        return if (oneLine.length <= maxLabelChars) oneLine else oneLine.take(maxLabelChars).trimEnd() + "…"
    }

    companion object {
        val OnDevice = PromptBudget(maxGroups = 5, maxFilesPerGroup = 8, maxLabelChars = 80)
        val Cloud = PromptBudget(maxGroups = 12, maxFilesPerGroup = 20, maxLabelChars = 200)
        private val NEWLINES = "\\s*\\R\\s*".toRegex()
    }
}

/** A suggestion the user saw and said was not right, and what they said. */
data class RejectedSuggestion(val proposal: FilterProposal, val feedback: String)

/** A reply that did not pass [ProposalVerifier]: null [proposal] when it could not even be read. */
data class FailedAttempt(val proposal: FilterProposal?, val problems: List<ProposalProblem>)

/** A readable reply. */
data class ParsedReply(val proposal: FilterProposal, val explanation: String?)
