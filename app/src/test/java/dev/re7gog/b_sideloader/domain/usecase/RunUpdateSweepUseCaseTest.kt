package dev.re7gog.b_sideloader.domain.usecase

import dev.re7gog.b_sideloader.core.log.NoopLogger
import dev.re7gog.b_sideloader.domain.error.AppError
import dev.re7gog.b_sideloader.domain.model.AppSettings
import dev.re7gog.b_sideloader.domain.model.AppVersion
import dev.re7gog.b_sideloader.domain.model.PendingSelfUpdate
import dev.re7gog.b_sideloader.domain.model.InstallerMode
import dev.re7gog.b_sideloader.domain.model.SelfApp
import dev.re7gog.b_sideloader.testing.FakeAppsRepository
import dev.re7gog.b_sideloader.testing.FakeDeviceInfo
import dev.re7gog.b_sideloader.testing.FakeGithubRepository
import dev.re7gog.b_sideloader.testing.FakeInstallerGateway
import dev.re7gog.b_sideloader.testing.FakePackageInspector
import dev.re7gog.b_sideloader.testing.FakeSelfAppInfo
import dev.re7gog.b_sideloader.testing.FakeSelfUpdateStateRepository
import dev.re7gog.b_sideloader.testing.FakeSettingsRepository
import dev.re7gog.b_sideloader.testing.FakeTelegramRepository
import dev.re7gog.b_sideloader.testing.asset
import dev.re7gog.b_sideloader.testing.githubApp
import dev.re7gog.b_sideloader.testing.release
import dev.re7gog.b_sideloader.testing.selfApp
import dev.re7gog.b_sideloader.domain.model.InstallProgress
import dev.re7gog.b_sideloader.testing.updateCandidate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RunUpdateSweepUseCaseTest {

    private val github = FakeGithubRepository(
        releases = listOf(release("v2.0", assets = arrayOf(asset("app.apk")))),
    )
    private val telegram = FakeTelegramRepository()
    private val installer = FakeInstallerGateway()
    private val deviceInfo = FakeDeviceInfo()

    /** Every fixture app uses `com.example`, so this is "the tracked app is on the device". */
    private val packages = FakePackageInspector(installedPackages = setOf("com.example"))
    private val selfInfo = FakeSelfAppInfo()
    private val selfUpdates = FakeSelfUpdateStateRepository()

    /** The app-wide installs, as the screens see them. Built by [sweep]. */
    private lateinit var installs: InstallCoordinator

    private fun sweep(
        apps: FakeAppsRepository,
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        github: dev.re7gog.b_sideloader.domain.repository.GithubRepository = this.github,
        deviceInfo: FakeDeviceInfo = this.deviceInfo,
        packages: FakePackageInspector = this.packages,
        selfInfo: FakeSelfAppInfo = this.selfInfo,
        scope: CoroutineScope = CoroutineScope(SupervisorJob()),
    ): RunUpdateSweepUseCase {
        val reconcile = ReconcileSelfUpdateUseCase(selfUpdates, apps, selfInfo, NoopLogger)
        installs = InstallCoordinator(
            installApp = InstallAppUseCase(
                installer,
                apps,
                telegram,
                selfUpdates,
                reconcile,
                selfInfo,
                NoopLogger,
            ),
            scope = scope,
            logger = NoopLogger,
        )
        return RunUpdateSweepUseCase(
            appsRepository = apps,
            settingsRepository = settings,
            checkUpdates = CheckUpdatesUseCase(
                resolveUpdate = ResolveUpdateUseCase(github, telegram, deviceInfo),
                settingsRepository = settings,
                packageInspector = packages,
                logger = NoopLogger,
            ),
            installCoordinator = installs,
            reconcileSelfUpdate = reconcile,
            deviceInfo = deviceInfo,
            selfApp = selfInfo,
        )
    }

    @Test
    fun `reports and installs an outdated app`() = runTest {
        val apps = FakeAppsRepository(listOf(githubApp(id = 1, name = "A", version = AppVersion("v1.0"))))

        val report = sweep(apps)()

        assertEquals(1, report.checked)
        assertEquals(listOf("A"), report.withUpdates)
        assertEquals(listOf("A"), report.installed)
        assertEquals("v2.0", apps.getApps().single().version.raw)
    }

    /**
     * Installing B-SideLoader replaces the process running the sweep, so anything queued behind it
     * would never be reached. The check order puts it first here on purpose.
     */
    @Test
    fun `B-SideLoader is installed after everything else`() = runTest {
        val apps = FakeAppsRepository(
            listOf(
                selfApp(id = 1, version = AppVersion("1.0.0")),
                githubApp(id = 2, name = "A", version = AppVersion("v1.0")),
            )
        )
        val packages = FakePackageInspector(
            installedPackages = setOf("com.example", FakeSelfAppInfo.SELF_PACKAGE),
        )

        val report = sweep(apps, packages = packages)()

        assertEquals(listOf(SelfApp.NAME, "A"), report.withUpdates)
        assertEquals(listOf("A", SelfApp.NAME), report.installed)
    }

    /**
     * A self-update kills the worker that installed it, and the next sweep runs in the new build's
     * process. Its row must be reconciled before the check, or the sweep would find — and
     * install — the very update that just landed.
     */
    @Test
    fun `a self-update that just landed is recorded before the check`() = runTest {
        val apps = FakeAppsRepository(listOf(selfApp(id = 1, version = AppVersion("v1.0"))))
        selfUpdates.rememberedVersionCode = 1L
        selfUpdates.pending = PendingSelfUpdate(appId = 1, releaseName = AppVersion("v2.0"))
        val packages = FakePackageInspector(installedPackages = setOf(FakeSelfAppInfo.SELF_PACKAGE))

        val report = sweep(apps, packages = packages, selfInfo = FakeSelfAppInfo(versionCode = 2L))()

        assertEquals(emptyList<String>(), report.withUpdates)
        assertEquals(emptyList<String>(), report.installed)
        assertEquals("v2.0", apps.getApps().single().version.raw)
    }

    @Test
    fun `an up to date app is not installed`() = runTest {
        val apps = FakeAppsRepository(listOf(githubApp(id = 1, version = AppVersion("v2.0"))))

        val report = sweep(apps)()

        assertEquals(1, report.checked)
        assertTrue(report.withUpdates.isEmpty())
        assertTrue(installer.installed.isEmpty())
    }

    @Test
    fun `apps with autoupdate off are skipped entirely`() = runTest {
        val apps = FakeAppsRepository(
            listOf(githubApp(id = 1, version = AppVersion("v1.0"), autoUpdate = false)),
        )

        val report = sweep(apps)()

        assertEquals(0, report.checked)
    }

    /**
     * A tracked app that is not on the device has nothing to update, so querying its source spends
     * a request — and a slice of GitHub's hourly quota — to learn nothing. Asserting on the
     * repository's call count rather than on the report is the point: the report being empty would
     * also be true if the request had been made and its result discarded.
     */
    @Test
    fun `apps that are not installed are never queried`() = runTest {
        val apps = FakeAppsRepository(
            listOf(
                githubApp(id = 1, name = "Gone", packageName = "com.gone", version = AppVersion("v1.0")),
                githubApp(id = 2, name = "Here", version = AppVersion("v1.0")),
            ),
        )

        val report = sweep(apps)()

        assertEquals(1, github.releaseCallCount)
        assertEquals(1, report.checked)
        assertEquals(listOf("Here"), report.withUpdates)
    }

    /** Checking several apps at once must reach the same verdict as checking them one by one. */
    @Test
    fun `parallel checks find the same updates`() = runTest {
        val apps = FakeAppsRepository(
            listOf(
                githubApp(id = 1, name = "A", version = AppVersion("v1.0")),
                githubApp(id = 2, name = "B", version = AppVersion("v1.0")),
                githubApp(id = 3, name = "C", version = AppVersion("v2.0")),
            ),
        )
        val settings = FakeSettingsRepository(AppSettings(parallelUpdateChecks = true))

        val report = sweep(apps, settings)()

        assertEquals(3, report.checked)
        assertEquals(setOf("A", "B"), report.withUpdates.toSet())
        assertEquals(setOf("A", "B"), report.installed.toSet())
    }

    /**
     * Without a privileged installer and without Android 12's silent self-update, committing a
     * session would throw a dialog at a user who is not looking at the phone — so the sweep may
     * only report.
     */
    @Test
    fun `falls back to check-only when silent installs are impossible`() = runTest {
        val apps = FakeAppsRepository(listOf(githubApp(id = 1, name = "A", version = AppVersion("v1.0"))))
        val restricted = sweep(
            apps = apps,
            settings = FakeSettingsRepository(AppSettings(installerMode = InstallerMode.Session)),
            deviceInfo = FakeDeviceInfo(supportsSilentSelfUpdates = false),
        )

        val report = restricted()

        assertEquals(listOf("A"), report.withUpdates)
        assertTrue(report.installed.isEmpty())
        assertTrue(installer.installed.isEmpty())
    }

    /** One rate-limited repository must not stop the other twenty apps from updating. */
    @Test
    fun `one failing app does not abort the sweep`() = runTest {
        val apps = FakeAppsRepository(
            listOf(
                githubApp(id = 1, name = "Broken", version = AppVersion("v1.0")),
                githubApp(id = 2, name = "Fine", version = AppVersion("v1.0")),
            ),
        )
        var call = 0
        val flaky = object : GithubRepositoryDelegate(github) {
            override suspend fun getReleases(owner: String, repo: String, page: Int?) =
                if (call++ == 0) throw AppError.RateLimited() else super.getReleases(owner, repo, page)
        }

        val report = sweep(apps, github = flaky)()

        assertEquals(2, report.checked)
        assertEquals(listOf("Fine"), report.installed)
        assertEquals(listOf("Broken"), report.failed.map { it.appName })
        assertTrue(report.failed.single().error is AppError.RateLimited)
    }

    /**
     * Cancellation is not a failure. It has to propagate so that WorkManager stopping the worker
     * actually stops the work, instead of being caught by the per-app error handling.
     */
    @Test
    fun `cancellation propagates instead of being reported as a failure`() = runTest {
        val apps = FakeAppsRepository(listOf(githubApp(id = 1, version = AppVersion("v1.0"))))
        val cancelling = object : GithubRepositoryDelegate(github) {
            override suspend fun getReleases(owner: String, repo: String, page: Int?): Nothing =
                throw CancellationException("stopped")
        }
        val useCase = sweep(apps, github = cancelling)

        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking { useCase() }
        }
    }

    /** A background update is an install like any other: the screens show its progress. */
    @Test
    fun `a background install shows in the shared installs`() = runTest {
        val gate = CompletableDeferred<Unit>()
        installer.beforeVerdict = { gate.await() }
        val apps = FakeAppsRepository(listOf(githubApp(id = 1, name = "A", version = AppVersion("v1.0"))))
        val useCase = sweep(apps, scope = backgroundScope)

        val report = async { useCase() }
        runCurrent()

        assertEquals(InstallProgress.Downloading(0.5f), installs.installs.value[InstallKey.App(1L)])

        gate.complete(Unit)
        assertEquals(listOf("A"), report.await().installed)
        assertTrue(installs.installs.value.isEmpty())
    }

    /**
     * The user tapped "Update" a moment before the sweep reached the same app. The sweep must wait
     * for that install and report its result — not start a second one over it.
     */
    @Test
    fun `an app the user is already installing is waited for, not installed twice`() = runTest {
        val gate = CompletableDeferred<Unit>()
        installer.beforeVerdict = { gate.await() }
        val apps = FakeAppsRepository(listOf(githubApp(id = 1, name = "A", version = AppVersion("v1.0"))))
        val useCase = sweep(apps, scope = backgroundScope)
        installs.install(apps.getApp(1L)!!, updateCandidate("v2.0"))
        runCurrent()

        val report = async { useCase() }
        runCurrent()
        gate.complete(Unit)

        assertEquals(listOf("A"), report.await().installed)
        assertEquals(1, installer.installed.size)
    }

    /** Lets a test override one method without reimplementing the whole interface. */
    private open class GithubRepositoryDelegate(
        private val delegate: FakeGithubRepository,
    ) : dev.re7gog.b_sideloader.domain.repository.GithubRepository by delegate
}
