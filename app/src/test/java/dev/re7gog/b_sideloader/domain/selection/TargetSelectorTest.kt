package dev.re7gog.b_sideloader.domain.selection

import dev.re7gog.b_sideloader.domain.model.CandidateFile
import dev.re7gog.b_sideloader.domain.model.CandidateGroup
import dev.re7gog.b_sideloader.testing.ARM64_ABIS
import dev.re7gog.b_sideloader.testing.updateCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TargetSelectorTest {

    @Test
    fun `picks the first runnable file of the newest group`() {
        val target = TargetSelector.select(
            listOf(
                group("v2", "app-x86_64.apk", "app-arm64-v8a.apk", "app-universal.apk"),
                group("v1", "app-universal.apk"),
            ),
            ARM64_ABIS,
        )

        assertEquals("v2" to "app-arm64-v8a.apk", target?.version?.raw to target?.fileName)
    }

    @Test
    fun `skips a newer group with nothing runnable`() {
        val target = TargetSelector.select(
            listOf(group("v2", "app-x86_64.apk"), group("v1", "app-armeabi-v7a.apk")),
            ARM64_ABIS,
        )

        assertEquals("v1", target?.version?.raw)
    }

    /**
     * Nothing runnable anywhere: the newest file still comes back, so the page shows what the
     * filters let through instead of nothing at all.
     */
    @Test
    fun `falls back to the newest file when nothing is runnable`() {
        val target = TargetSelector.select(
            listOf(group("v2", "app-x86_64.apk", "app-x86.apk"), group("v1", "app-x86.apk")),
            ARM64_ABIS,
        )

        assertEquals("v2" to "app-x86_64.apk", target?.version?.raw to target?.fileName)
    }

    /** A file the APK filter leaves out is shown, but never chosen — even when it would run. */
    @Test
    fun `never picks a file the filter leaves out`() {
        val group = CandidateGroup(
            title = "v1",
            notes = null,
            files = listOf(
                CandidateFile(updateCandidate("v1", "app-arm64-v8a.apk"), matchesFilter = false),
                CandidateFile(updateCandidate("v1", "app-x86_64.apk"), matchesFilter = true),
            ),
        )

        assertEquals("app-x86_64.apk", TargetSelector.select(listOf(group), ARM64_ABIS)?.fileName)
    }

    @Test
    fun `returns null when there is nothing`() {
        assertNull(TargetSelector.select(emptyList(), ARM64_ABIS))
    }

    private fun group(version: String, vararg fileNames: String) = CandidateGroup(
        title = version,
        notes = null,
        files = fileNames.map { CandidateFile(updateCandidate(version, it), matchesFilter = true) },
    )
}
