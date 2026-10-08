package dev.re7gog.b_sideloader.domain.suggestion

import dev.re7gog.b_sideloader.domain.model.FilterMode
import dev.re7gog.b_sideloader.domain.model.FilterRule
import dev.re7gog.b_sideloader.domain.selection.NameMatcher

/**
 * Derives filters from one file the user picked, with no language model: the words that set the
 * picked file apart from its siblings in the same release or message.
 *
 * The example names a *variant* (FOSS vs Play, release vs debug), not a CPU architecture: files
 * that differ from it only by an ABI marker are the same variant, and the updater already picks
 * among those by what the phone runs. So by default nothing ABI-specific goes into the filter, and
 * the same filter keeps working on a phone with another architecture. [pinAbi] makes those
 * siblings count as different too, for the case where the selector would otherwise pick the wrong
 * one — a project whose ABI names `AbiMatcher` does not recognise.
 *
 * The result is only a candidate: [ProposalVerifier] decides whether it works.
 */
object ExampleFilterDeriver {

    /**
     * Filters that accept [example] and reject the other variants in [group], starting from
     * [current]. Null when no set of words can tell the example apart — names that differ only by
     * numbers, say.
     */
    fun derive(
        example: SampleFile,
        group: SampleGroup,
        current: FilterProposal,
        pinAbi: Boolean = false,
    ): FilterProposal? {
        val exampleName = example.name.baseName()
        val exampleVariant = exampleName.withoutAbi()
        val others = group.files
            .filter { it.ref != example.ref }
            .map { it.name.baseName() }
            .filter { pinAbi || it.withoutAbi() != exampleVariant }
            .distinct()

        // A release or message filter that already rejects the example's group is in the way;
        // otherwise it is the user's and stays.
        val sourceFilter = if (NameMatcher.matches(group.filterTarget, current.sourceFilter, current.mode)) {
            current.sourceFilter
        } else {
            FilterRule.None
        }
        val usePrereleases = current.usePrereleases || group.isPrerelease

        if (others.isEmpty()) {
            // Nothing to tell the example apart from: an APK filter that already accepts it may be
            // doing work in older releases, so it stays; one that rejects it goes.
            val keeps = NameMatcher.matches(example.name, current.assetFilter, current.mode)
            return current.copy(
                assetFilter = if (keeps) current.assetFilter else FilterRule.None,
                sourceFilter = sourceFilter,
                usePrereleases = usePrereleases,
            )
        }
        val assetFilter = distinguishingWords(exampleName, others, pinAbi) ?: return null
        // The words convert to a regex losslessly, so a regex-mode app stays in regex mode and keeps
        // its own release or message pattern.
        return FilterProposal(FilterMode.Words, assetFilter, FilterRule.None, usePrereleases)
            .inMode(current.mode)
            .copy(sourceFilter = sourceFilter)
    }

    /**
     * A small set of include and exclude words such that the example passes and every other name
     * fails — a greedy set cover, which for file names (a handful of candidates) is as good as
     * exact. Include words are preferred on a tie: "foss" still rejects a "huawei" build added
     * next month, where excluding today's "gplay" would not.
     */
    private fun distinguishingWords(example: String, others: List<String>, pinAbi: Boolean): FilterRule? {
        val includes = example.tokens(pinAbi).toSet()
        // Checked against the full name: GitHub matches asset names with their `.apk`, so an
        // exclude word inside it would reject the example too.
        val excludes = others.flatMap { it.tokens(pinAbi) }.filter { it !in example + APK_SUFFIX }.toSet()

        // Each candidate word, with which of the other names it rejects.
        val candidates = includes.map { word -> Candidate(word, include = true, rejects = others.filterNot { word in it }.toSet()) } +
            excludes.map { word -> Candidate(word, include = false, rejects = others.filter { word in it }.toSet()) }

        val uncovered = others.toMutableSet()
        val chosen = mutableListOf<Candidate>()
        while (uncovered.isNotEmpty()) {
            val best = candidates
                .filter { it !in chosen }
                .maxWithOrNull(
                    compareBy<Candidate> { (it.rejects intersect uncovered).size }
                        .thenBy { it.include }
                        .thenBy { it.word.length },
                )
            if (best == null || (best.rejects intersect uncovered).isEmpty()) return null
            chosen += best
            uncovered -= best.rejects
        }
        return FilterRule(
            include = chosen.filter { it.include }.joinToString(" ") { it.word },
            exclude = chosen.filterNot { it.include }.joinToString(" ") { it.word },
        )
    }

    private data class Candidate(val word: String, val include: Boolean, val rejects: Set<String>)

    /** Lower-case, without the `.apk` the Telegram matcher strips (and that every name shares). */
    private fun String.baseName(): String = lowercase().removeSuffix(APK_SUFFIX)

    private fun String.withoutAbi(): String =
        ABI_MARKERS.fold(this) { name, marker -> name.replace(marker, "") }.replace(SEPARATOR_RUNS, "-").trim('-')

    /**
     * The words a filter can usefully hold: no numbers (versions and build numbers change every
     * release), nothing shorter than two letters, and — unless pinned — no architecture markers.
     */
    private fun String.tokens(pinAbi: Boolean): List<String> {
        val abiTokens = ABI_MARKERS.filter { it in this }
        val pinned = if (pinAbi) abiTokens.take(1) else emptyList()
        val words = (if (pinAbi) this else withoutAbi())
            .split(NON_ALPHANUMERIC)
            .filter { it.length >= 2 && it.none(Char::isDigit) && it != "apk" }
        return pinned + words
    }

    private const val APK_SUFFIX = ".apk"

    /**
     * Longest first, so `arm64-v8a` is removed before `arm64` could leave a stray `-v8a`. The first
     * five are what `AbiMatcher` recognises; the rest are spellings projects use on their own.
     */
    private val ABI_MARKERS = listOf(
        "arm64-v8a", "armeabi-v7a", "armeabi", "x86_64", "x86-64", "riscv64",
        "aarch64", "arm64", "armv8a", "armv8", "armv7a", "armv7", "arm32", "x64", "x86",
    )

    private val NON_ALPHANUMERIC = "[^a-z0-9]+".toRegex()
    private val SEPARATOR_RUNS = "[-_.\\s]+".toRegex()
}
