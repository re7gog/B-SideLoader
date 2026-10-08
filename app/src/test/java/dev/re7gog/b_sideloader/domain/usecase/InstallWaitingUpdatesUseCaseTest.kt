package dev.re7gog.b_sideloader.domain.usecase

import dev.re7gog.b_sideloader.core.log.NoopLogger
import dev.re7gog.b_sideloader.domain.installer.InstallScheduler
import dev.re7gog.b_sideloader.domain.model.AppVersion
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
import dev.re7gog.b_sideloader.testing.reconcileSelfUpdate
import dev.re7gog.b_sideloader.testing.release
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What tapping the "updates available" notification installs, and how. */
class InstallWaitingUpdatesUseCaseTest {

    private val github = FakeGithubRepository(releases = listOf(release("v2.0", assets = arrayOf(asset("app.apk")))))
    private val installer = FakeInstallerGateway()
    private val apps = FakeAppsRepository(
        listOf(
            githubApp(id = 1, name = "Waiting", version = AppVersion("v1.0")),
            githubApp(id = 2, name = "NotOffered", packageName = "com.other", version = AppVersion("v1.0")),
            githubApp(id = 3, name = "UpToDateNow", packageName = "com.current", version = AppVersion("v2.0")),
        ),
    )
    private val packages = FakePackageInspector(installedPackages = setOf("com.example", "com.other", "com.current"))

    private fun TestScope.useCase(): InstallWaitingUpdatesUseCase {
        val settings = FakeSettingsRepository()
        val selfUpdates = FakeSelfUpdateStateRepository()
        val selfInfo = FakeSelfAppInfo()
        val reconcile = reconcileSelfUpdate(apps, selfUpdates, selfInfo)
        return InstallWaitingUpdatesUseCase(
            appsRepository = apps,
            checkUpdates = CheckUpdatesUseCase(
                resolveUpdate = ResolveUpdateUseCase(github, FakeTelegramRepository(), FakeDeviceInfo()),
                settingsRepository = settings,
                packageInspector = packages,
                logger = NoopLogger,
            ),
            installCoordinator = InstallCoordinator(
                installApp = InstallAppUseCase(
                    installer,
                    InstallScheduler(settings),
                    apps,
                    FakeTelegramRepository(),
                    selfUpdates,
                    reconcile,
                    selfInfo,
                    NoopLogger,
                ),
                scope = backgroundScope,
                logger = NoopLogger,
            ),
            reconcileSelfUpdate = reconcile,
            selfApp = selfInfo,
        )
    }

    /**
     * The user is here now, so the install may ask: it is the user's own, with the dialogs and
     * pre-approval that come with that — even for an app the gateway says would ask.
     */
    @Test
    fun `the offered apps are installed as the user's own installs`() = runTest {
        installer.requiringConfirmation += "com.example"

        val started = useCase()(listOf(1L))
        runCurrent()

        assertEquals(1, started)
        assertEquals(listOf(true), installer.committedInteractive)
        assertEquals("v2.0", apps.getApp(1L)!!.version.raw)
        assertEquals("v1.0", apps.getApp(2L)!!.version.raw)
    }

    /** Installed by hand since the notification was posted: checked again, nothing to do. */
    @Test
    fun `an app that no longer has an update is left alone`() = runTest {
        val started = useCase()(listOf(3L))
        runCurrent()

        assertEquals(0, started)
        assertTrue(installer.installed.isEmpty())
    }

    /** Deleted from the list since: its id matches nothing. */
    @Test
    fun `an app that is gone is ignored`() = runTest {
        assertEquals(0, useCase()(listOf(99L)))
    }
}
