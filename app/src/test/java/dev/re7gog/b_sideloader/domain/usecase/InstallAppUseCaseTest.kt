package dev.re7gog.b_sideloader.domain.usecase

import dev.re7gog.b_sideloader.core.log.NoopLogger
import dev.re7gog.b_sideloader.domain.error.AppError
import dev.re7gog.b_sideloader.domain.error.InstallFailure
import dev.re7gog.b_sideloader.domain.installer.InstallScheduler
import dev.re7gog.b_sideloader.domain.model.AppVersion
import dev.re7gog.b_sideloader.domain.model.DownloadRef
import dev.re7gog.b_sideloader.domain.model.InstallOutcome
import dev.re7gog.b_sideloader.domain.model.InstallProgress
import dev.re7gog.b_sideloader.domain.model.PendingSelfUpdate
import dev.re7gog.b_sideloader.domain.model.TrackedApp
import dev.re7gog.b_sideloader.domain.model.UpdateCandidate
import dev.re7gog.b_sideloader.testing.FakeAppsRepository
import dev.re7gog.b_sideloader.testing.FakeInstallerGateway
import dev.re7gog.b_sideloader.testing.FakeSelfAppInfo
import dev.re7gog.b_sideloader.testing.FakeSelfUpdateStateRepository
import dev.re7gog.b_sideloader.testing.FakeSettingsRepository
import dev.re7gog.b_sideloader.testing.FakeTelegramRepository
import dev.re7gog.b_sideloader.testing.githubApp
import dev.re7gog.b_sideloader.testing.reconcileSelfUpdate
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
    private val selfUpdates = FakeSelfUpdateStateRepository()
    private val selfInfo = FakeSelfAppInfo(versionCode = 7L)

    private fun useCase(apps: FakeAppsRepository) = InstallAppUseCase(
        installer,
        InstallScheduler(FakeSettingsRepository()),
        apps,
        telegram,
        selfUpdates,
        reconcileSelfUpdate(apps, selfUpdates, selfInfo),
        selfInfo,
        NoopLogger,
    )

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

        val events = useCase(apps).invoke(githubApp(id = TrackedApp.NEW_APP_ID), httpCandidate).toList()

        // Every phase in order — waiting, downloading, waiting for the installer, installing —
        // and only then the verdict.
        val progress = events.dropLast(1).map { (it as AppInstallEvent.Progress).progress }
        assertEquals(
            listOf(
                InstallProgress.Queued,
                InstallProgress.Preparing,
                InstallProgress.Downloading(0.5f),
                InstallProgress.Queued,
                InstallProgress.Staging(0.5f),
            ),
            progress,
        )
        assertTrue(events.last() is AppInstallEvent.Completed)
    }

    /**
     * The write-ahead record for a self-update has to exist *before* the install starts: the
     * process is killed the moment the package is replaced, so anything written afterwards is
     * written by nobody. And this process has to have reconciled first, so that the version code
     * it remembers is the one the next process compares against.
     */
    @Test
    fun `installing over ourselves records the pending release before the install`() = runTest {
        val self = selfApp(id = 3L, version = AppVersion("1.0.0"))
        val apps = FakeAppsRepository(listOf(self))
        var recordedWhenInstallStarted: PendingSelfUpdate? = null
        var rememberedWhenInstallStarted: Long? = null
        installer.onInstall = {
            recordedWhenInstallStarted = selfUpdates.pending
            rememberedWhenInstallStarted = selfUpdates.rememberedVersionCode
        }

        useCase(apps).invoke(self, httpCandidate).toList()

        assertEquals(
            PendingSelfUpdate(appId = 3L, releaseName = AppVersion("v2.0")),
            recordedWhenInstallStarted,
        )
        assertEquals(7L, rememberedWhenInstallStarted)
    }

    /**
     * Even a success this process lives to see is not written: only the next process's version
     * code can prove the replace happened, so the record stays for it to judge.
     */
    @Test
    fun `a successful self-install leaves the row to the next process`() = runTest {
        val self = selfApp(id = 3L, version = AppVersion("1.0.0"))
        val apps = FakeAppsRepository(listOf(self))

        val events = useCase(apps).invoke(self, httpCandidate).toList()

        assertEquals("1.0.0", apps.getApps().single().version.raw)
        assertEquals(AppVersion("v2.0"), selfUpdates.pending?.releaseName)
        // The screen is still told what was installed, so it stops offering the update.
        val completed = events.filterIsInstance<AppInstallEvent.Completed>().single()
        assertEquals("v2.0", completed.app.version.raw)
    }

    /** A failure this process sees is final; there is nothing left for the next one to judge. */
    @Test
    fun `a failed self-install drops the record`() = runTest {
        installer.outcome = InstallOutcome.Failure(AppError.Install(InstallFailure.Aborted))
        val self = selfApp(id = 3L)
        val apps = FakeAppsRepository(listOf(self))

        useCase(apps).invoke(self, httpCandidate).toList()

        assertNull(selfUpdates.pending)
        assertEquals("1.0.0", apps.getApps().single().version.raw)
    }

    /** Every other app writes its version the normal way, so there is nothing to write ahead. */
    @Test
    fun `installing another app records nothing`() = runTest {
        val other = githubApp(id = 5L, packageName = "com.example")
        val apps = FakeAppsRepository(listOf(other))

        useCase(apps).invoke(other, httpCandidate).toList()

        assertNull(selfUpdates.pending)
        assertEquals(0, selfUpdates.writeCount)
    }

    /**
     * The background sweep must not download an update only to raise a dialog nobody sees. When
     * the gateway says the update would ask, it ends there, before the queue and the download.
     */
    @Test
    fun `a background update that would ask ends before downloading`() = runTest {
        installer.requiringConfirmation += "com.example"
        val saved = githubApp(id = 5L, version = AppVersion("v1.0"))
        val apps = FakeAppsRepository(listOf(saved))

        val events = useCase(apps).invoke(saved, httpCandidate, interactive = false).toList()

        assertEquals(listOf(AppInstallEvent.NeedsConfirmation), events)
        assertTrue(installer.installed.isEmpty())
        assertEquals("v1.0", apps.getApps().single().version.raw)
    }

    /** The user is there to answer: nothing is predicted, and the installer may ask. */
    @Test
    fun `an interactive install is never held back by the prediction`() = runTest {
        installer.requiringConfirmation += "com.example"
        val saved = githubApp(id = 5L, version = AppVersion("v1.0"))

        val events = useCase(FakeAppsRepository(listOf(saved))).invoke(saved, httpCandidate, interactive = true).toList()

        assertTrue(installer.confirmationChecks.isEmpty())
        assertEquals(listOf(true), installer.committedInteractive)
        assertTrue(events.last() is AppInstallEvent.Completed)
    }

    @Test
    fun `an install the system wanted confirmed writes nothing and is not a failure`() = runTest {
        installer.outcome = InstallOutcome.NeedsConfirmation
        val saved = githubApp(id = 5L, version = AppVersion("v1.0"))
        val apps = FakeAppsRepository(listOf(saved))

        val events = useCase(apps).invoke(saved, httpCandidate).toList()

        assertEquals(listOf(false), installer.committedInteractive)
        assertEquals(AppInstallEvent.NeedsConfirmation, events.last())
        assertEquals("v1.0", apps.getApps().single().version.raw)
    }

    /** Nothing was installed over this process, so there is nothing for the next one to judge. */
    @Test
    fun `a self-install that needed confirming drops the record`() = runTest {
        installer.outcome = InstallOutcome.NeedsConfirmation
        val self = selfApp(id = 3L)
        val apps = FakeAppsRepository(listOf(self))

        useCase(apps).invoke(self, httpCandidate).toList()

        assertNull(selfUpdates.pending)
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
