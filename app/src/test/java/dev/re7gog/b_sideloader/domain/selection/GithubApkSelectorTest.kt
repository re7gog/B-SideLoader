package dev.re7gog.b_sideloader.domain.selection

import dev.re7gog.b_sideloader.domain.model.AppSource
import dev.re7gog.b_sideloader.domain.model.DownloadRef
import dev.re7gog.b_sideloader.testing.ARM64_ABIS
import dev.re7gog.b_sideloader.testing.asset
import dev.re7gog.b_sideloader.testing.githubApp
import dev.re7gog.b_sideloader.testing.release
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubApkSelectorTest {

    @Test
    fun `picks the newest release with a matching apk`() {
        val app = githubApp()
        val candidate = select(
            app,
            release("v2.0", assets = arrayOf(asset("app-universal.apk"))),
            release("v1.0", assets = arrayOf(asset("app-universal.apk"))),
        )

        assertEquals("v2.0", candidate?.version?.raw)
    }

    @Test
    fun `prereleases are skipped unless enabled`() {
        val releases = arrayOf(
            release("v2.0-rc1", prerelease = true, assets = arrayOf(asset("app.apk"))),
            release("v1.0", assets = arrayOf(asset("app.apk"))),
        )

        assertEquals("v1.0", select(githubApp(), *releases)?.version?.raw)
        assertEquals("v2.0-rc1", select(githubApp(usePrereleases = true), *releases)?.version?.raw)
    }

    @Test
    fun `release filter skips non matching releases`() {
        val candidate = select(
            githubApp(releaseExclude = "nightly"),
            release("nightly-2026-01-01", assets = arrayOf(asset("app.apk"))),
            release("v1.0", assets = arrayOf(asset("app.apk"))),
        )

        assertEquals("v1.0", candidate?.version?.raw)
    }

    @Test
    fun `non apk assets are ignored`() {
        val candidate = select(
            githubApp(),
            release("v1.0", assets = arrayOf(asset("checksums.txt"), asset("app.apk"))),
        )

        assertEquals("app.apk", candidate?.fileName)
    }

    /** A release whose only APK fails the filter is skipped entirely, not returned empty. */
    @Test
    fun `release with no matching asset is skipped`() {
        val candidate = select(
            githubApp(assetInclude = "arm64"),
            release("v2.0", assets = arrayOf(asset("app-x86_64.apk"))),
            release("v1.0", assets = arrayOf(asset("app-arm64-v8a.apk"))),
        )

        assertEquals("v1.0", candidate?.version?.raw)
        assertEquals("app-arm64-v8a.apk", candidate?.fileName)
    }

    /**
     * The device's architecture decides *within* a release. This is the case the old code got
     * wrong for GitHub: it took the first matching asset regardless of ABI.
     */
    @Test
    fun `prefers the asset this device can run`() {
        val candidate = select(
            githubApp(),
            release(
                "v1.0",
                assets = arrayOf(
                    asset("app-x86_64.apk"),
                    asset("app-arm64-v8a.apk"),
                ),
            ),
        )

        assertEquals("app-arm64-v8a.apk", candidate?.fileName)
    }

    @Test
    fun `returns null when nothing matches`() {
        assertNull(select(githubApp(assetInclude = "nonexistent"), release("v1.0", assets = arrayOf(asset("app.apk")))))
    }

    @Test
    fun `candidate carries the asset download url and size`() {
        val candidate = select(githubApp(), release("v1.0", assets = arrayOf(asset("app.apk", size = 4242L))))

        assertTrue(candidate?.download is DownloadRef.Http)
        assertEquals("https://example.test/app.apk", (candidate?.download as DownloadRef.Http).url)
        assertEquals(4242L, candidate.sizeBytes)
    }

    @Test
    fun `matchingAssets lists every accepted apk newest first`() {
        val app = githubApp()
        val all = GithubApkSelector.matchingAssets(
            releases = listOf(
                release("v2.0", assets = arrayOf(asset("a-arm64-v8a.apk"), asset("a-x86.apk"))),
                release("v1.0", assets = arrayOf(asset("a-universal.apk"))),
            ),
            app = app,
            source = app.source as AppSource.GitHub,
        )

        assertEquals(
            listOf("a-arm64-v8a.apk", "a-x86.apk", "a-universal.apk"),
            all.map { it.fileName },
        )
    }

    private fun select(
        app: dev.re7gog.b_sideloader.domain.model.TrackedApp,
        vararg releases: dev.re7gog.b_sideloader.domain.model.GithubRelease,
    ) = GithubApkSelector.select(
        releases = releases.toList(),
        app = app,
        source = app.source as AppSource.GitHub,
        deviceAbis = ARM64_ABIS,
    )
}
