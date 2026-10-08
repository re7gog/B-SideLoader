package dev.re7gog.b_sideloader.domain.usecase

import dev.re7gog.b_sideloader.core.log.NoopLogger
import dev.re7gog.b_sideloader.domain.error.AppError
import dev.re7gog.b_sideloader.domain.error.InstallFailure
import dev.re7gog.b_sideloader.domain.installer.InstallScheduler
import dev.re7gog.b_sideloader.domain.model.AppSettings
import dev.re7gog.b_sideloader.domain.model.AppVersion
import dev.re7gog.b_sideloader.domain.model.DownloadRef
import dev.re7gog.b_sideloader.domain.model.InstallOutcome
import dev.re7gog.b_sideloader.domain.model.InstallProgress
import dev.re7gog.b_sideloader.domain.model.TrackedApp
import dev.re7gog.b_sideloader.domain.model.UpdateCandidate
import dev.re7gog.b_sideloader.testing.FakeAppsRepository
import dev.re7gog.b_sideloader.testing.FakeInstallerGateway
import dev.re7gog.b_sideloader.testing.FakeSelfAppInfo
import dev.re7gog.b_sideloader.testing.FakeSelfUpdateStateRepository
import dev.re7gog.b_sideloader.testing.FakeSettingsRepository
import dev.re7gog.b_sideloader.testing.FakeTelegramRepository
import dev.re7gog.b_sideloader.testing.githubApp
import dev.re7gog.b_sideloader.testing.telegramApp
import dev.re7gog.b_sideloader.testing.reconcileSelfUpdate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallCoordinatorTest {

    private val installer = FakeInstallerGateway()
    private val apps = FakeAppsRepository(
        listOf(
            githubApp(id = 1L, name = "Alpha", version = AppVersion("v1.0")),
            githubApp(id = 2L, name = "Beta", version = AppVersion("v1.0")),
            githubApp(id = 3L, name = "Gamma", version = AppVersion("v1.0")),
            githubApp(id = 4L, name = "Delta", version = AppVersion("v1.0")),
            telegramApp(id = 5L, name = "Channel app", version = AppVersion("v1.0")),
        ),
    )

    private val candidate = UpdateCandidate(
        version = AppVersion("v2.0"),
        download = DownloadRef.Http("https://example.test/app.apk"),
        fileName = "app.apk",
    )

    /**
     * `backgroundScope`: the installs run on the test's scheduler and die with the test. Note that
     * `advanceUntilIdle` does not drain background work, hence `runCurrent` throughout.
     */
    private fun TestScope.coordinator(
        settings: FakeSettingsRepository = FakeSettingsRepository(),
    ): InstallCoordinator {
        val selfUpdates = FakeSelfUpdateStateRepository()
        val selfInfo = FakeSelfAppInfo()
        return InstallCoordinator(
            installApp = InstallAppUseCase(
                installer,
                InstallScheduler(settings),
                apps,
                FakeTelegramRepository(),
                selfUpdates,
                reconcileSelfUpdate(apps, selfUpdates, selfInfo),
                selfInfo,
                NoopLogger,
            ),
            scope = backgroundScope,
            logger = NoopLogger,
        )
    }

    /** What lets the list and the details page show one bar: progress is published by app. */
    @Test
    fun `progress is published under the app's key until the install ends`() = runTest {
        val gate = CompletableDeferred<Unit>()
        installer.beforeVerdict = { gate.await() }
        val coordinator = coordinator()
        val alpha = apps.getApp(1L)!!

        val key = coordinator.install(alpha, candidate)
        runCurrent()

        assertEquals(InstallKey.App(1L), key)
        assertEquals(InstallProgress.Staging(0.5f), coordinator.installs.value[key])

        gate.complete(Unit)
        runCurrent()

        assertTrue(coordinator.installs.value.isEmpty())
        assertEquals("v2.0", apps.getApp(1L)?.version?.raw)
    }

    /** "Update" on the list, then "Update" on the app's page: one install, not two. */
    @Test
    fun `an app already installing is not queued again`() = runTest {
        installer.beforeVerdict = { CompletableDeferred<Unit>().await() }
        val coordinator = coordinator()
        val alpha = apps.getApp(1L)!!

        assertNotNull(coordinator.install(alpha, candidate))
        assertNull(coordinator.install(alpha, candidate))
        runCurrent()

        assertEquals(1, installer.installed.size)
    }

    /**
     * Sessions do not overlap sanely, so a downloaded app waits for the installer — and is shown
     * as queued meanwhile rather than looking like nothing happened.
     */
    @Test
    fun `installs run one at a time and a waiting one reports queued`() = runTest {
        val gate = CompletableDeferred<Unit>()
        installer.beforeVerdict = { gate.await() }
        val coordinator = coordinator()

        coordinator.install(apps.getApp(1L)!!, candidate)
        coordinator.install(apps.getApp(2L)!!, candidate)
        runCurrent()

        // Both downloaded — one after the other, as they share a source — but only one installs.
        assertEquals(2, installer.installed.size)
        assertEquals(1, installer.committed.size)
        assertEquals(InstallProgress.Queued, coordinator.installs.value[InstallKey.App(2L)])

        installer.beforeVerdict = {}
        gate.complete(Unit)
        runCurrent()

        assertEquals(2, installer.committed.size)
        assertTrue(coordinator.installs.value.isEmpty())
    }

    /** Off by default: one download per source, but GitHub and Telegram never wait for each other. */
    @Test
    fun `downloads from different sources run side by side`() = runTest {
        val gate = CompletableDeferred<Unit>()
        installer.duringDownload = { gate.await() }
        val coordinator = coordinator()

        listOf(1L, 2L, 5L).forEach { coordinator.install(apps.getApp(it)!!, candidate) }
        runCurrent()

        val installs = coordinator.installs.value
        assertEquals(InstallProgress.Downloading(0.5f), installs[InstallKey.App(1L)])
        assertEquals(InstallProgress.Queued, installs[InstallKey.App(2L)])
        assertEquals(InstallProgress.Downloading(0.5f), installs[InstallKey.App(5L)])

        gate.complete(Unit)
        runCurrent()

        assertTrue(coordinator.installs.value.isEmpty())
        assertEquals(3, installer.committed.size)
    }

    @Test
    fun `with parallel updates on, three apps download from one source at once`() = runTest {
        val gate = CompletableDeferred<Unit>()
        installer.duringDownload = { gate.await() }
        val coordinator = coordinator(FakeSettingsRepository(AppSettings(parallelUpdates = true)))

        listOf(1L, 2L, 3L, 4L).forEach { coordinator.install(apps.getApp(it)!!, candidate) }
        runCurrent()

        val installs = coordinator.installs.value
        assertEquals(3, installs.values.count { it == InstallProgress.Downloading(0.5f) })
        assertEquals(InstallProgress.Queued, installs[InstallKey.App(4L)])

        gate.complete(Unit)
        runCurrent()

        assertEquals(4, installer.committed.size)
    }

    /** A failed download installs nothing, and leaves nothing to clean up. */
    @Test
    fun `a failed download is reported and installs nothing`() = runTest {
        installer.downloadFailure = AppError.Http(404, "Not Found")
        val coordinator = coordinator()
        val result = backgroundScope.async { coordinator.results.first() }
        runCurrent()

        coordinator.install(apps.getApp(1L)!!, candidate)
        runCurrent()

        assertTrue((result.await() as InstallResult.Failed).error is AppError.Http)
        assertTrue(installer.committed.isEmpty())
        assertTrue(installer.discarded.isEmpty())
    }

    /** The downloaded APK is handed back whatever happens — here, after a successful install. */
    @Test
    fun `the downloaded APK is discarded after the install`() = runTest {
        val coordinator = coordinator()

        coordinator.install(apps.getApp(1L)!!, candidate)
        runCurrent()

        assertEquals(installer.committed, installer.discarded)
        assertEquals(1, installer.discarded.size)
    }

    /**
     * A screen reacting to the result must never see the install gone while it still shows the
     * state from before it — so a collector of both streams handles the result first.
     */
    @Test
    fun `the result is seen before the install leaves the map`() = runTest {
        val coordinator = coordinator()
        val seen = mutableListOf<String>()
        backgroundScope.launch { coordinator.results.collect { seen += "result" } }
        backgroundScope.launch {
            var wasListed = false
            coordinator.installs.collect { installs ->
                val listed = InstallKey.App(1L) in installs
                if (wasListed && !listed) seen += "gone"
                wasListed = listed
            }
        }
        runCurrent()

        coordinator.install(apps.getApp(1L)!!, candidate)
        runCurrent()

        assertEquals(listOf("result", "gone"), seen)
    }

    @Test
    fun `a failure is reported under the app's key`() = runTest {
        val error = AppError.Install(InstallFailure.Aborted)
        installer.outcome = InstallOutcome.Failure(error)
        val coordinator = coordinator()
        val result = backgroundScope.async { coordinator.results.first() }
        runCurrent()

        coordinator.install(apps.getApp(1L)!!, candidate)
        runCurrent()

        val failed = result.await() as InstallResult.Failed
        assertEquals(InstallKey.App(1L), failed.key)
        assertEquals(error, failed.error)
        assertEquals("v1.0", apps.getApp(1L)?.version?.raw)
    }

    /** The sweep's way in: same bookkeeping, but it waits and hands back the result. */
    @Test
    fun `installAndAwait returns the result and forwards progress`() = runTest {
        val coordinator = coordinator()
        val seen = mutableListOf<InstallProgress>()

        val result = coordinator.installAndAwait(apps.getApp(1L)!!, candidate) { seen += it }

        assertTrue(result is InstallResult.Installed)
        assertTrue(InstallProgress.Downloading(0.5f) in seen)
        assertTrue(coordinator.installs.value.isEmpty())
    }

    /**
     * The sweep never asks: an update that would need confirming comes back as such, publishes
     * that to the screens, and leaves the app free for the user to install.
     */
    @Test
    fun `installAndAwait reports an update that needs confirming`() = runTest {
        installer.requiringConfirmation += "com.example"
        val coordinator = coordinator()
        val published = async { coordinator.results.first() }
        runCurrent()

        val result = coordinator.installAndAwait(apps.getApp(1L)!!, candidate)

        assertEquals(InstallResult.NeedsConfirmation(InstallKey.App(1L), apps.getApp(1L)!!), result)
        assertEquals(result, published.await())
        assertTrue(installer.installed.isEmpty())
        assertTrue(coordinator.installs.value.isEmpty())
    }

    /** A screen's install is the user's own: it is allowed to ask. */
    @Test
    fun `install lets the system ask, installAndAwait does not`() = runTest {
        val coordinator = coordinator()

        coordinator.install(apps.getApp(1L)!!, candidate)
        runCurrent()
        coordinator.installAndAwait(apps.getApp(2L)!!, candidate)

        assertEquals(listOf(true, false), installer.committedInteractive)
    }

    /** Joining an install already in flight reports that install, without starting another. */
    @Test
    fun `installAndAwait waits for an install already in flight`() = runTest {
        val gate = CompletableDeferred<Unit>()
        installer.beforeVerdict = { gate.await() }
        val coordinator = coordinator()
        coordinator.install(apps.getApp(1L)!!, candidate)
        runCurrent()

        val result = async { coordinator.installAndAwait(apps.getApp(1L)!!, candidate) }
        runCurrent()
        gate.complete(Unit)

        assertTrue(result.await() is InstallResult.Installed)
        assertEquals(1, installer.installed.size)
    }

    /** WorkManager stopping the worker must stop the install it is running, and free the app. */
    @Test
    fun `cancelling the caller of installAndAwait stops its install`() = runTest {
        installer.beforeVerdict = { CompletableDeferred<Unit>().await() }
        val coordinator = coordinator()
        val job = launch { coordinator.installAndAwait(apps.getApp(1L)!!, candidate) }
        runCurrent()
        assertEquals(setOf(InstallKey.App(1L)), coordinator.installs.value.keys)

        job.cancel()
        runCurrent()

        assertTrue(coordinator.installs.value.isEmpty())
        assertNotNull(coordinator.install(apps.getApp(1L)!!, candidate))
    }

    /** An app opened from search has no row yet; each install of one gets a key of its own. */
    @Test
    fun `unsaved apps get distinct draft keys`() = runTest {
        installer.beforeVerdict = { CompletableDeferred<Unit>().await() }
        val coordinator = coordinator()
        val draft = githubApp(id = TrackedApp.NEW_APP_ID, name = "New")

        val first = coordinator.install(draft, candidate)
        val second = coordinator.install(draft, candidate)

        assertTrue(first is InstallKey.Draft)
        assertTrue(second is InstallKey.Draft)
        assertTrue(first != second)
    }
}
