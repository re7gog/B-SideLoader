package dev.re7gog.b_sideloader.domain.suggestion

import dev.re7gog.b_sideloader.domain.model.CandidateGroup
import dev.re7gog.b_sideloader.domain.model.FilterMode
import dev.re7gog.b_sideloader.domain.model.FilterRule
import dev.re7gog.b_sideloader.domain.model.TrackedApp
import dev.re7gog.b_sideloader.domain.selection.AbiMatcher
import dev.re7gog.b_sideloader.domain.selection.TargetSelector

/**
 * Checks a [FilterProposal] by running it: the real selectors over the [SourceSnapshot], so what
 * passes here is what the details page will show and the updater will install. Nothing a language
 * model says about its own filter is trusted.
 *
 * Only what can be decided mechanically is a [ProposalProblem]: a pattern that does not compile, a
 * filter that accepts nothing, an example that is not what gets installed. Whether the filter does
 * what the user *meant* is left to the user, with the [Verification.preview] to judge by.
 */
object ProposalVerifier {

    /** How many of the newest releases or messages the preview lists. */
    const val PREVIEW_SIZE = 8

    fun verify(
        proposal: FilterProposal,
        app: TrackedApp,
        snapshot: SourceSnapshot,
        example: SampleFile?,
        deviceAbis: List<String>,
    ): Verification {
        val proposed = proposal.applyTo(app)
        val problems = buildList {
            addAll(invalidPatterns(proposal))
            val groups = snapshot.groups(proposed)
            if (groups.isEmpty()) add(ProposalProblem.NothingMatches)
            if (example != null) exampleProblem(example, proposed, groups, snapshot, deviceAbis)?.let(::add)
        }
        return Verification(
            problems = problems,
            preview = preview(app, proposed, snapshot, deviceAbis),
        )
    }

    private fun invalidPatterns(proposal: FilterProposal): List<ProposalProblem> {
        if (proposal.mode != FilterMode.Regex) return emptyList()
        return listOf(
            FilterField.ApkInclude to proposal.assetFilter.include,
            FilterField.ApkExclude to proposal.assetFilter.exclude,
            FilterField.SourceInclude to proposal.sourceFilter.include,
            FilterField.SourceExclude to proposal.sourceFilter.exclude,
        ).mapNotNull { (field, pattern) ->
            if (pattern.isBlank()) return@mapNotNull null
            try {
                Regex(pattern, RegexOption.IGNORE_CASE)
                null
            } catch (e: IllegalArgumentException) {
                ProposalProblem.InvalidRegex(field, pattern, e.message?.lineSequence()?.firstOrNull().orEmpty())
            }
        }
    }

    /**
     * The example must be what an update installs from its release: accepted, and the file the
     * selector picks there. When the example cannot run on this phone the selector would never
     * pick it, so being accepted is all that can be asked.
     */
    private fun exampleProblem(
        example: SampleFile,
        proposed: TrackedApp,
        groups: List<CandidateGroup>,
        snapshot: SourceSnapshot,
        deviceAbis: List<String>,
    ): ProposalProblem? {
        val group = groups.firstOrNull { it.contains(example) }
        val file = group?.files?.firstOrNull { it.candidate.download == example.ref }
        if (file == null || !file.matchesFilter) {
            // A group is dropped both when its release/message is rejected and when none of its
            // files pass; with the APK filter cleared, only the first reason is left.
            val acceptedWithoutApkFilter = snapshot.groups(proposed.copy(assetFilter = FilterRule.None))
                .any { it.contains(example) }
            return if (acceptedWithoutApkFilter) {
                ProposalProblem.ExampleFileRejected(example.name)
            } else {
                ProposalProblem.ExampleGroupRejected(example.name)
            }
        }
        if (!AbiMatcher.runsOn(example.name, deviceAbis)) return null
        val chosen = TargetSelector.select(listOf(group), deviceAbis) ?: return null
        return if (chosen.download == example.ref) {
            null
        } else {
            ProposalProblem.OtherFileChosen(example = example.name, chosen = chosen.fileName)
        }
    }

    /**
     * For each of the newest releases or messages, which file it would install from with the
     * current filters and with the proposed ones, and which one an update would install now.
     */
    private fun preview(
        current: TrackedApp,
        proposed: TrackedApp,
        snapshot: SourceSnapshot,
        deviceAbis: List<String>,
    ): List<PreviewRow> {
        val before = snapshot.groups(current)
        val after = snapshot.groups(proposed)
        val target = TargetSelector.select(after, deviceAbis)
        return snapshot.samples(current).take(PREVIEW_SIZE).map { sample ->
            val chosenAfter = after.chosenFor(sample, deviceAbis)
            PreviewRow(
                label = sample.label,
                isPrerelease = sample.isPrerelease,
                before = before.chosenFor(sample, deviceAbis)?.name,
                after = chosenAfter?.name,
                isTarget = chosenAfter != null && chosenAfter.ref == target?.download,
            )
        }
    }

    /** The file the selector picks from [sample]'s group, when the filters accept that group. */
    private fun List<CandidateGroup>.chosenFor(sample: SampleGroup, deviceAbis: List<String>): SampleFile? {
        val refs = sample.files.mapTo(HashSet()) { it.ref }
        val group = firstOrNull { group -> group.files.any { it.candidate.download in refs } } ?: return null
        val chosen = TargetSelector.select(listOf(group), deviceAbis) ?: return null
        return SampleFile(chosen.fileName, chosen.download)
    }

    private fun CandidateGroup.contains(file: SampleFile): Boolean =
        files.any { it.candidate.download == file.ref }
}

/** The outcome of checking one proposal. */
data class Verification(
    /** Empty when the proposal works. */
    val problems: List<ProposalProblem>,
    /** The newest releases or messages, each with what it would install before and after. */
    val preview: List<PreviewRow>,
) {
    val passed: Boolean get() = problems.isEmpty()
}

/** Which of a proposal's four text fields something is about. */
enum class FilterField { ApkInclude, ApkExclude, SourceInclude, SourceExclude }

/** Something mechanically wrong with a proposal. Each is fed back to the model to fix. */
sealed interface ProposalProblem {
    data class InvalidRegex(val field: FilterField, val pattern: String, val error: String) : ProposalProblem

    /** No release or message passes. */
    data object NothingMatches : ProposalProblem

    /** The example's release or message is filtered out. */
    data class ExampleGroupRejected(val example: String) : ProposalProblem

    /** The example's release or message passes, but the APK filter drops the example itself. */
    data class ExampleFileRejected(val example: String) : ProposalProblem

    /** The example passes, but a sibling would be installed in its place. */
    data class OtherFileChosen(val example: String, val chosen: String) : ProposalProblem

    /** The model's reply was not the JSON object it was asked for. */
    data class Unreadable(val reply: String) : ProposalProblem
}

/** One release or message in a proposal's preview. */
data class PreviewRow(
    /** The release name or the caption's first line; null for a message without a caption. */
    val label: String?,
    val isPrerelease: Boolean,
    /** The file it installs with the current filters; null when they skip it. */
    val before: String?,
    /** The file it installs with the proposed filters; null when they skip it. */
    val after: String?,
    /** Whether [after] is what an update would install now. */
    val isTarget: Boolean,
)
