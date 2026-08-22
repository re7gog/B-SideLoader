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

    /** What the previous process wrote down before handing the APK to the system. */
    private fun pending(
        appId: Long = 1L,
        version: String = "v2.0.0",
        previousLastUpdateTime: Long = 100L,
        previousVersionCode: Long = 1L,
    ) = PendingSelfUpdate(
        appId = appId,
        packageName = FakeSelfAppInfo.SELF_PACKAGE,
        version = AppVersion(version),
        previousLastUpdateTime = previousLastUpdateTime,
        previousVersionCode = previousVersionCode,
    )

    /**
     * The ordinary case: the install killed the old process, and what came up is a different
     * build, installed at a different time.
     */
    @Test
    fun `a landed update is written to the row the record points at`() = runTest {
        val apps = FakeAppsRepository(listOf(selfApp(id = 1L, version = AppVersion("v1.0.0"))))
        pendingSelfUpdates.pending = pending(version = "v2.0.0")

        useCase(apps, FakeSelfAppInfo(versionCode = 2L, lastUpdateTime = 200L))()

        assertEquals("v2.0.0", apps.getApps().single().version.raw)
        assertNull(pendingSelfUpdates.pending)
    }

    /**
     * The first install through B-SideLoader is a *reinstall* of the build already running: same
     * version code, same everything except the moment it was installed. Recording it is the whole
     * point — it is what turns the seeded "unknown version" row into a tracked one.
     */
    @Test
    fun `reinstalling the running build counts as landed`() = runTest {
        val apps = FakeAppsRepository(listOf(selfApp(id = 1L, version = AppVersion.Unknown)))
        pendingSelfUpdates.pending = pending(
            version = "v1.0.0",
            previousLastUpdateTime = 100L,
            previousVersionCode = 1L,
        )

        useCase(apps, FakeSelfAppInfo(versionCode = 1L, lastUpdateTime = 200L))()

        assertEquals("v1.0.0", apps.getApps().single().version.raw)
    }

    /** A ROM reporting a stale install time must not cost a real update its version write. */
    @Test
    fun `a changed version code alone counts as landed`() = runTest {
        val apps = FakeAppsRepository(listOf(selfApp(id = 1L, version = AppVersion("v1.0.0"))))
        pendingSelfUpdates.pending = pending(previousLastUpdateTime = 100L, previousVersionCode = 1L)

        useCase(apps, FakeSelfAppInfo(versionCode = 2L, lastUpdateTime = 100L))()

        assertEquals("v2.0.0", apps.getApps().single().version.raw)
    }

    /** Declining the system dialog leaves the old build in place; nothing may be recorded then. */
    @Test
    fun `a record whose install never happened is dropped without touching the row`() = runTest {
        val apps = FakeAppsRepository(listOf(selfApp(id = 1L, version = AppVersion("v1.0.0"))))
        pendingSelfUpdates.pending = pending(previousLastUpdateTime = 100L, previousVersionCode = 1L)

        useCase(apps, FakeSelfAppInfo(versionCode = 1L, lastUpdateTime = 100L))()

        assertEquals("v1.0.0", apps.getApps().single().version.raw)
        assertNull(pendingSelfUpdates.pending)
    }

    @Test
    fun `without a record nothing is read or written`() = runTest {
        val apps = FakeAppsRepository(listOf(selfApp(id = 1L, version = AppVersion("v1.0.0"))))

        useCase(apps, FakeSelfAppInfo(versionCode = 2L, lastUpdateTime = 200L))()

        assertEquals("v1.0.0", apps.getApps().single().version.raw)
        assertEquals(0, pendingSelfUpdates.clearCount)
    }

    /** The user may have removed the app from the list while the update was installing. */
    @Test
    fun `a record pointing at a deleted row is dropped quietly`() = runTest {
        val apps = FakeAppsRepository(listOf(githubApp(id = 9L)))
        pendingSelfUpdates.pending = pending(appId = 1L)

        useCase(apps, FakeSelfAppInfo(versionCode = 2L, lastUpdateTime = 200L))()

        assertEquals(1, apps.getApps().size)
        assertNull(pendingSelfUpdates.pending)
    }

    /** Both `MY_PACKAGE_REPLACED` and `Application.onCreate` call this; the second is a no-op. */
    @Test
    fun `running twice writes the version once`() = runTest {
        val apps = FakeAppsRepository(listOf(selfApp(id = 1L, version = AppVersion("v1.0.0"))))
        pendingSelfUpdates.pending = pending()
        val selfInfo = FakeSelfAppInfo(versionCode = 2L, lastUpdateTime = 200L)

        useCase(apps, selfInfo)()
        useCase(apps, selfInfo)()

        assertEquals("v2.0.0", apps.getApps().single().version.raw)
        assertEquals(1, pendingSelfUpdates.clearCount)
    }
}
