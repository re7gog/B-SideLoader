package dev.re7gog.b_sideloader.domain.usecase

import app.cash.turbine.test
import dev.re7gog.b_sideloader.core.log.NoopLogger
import dev.re7gog.b_sideloader.domain.error.AppError
import dev.re7gog.b_sideloader.domain.error.InstallFailure
import dev.re7gog.b_sideloader.domain.model.AppVersion
import dev.re7gog.b_sideloader.domain.model.DownloadRef
import dev.re7gog.b_sideloader.domain.model.InstallOutcome
import dev.re7gog.b_sideloader.domain.model.PendingSelfUpdate
import dev.re7gog.b_sideloader.domain.model.TrackedApp
import dev.re7gog.b_sideloader.domain.model.UpdateCandidate
import dev.re7gog.b_sideloader.testing.FakeAppsRepository
import dev.re7gog.b_sideloader.testing.FakeInstallerGateway
import dev.re7gog.b_sideloader.testing.FakePendingSelfUpdateRepository
import dev.re7gog.b_sideloader.testing.FakeSelfAppInfo
import dev.re7gog.b_sideloader.testing.FakeTelegramRepository
import dev.re7gog.b_sideloader.testing.githubApp
import dev.re7gog.b_sideloader.testing.selfApp
import dev.re7gog.b_sideloader.testing.telegramApp
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallAppUseCaseTest {

    private val installer = FakeInstallerGateway()
    private val telegram = FakeTelegramRepository()
    private val pendingSelfUpdates = FakePendingSelfUpdateRepository()
    private val selfInfo = FakeSelfAppInfo(versionCode = 7L)

    private fun useCase(apps: FakeAppsRepository) =
        InstallAppUseCase(installer, apps, telegram, pendingSelfUpdates, selfInfo, NoopLogger)

    private val httpCandidate = UpdateCandidate(
        version = AppVersion("v2.0"),
        download = DownloadRef.Http("https://example.test/app.apk"),
        fileName = "app.apk",
    )

    @Test
    fun `a saved app is updated in place with the installed version`() = runTest {
        val saved = githubApp(id = 5L, version = AppVersion("v1.0"))
        val apps = FakeAppsRepository(listOf(saved))

        val events = useCase(apps).invoke(saved, httpCandidate).toList()

        val completed = events.filterIsInstance<AppInstallEvent.Completed>().single()
        assertEquals(5L, completed.app.id)
        assertEquals("v2.0", completed.app.version.raw)
        assertEquals("v2.0", apps.getApps().single().version.raw)
    }

    /**
     * An app opened from search has no row yet; the successful install is what saves it, and the
     * caller needs the assigned id back to switch the page into "saved" mode.
     */
    @Test
    fun `an unsaved app is inserted on success and gains an id`() = runTest {
        val fresh = githubApp(id = TrackedApp.NEW_APP_ID, packageName = "")
        val apps = FakeAppsRepository()

        val events = useCase(apps).invoke(fresh, httpCandidate).toList()

        val completed = events.filterIsInstance<AppInstallEvent.Completed>().single()
        assertNotEquals(TrackedApp.NEW_APP_ID, completed.app.id)
        assertEquals(1, apps.getApps().size)
    }

    /** The installer reports the real package name; a searched app has none until then. */
    @Test
    fun `package name reported by the installer is stored`() = runTest {
        installer.outcome = InstallOutcome.Success("com.installed.pkg")
        val apps = FakeAppsRepository()

        useCase(apps).invoke(githubApp(id = TrackedApp.NEW_APP_ID, packageName = ""), httpCandidate).toList()

        assertEquals("com.installed.pkg", apps.getApps().single().packageName)
    }

    @Test
    fun `a failed install writes nothing`() = runTest {
        installer.outcome = InstallOutcome.Failure(AppError.Install(InstallFailure.Aborted))
        val apps = FakeAppsRepository()

        val events = useCase(apps).invoke(githubApp(id = TrackedApp.NEW_APP_ID), httpCandidate).toList()

        assertTrue(events.last() is AppInstallEvent.Failed)
        assertTrue(apps.getApps().isEmpty())
    }

    @Test
    fun `progress is forwarded before the terminal event`() = runTest {
        val apps = FakeAppsRepository()

        useCase(apps).invoke(githubApp(id = TrackedApp.NEW_APP_ID), httpCandidate).test {
            assertTrue(awaitItem() is AppInstallEvent.Progress)
            assertTrue(awaitItem() is AppInstallEvent.Progress)
            assertTrue(awaitItem() is AppInstallEvent.Completed)
            awaitComplete()
        }
    }

    /**
     * The write-ahead record for a self-update has to exist *before* the install starts: the
     * process is killed the moment the package is replaced, so anything written afterwards is
     * written by nobody.
     */
    @Test
    fun `installing over ourselves records the pending version before the install`() = runTest {
        val self = selfApp(id = 3L, version = AppVersion("1.0.0"))
        val apps = FakeAppsRepository(listOf(self))
        var recordedWhenInstallStarted: PendingSelfUpdate? = null
        installer.onInstall = { recordedWhenInstallStarted = pendingSelfUpdates.pending }

        useCase(apps).invoke(self, httpCandidate).toList()

        val pending = recordedWhenInstallStarted!!
        assertEquals(3L, pending.appId)
        assertEquals("v2.0", pending.version.raw)
        assertEquals(FakeSelfAppInfo.SELF_PACKAGE, pending.packageName)
        assertEquals(7L, pending.previousVersionCode)
    }

    /** Surviving to the end means the ordinary write happened, so the record is just litter. */
    @Test
    fun `the pending record is dropped when the process outlives the install`() = runTest {
        val self = selfApp(id = 3L, version = AppVersion("1.0.0"))
        val apps = FakeAppsRepository(listOf(self))

        useCase(apps).invoke(self, httpCandidate).toList()

        assertNull(pendingSelfUpdates.pending)
        assertEquals("v2.0", apps.getApps().single().version.raw)
    }

    @Test
    fun `a failed self-install drops the record too`() = runTest {
        installer.outcome = InstallOutcome.Failure(AppError.Install(InstallFailure.Aborted))
        val self = selfApp(id = 3L)
        val apps = FakeAppsRepository(listOf(self))

        useCase(apps).invoke(self, httpCandidate).toList()

        assertNull(pendingSelfUpdates.pending)
        assertEquals("1.0.0", apps.getApps().single().version.raw)
    }

    /** Every other app writes its version the normal way, so there is nothing to write ahead. */
    @Test
    fun `installing another app records nothing`() = runTest {
        val other = githubApp(id = 5L, packageName = "com.example")
        val apps = FakeAppsRepository(listOf(other))

        useCase(apps).invoke(other, httpCandidate).toList()

        assertNull(pendingSelfUpdates.pending)
        assertEquals(0, pendingSelfUpdates.clearCount)
    }

    /**
     * TDLib keeps a full copy of every file it downloads. Without this the cache grows by one APK
     * per install attempt, which on a phone tracking a dozen apps is gigabytes.
     */
    @Test
    fun `telegram local copy is discarded when the flow ends`() = runTest {
        val candidate = httpCandidate.copy(
            download = DownloadRef.TelegramFile(fileId = 11, sizeBytes = 100L),
        )
        val apps = FakeAppsRepository()

        useCase(apps).invoke(telegramApp(id = TrackedApp.NEW_APP_ID), candidate).toList()

        assertEquals(listOf(11), telegram.discardedFileIds)
    }
}
