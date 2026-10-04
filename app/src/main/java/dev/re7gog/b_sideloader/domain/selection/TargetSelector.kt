package dev.re7gog.b_sideloader.domain.selection

import dev.re7gog.b_sideloader.domain.model.CandidateGroup
import dev.re7gog.b_sideloader.domain.model.UpdateCandidate

/**
 * Picks the one APK to install out of a source's releases or messages, for every source alike.
 *
 * The update check and the details page both call this with the same groups, so the file the page
 * highlights is the file an update would install — by construction rather than by two pieces of
 * code that happen to agree. They used not to: the page picked across a flat list of files while
 * the check picked within the newest release, so a newest release with only, say, an x86 build
 * was highlighted one way and installed another.
 */
object TargetSelector {

    /**
     * The newest group (input is newest-first) holding a file this device can run, and the first
     * such file in it. When no group has one, the newest group's first file instead — installing
     * it will fail, but showing *something* lets the user see and fix their filters rather than
     * staring at an empty page. Null only when there is nothing at all.
     */
    fun select(groups: List<CandidateGroup>, deviceAbis: List<String>): UpdateCandidate? =
        groups.firstNotNullOfOrNull { group ->
            group.candidates.firstOrNull { AbiMatcher.runsOn(it.fileName, deviceAbis) }
        } ?: groups.firstNotNullOfOrNull { it.candidates.firstOrNull() }
}
