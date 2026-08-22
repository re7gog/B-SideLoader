package dev.re7gog.b_sideloader.domain.usecase

import dev.re7gog.b_sideloader.core.log.NoopLogger
import dev.re7gog.b_sideloader.domain.model.AppVersion
import dev.re7gog.b_sideloader.domain.model.PendingSelfUpdate
import dev.re7gog.b_sideloader.testing.FakeAppsRepository
import dev.re7gog.b_sideloader.testing.FakePendingSelfUpdateRepository
import dev.re7gog.b_sideloader.testing.FakeSelfAppInfo
import dev.re7gog.b_sideloader.testing.githubApp
import dev.re7gog.b_sideloader.testing.selfApp
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfirmSelfUpdateUseCaseTest {

    private val pendingSelfUpdates = FakePendingSelfUpdateRepository()

    private fun useCase(
        apps: FakeAppsRepository,
        selfInfo: FakeSelfAppInfo,
    ) = ConfirmSelfUpdateUseCase(pendingSelfUpdates, apps, selfInfo, NoopLogger)

    private fun pending(
        appId: Long = 1L,
        version: String = "2.0.0",
        previousVersionCode: Long = 1L,
    ) = PendingSelfUpdate(
        appId = appId,
        packageName = FakeSelfAppInfo.SELF_PACKAGE,
        version = AppVersion(version),
        previousVersionCode = previousVersionCode,
    )

    /**
     * The ordinary case: the install killed the old process, and the version that came up is the
     * one the record was written for.
     */
    @Test
    fun `a landed update is written to the row the record points at`() = runTest {
        val apps = FakeAppsRepository(listOf(selfApp(id = 1L, version = AppVersion("1.0.0"))))
        pendingSelfUpdates.pending = pending(version = "2.0.0")

        useCase(apps, FakeSelfAppInfo(versionName = "2.0.0", versionCode = 2L))()

        assertEquals("2.0.0", apps.getApps().single().version.raw)
        assertNull(pendingSelfUpdates.pending)
    }

    /**
     * A release that ignores the naming convention still updated the package, and Android only
     * accepts a *higher* version code — so a changed code is proof enough that it landed.
     */
    @Test
    fun `a changed version code counts as landed even when the release was named differently`() = runTest {
        val apps = FakeAppsRepository(listOf(selfApp(id = 1L, version = AppVersion("1.0.0"))))
        pendingSelfUpdates.pending = pending(version = "v2.0.0 hotfix", previousVersionCode = 1L)

        useCase(apps, FakeSelfAppInfo(versionName = "2.0.0", versionCode = 2L))()

        assertEquals("v2.0.0 hotfix", apps.getApps().single().version.raw)
    }

    /** Declining the system dialog leaves the old build running; nothing may be recorded then. */
    @Test
    fun `a record whose update never landed is dropped without touching the row`() = runTest {
        val apps = FakeAppsRepository(listOf(selfApp(id = 1L, version = AppVersion("1.0.0"))))
        pendingSelfUpdates.pending = pending(version = "2.0.0", previousVersionCode = 1L)

        useCase(apps, FakeSelfAppInfo(versionName = "1.0.0", versionCode = 1L))()

        assertEquals("1.0.0", apps.getApps().single().version.raw)
        assertNull(pendingSelfUpdates.pending)
    }

    @Test
    fun `without a record nothing is read or written`() = runTest {
        val apps = FakeAppsRepository(listOf(selfApp(id = 1L, version = AppVersion("1.0.0"))))

        useCase(apps, FakeSelfAppInfo(versionName = "2.0.0", versionCode = 2L))()

        assertEquals("1.0.0", apps.getApps().single().version.raw)
        assertEquals(0, pendingSelfUpdates.clearCount)
    }

    /** The user may have removed the app from the list while the update was installing. */
    @Test
    fun `a record pointing at a deleted row is dropped quietly`() = runTest {
        val apps = FakeAppsRepository(listOf(githubApp(id = 9L)))
        pendingSelfUpdates.pending = pending(appId = 1L, version = "2.0.0")

        useCase(apps, FakeSelfAppInfo(versionName = "2.0.0", versionCode = 2L))()

        assertEquals(1, apps.getApps().size)
        assertNull(pendingSelfUpdates.pending)
    }

    /** Both `MY_PACKAGE_REPLACED` and `Application.onCreate` call this; the second is a no-op. */
    @Test
    fun `running twice writes the version once`() = runTest {
        val apps = FakeAppsRepository(listOf(selfApp(id = 1L, version = AppVersion("1.0.0"))))
        pendingSelfUpdates.pending = pending(version = "2.0.0")
        val selfInfo = FakeSelfAppInfo(versionName = "2.0.0", versionCode = 2L)

        useCase(apps, selfInfo)()
        useCase(apps, selfInfo)()

        assertEquals("2.0.0", apps.getApps().single().version.raw)
        assertEquals(1, pendingSelfUpdates.clearCount)
    }
}
