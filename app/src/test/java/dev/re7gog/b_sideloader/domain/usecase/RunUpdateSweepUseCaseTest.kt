package dev.re7gog.b_sideloader.domain.usecase

import dev.re7gog.b_sideloader.core.log.NoopLogger
import dev.re7gog.b_sideloader.domain.error.AppError
import dev.re7gog.b_sideloader.domain.model.AppSettings
import dev.re7gog.b_sideloader.domain.model.AppVersion
import dev.re7gog.b_sideloader.domain.model.InstallerMode
import dev.re7gog.b_sideloader.testing.FakeAppsRepository
import dev.re7gog.b_sideloader.testing.FakeDeviceInfo
import dev.re7gog.b_sideloader.testing.FakeGithubRepository
import dev.re7gog.b_sideloader.testing.FakeInstallerGateway
import dev.re7gog.b_sideloader.testing.FakeSettingsRepository
import dev.re7gog.b_sideloader.testing.FakeTelegramRepository
import dev.re7gog.b_sideloader.testing.asset
import dev.re7gog.b_sideloader.testing.githubApp
import dev.re7gog.b_sideloader.testing.release
import kotlinx.coroutines.CancellationException
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

    private fun sweep(
        apps: FakeAppsRepository,
        settings: FakeSettingsRepository = FakeSettingsRepository(),
    ): RunUpdateSweepUseCase {
        val resolve = ResolveUpdateUseCase(github, telegram, deviceInfo)
        val install = InstallAppUseCase(installer, apps, telegram, NoopLogger)
        return RunUpdateSweepUseCase(apps, settings, resolve, install, deviceInfo, NoopLogger)
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
     * Without a privileged installer and without Android 12's silent self-update, committing a
     * session would throw a dialog at a user who is not looking at the phone — so the sweep may
     * only report.
     */
    @Test
    fun `falls back to check-only when silent installs are impossible`() = runTest {
        val apps = FakeAppsRepository(listOf(githubApp(id = 1, name = "A", version = AppVersion("v1.0"))))
        val restricted = RunUpdateSweepUseCase(
            apps,
            FakeSettingsRepository(AppSettings(installerMode = InstallerMode.Session)),
            ResolveUpdateUseCase(github, telegram, FakeDeviceInfo(supportsSilentSelfUpdates = false)),
            InstallAppUseCase(installer, apps, telegram, NoopLogger),
            FakeDeviceInfo(supportsSilentSelfUpdates = false),
            NoopLogger,
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
        val useCase = RunUpdateSweepUseCase(
            apps,
            FakeSettingsRepository(),
            ResolveUpdateUseCase(flaky, telegram, deviceInfo),
            InstallAppUseCase(installer, apps, telegram, NoopLogger),
            deviceInfo,
            NoopLogger,
        )

        val report = useCase()

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
        val useCase = RunUpdateSweepUseCase(
            apps,
            FakeSettingsRepository(),
            ResolveUpdateUseCase(cancelling, telegram, deviceInfo),
            InstallAppUseCase(installer, apps, telegram, NoopLogger),
            deviceInfo,
            NoopLogger,
        )

        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking { useCase() }
        }
    }

    /** Lets a test override one method without reimplementing the whole interface. */
    private open class GithubRepositoryDelegate(
        private val delegate: FakeGithubRepository,
    ) : dev.re7gog.b_sideloader.domain.repository.GithubRepository by delegate
}
