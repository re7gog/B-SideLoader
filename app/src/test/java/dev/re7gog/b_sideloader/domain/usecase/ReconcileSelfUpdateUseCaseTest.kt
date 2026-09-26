package dev.re7gog.b_sideloader.domain.usecase

import dev.re7gog.b_sideloader.core.log.NoopLogger
import dev.re7gog.b_sideloader.domain.error.AppError
import dev.re7gog.b_sideloader.domain.model.AppSource
import dev.re7gog.b_sideloader.domain.model.AppVersion
import dev.re7gog.b_sideloader.domain.model.PendingSelfUpdate
import dev.re7gog.b_sideloader.testing.FakeAppsRepository
import dev.re7gog.b_sideloader.testing.FakeSelfAppInfo
import dev.re7gog.b_sideloader.testing.FakeSelfUpdateStateRepository
import dev.re7gog.b_sideloader.testing.githubApp
import dev.re7gog.b_sideloader.testing.selfApp
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReconcileSelfUpdateUseCaseTest {

    private val state = FakeSelfUpdateStateRepository()

    /** One instance is one process: the use case reconciles once per instance. */
    private fun process(
        apps: FakeAppsRepository,
        versionCode: Long,
        releaseTag: String = "",
    ) = ReconcileSelfUpdateUseCase(
        state,
        apps,
        FakeSelfAppInfo(versionCode = versionCode, releaseTag = AppVersion(releaseTag)),
        NoopLogger,
    )

    private suspend fun FakeAppsRepository.app(id: Long) = getApps().single { it.id == id }

    private suspend fun FakeAppsRepository.versionOf(id: Long): String = app(id).version.raw

    // ---- the update the previous process started ---------------------------------------------

    /** The ordinary case: the install killed the old process, and a newer build came up. */
    @Test
    fun `a pending update whose version code went up is recorded`() = runTest {
        val apps = FakeAppsRepository(listOf(selfApp(id = 1L, version = AppVersion("v1.0.0"))))
        state.rememberedVersionCode = 1L
        state.pending = PendingSelfUpdate(appId = 1L, releaseName = AppVersion("v2.0.0"))

        process(apps, versionCode = 2L, releaseTag = "v2.0.0")()

        assertEquals("v2.0.0", apps.versionOf(1L))
        assertNull(state.pending)
        assertEquals(2L, state.rememberedVersionCode)
    }

    /**
     * The row is compared against release *names*, so the name that was being installed is what
     * has to land — even when someone renamed the release away from its tag.
     */
    @Test
    fun `a landed update records the release it installed rather than the build tag`() = runTest {
        val apps = FakeAppsRepository(listOf(selfApp(id = 1L, version = AppVersion("v1.0.0"))))
        state.rememberedVersionCode = 1L
        state.pending = PendingSelfUpdate(appId = 1L, releaseName = AppVersion("Spring release"))

        process(apps, versionCode = 2L, releaseTag = "v2.0.0")()

        assertEquals("Spring release", apps.versionOf(1L))
    }

    /**
     * Declining the system dialog — or a reinstall of the very same build — leaves the version
     * code where it was. Nothing may be recorded then.
     */
    @Test
    fun `a pending update whose version code did not go up is dropped`() = runTest {
        val apps = FakeAppsRepository(listOf(selfApp(id = 1L, version = AppVersion("v1.0.0"))))
        state.rememberedVersionCode = 1L
        state.pending = PendingSelfUpdate(appId = 1L, releaseName = AppVersion("v2.0.0"))

        process(apps, versionCode = 1L, releaseTag = "v1.0.0")()

        assertEquals("v1.0.0", apps.versionOf(1L))
        assertNull(state.pending)
        assertEquals(1L, state.rememberedVersionCode)
    }

    /** The user may have removed the app from the list while the update was installing. */
    @Test
    fun `a pending update for a deleted row is dropped quietly`() = runTest {
        val apps = FakeAppsRepository(listOf(githubApp(id = 9L, version = AppVersion("v1.0"))))
        state.rememberedVersionCode = 1L
        state.pending = PendingSelfUpdate(appId = 1L, releaseName = AppVersion("v2.0.0"))

        process(apps, versionCode = 2L, releaseTag = "v2.0.0")()

        assertEquals("v1.0", apps.versionOf(9L))
        assertNull(state.pending)
    }

    // ---- the build's own tag -----------------------------------------------------------------

    /** The seeded row of a database that predates the tag starts out empty; first start fills it. */
    @Test
    fun `on first start the row learns which release is running`() = runTest {
        val apps = FakeAppsRepository(listOf(selfApp(id = 1L, version = AppVersion.Unknown)))

        process(apps, versionCode = 1_000_299L, releaseTag = "v1.0.2")()

        assertEquals("v1.0.2", apps.versionOf(1L))
        assertEquals(1_000_299L, state.rememberedVersionCode)
    }

    /**
     * An older build wrote its pending record in a format that remembered no version code, so
     * nothing can judge it. The running build's own tag is the truth either way.
     */
    @Test
    fun `a pending record nothing can judge gives way to the build tag`() = runTest {
        val apps = FakeAppsRepository(listOf(selfApp(id = 1L, version = AppVersion("v1.0.1"))))
        state.pending = PendingSelfUpdate(appId = 1L, releaseName = AppVersion("v9.9.9"))

        process(apps, versionCode = 1_000_299L, releaseTag = "v1.0.2")()

        assertEquals("v1.0.2", apps.versionOf(1L))
        assertNull(state.pending)
    }

    /**
     * An APK installed by hand, or an update that finished only after its record had already been
     * judged: either way this build is running and the row does not say so. Left alone, the app
     * would offer to install what it already is, forever.
     */
    @Test
    fun `a build that arrived some other way records its own tag`() = runTest {
        val apps = FakeAppsRepository(listOf(selfApp(id = 1L, version = AppVersion("v1.0.1"))))
        state.rememberedVersionCode = 1_000_199L

        process(apps, versionCode = 1_000_299L, releaseTag = "v1.0.2")()

        assertEquals("v1.0.2", apps.versionOf(1L))
        assertEquals(1_000_299L, state.rememberedVersionCode)
    }

    /** Every start of the same build must be free: no database write, no preference write. */
    @Test
    fun `an unchanged build writes nothing`() = runTest {
        val apps = FakeAppsRepository(listOf(selfApp(id = 1L, version = AppVersion("v1.0.1"))))
        state.rememberedVersionCode = 1_000_299L

        process(apps, versionCode = 1_000_299L, releaseTag = "v1.0.2")()

        assertEquals("v1.0.1", apps.versionOf(1L))
        assertEquals(0, state.writeCount)
    }

    /** A local build is not any release; claiming one would hide real updates behind it. */
    @Test
    fun `a build without a tag leaves the row unknown`() = runTest {
        val apps = FakeAppsRepository(listOf(selfApp(id = 1L, version = AppVersion.Unknown)))

        process(apps, versionCode = 2L, releaseTag = "")()

        assertEquals("", apps.versionOf(1L))
        assertEquals(2L, state.rememberedVersionCode)
    }

    /**
     * The tag names a release of the official repository. A fork sharing the package name may
     * name its releases differently, and other apps have nothing to do with it at all. A row for
     * the official repository with no package name yet was simply never installed from here.
     */
    @Test
    fun `only rows tracking the official repository take the tag`() = runTest {
        val fork = selfApp(id = 2L, version = AppVersion("fork-1"))
            .copy(source = AppSource.GitHub(owner = "someone", repo = "B-SideLoader"))
        val neverInstalled = selfApp(id = 4L, version = AppVersion.Unknown).copy(packageName = "")
        val apps = FakeAppsRepository(
            listOf(
                selfApp(id = 1L, version = AppVersion.Unknown),
                fork,
                githubApp(id = 3L, version = AppVersion("v1.0")),
                neverInstalled,
            ),
        )

        process(apps, versionCode = 1_000_299L, releaseTag = "v1.0.2")()

        assertEquals("v1.0.2", apps.versionOf(1L))
        assertEquals("fork-1", apps.versionOf(2L))
        assertEquals("v1.0", apps.versionOf(3L))
        assertEquals("v1.0.2", apps.versionOf(4L))
        assertEquals(FakeSelfAppInfo.SELF_PACKAGE, apps.app(4L).packageName)
    }

    // ---- once per process --------------------------------------------------------------------

    /**
     * The whole reason this runs once: an install started by this process writes a record whose
     * version code cannot have moved yet. Judged here, it would always look failed.
     */
    @Test
    fun `a record written after reconciling is left for the next process`() = runTest {
        val apps = FakeAppsRepository(listOf(selfApp(id = 1L, version = AppVersion("v1.0.0"))))
        state.rememberedVersionCode = 1L
        val oldProcess = process(apps, versionCode = 1L, releaseTag = "v1.0.0")

        oldProcess()
        state.markPending(PendingSelfUpdate(appId = 1L, releaseName = AppVersion("v2.0.0")))
        oldProcess()

        assertEquals(AppVersion("v2.0.0"), state.pending?.releaseName)

        process(apps, versionCode = 2L, releaseTag = "v2.0.0")()

        assertEquals("v2.0.0", apps.versionOf(1L))
        assertNull(state.pending)
    }

    /**
     * A failed database write must not stop the app from starting, and must not lose the record:
     * the next process gets the same state and another go.
     */
    @Test
    fun `a failure is swallowed and the state kept for the next process`() = runTest {
        val apps = FakeAppsRepository(listOf(selfApp(id = 1L, version = AppVersion("v1.0.0"))))
        state.rememberedVersionCode = 1L
        state.pending = PendingSelfUpdate(appId = 1L, releaseName = AppVersion("v2.0.0"))
        apps.failure = AppError.Storage("disk full")

        process(apps, versionCode = 2L, releaseTag = "v2.0.0")()

        assertEquals(AppVersion("v2.0.0"), state.pending?.releaseName)
        assertEquals(1L, state.rememberedVersionCode)

        apps.failure = null
        process(apps, versionCode = 2L, releaseTag = "v2.0.0")()

        assertEquals("v2.0.0", apps.versionOf(1L))
    }
}
