package dev.re7gog.b_sideloader.data.background

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import dev.re7gog.b_sideloader.core.log.NoopLogger
import dev.re7gog.b_sideloader.domain.error.AppError
import dev.re7gog.b_sideloader.domain.installer.InstallScheduler
import dev.re7gog.b_sideloader.domain.model.AppSettings
import dev.re7gog.b_sideloader.domain.model.AppVersion
import dev.re7gog.b_sideloader.domain.model.InstallerMode
import dev.re7gog.b_sideloader.domain.usecase.CheckUpdatesUseCase
import dev.re7gog.b_sideloader.domain.usecase.InstallAppUseCase
import dev.re7gog.b_sideloader.domain.usecase.InstallCoordinator
import dev.re7gog.b_sideloader.domain.usecase.ReconcileSelfUpdateUseCase
import dev.re7gog.b_sideloader.domain.usecase.ResolveUpdateUseCase
import dev.re7gog.b_sideloader.domain.usecase.RunUpdateSweepUseCase
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * The periodic job end to end over fakes: the real sweep, the real notifications, and the result
 * WorkManager gets back — which decides whether it retries.
 */
@RunWith(AndroidJUnit4::class)
class UpdateCheckWorkerTest {

    private val application: Application = ApplicationProvider.getApplicationContext()
    private val notificationManager =
        application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val notifications = NotificationCenter(application, NoopLogger)

    /** One GitHub app, installed at v1.0, whose repository has published v2.0. */
    private val apps = FakeAppsRepository(listOf(githubApp(id = 1, name = "Example", version = AppVersion("v1.0"))))
    private val github = FakeGithubRepository(releases = listOf(release("v2.0", assets = arrayOf(asset("app.apk")))))
    private val installer = FakeInstallerGateway()

    @Before
    fun allowNotifications() {
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifications.ensureChannels()
    }

    @Test
    fun anUpToDateSweepSucceedsQuietly() = runTest {
        github.releases = listOf(release("v1.0", assets = arrayOf(asset("app.apk"))))

        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        assertTrue(installer.installed.isEmpty())
        assertNull(updatesAlert())
    }

    @Test
    fun anUpdateThatCanBeInstalledSilentlyIsInstalledWithoutAnAlert() = runTest {
        val result = worker(deviceInfo = FakeDeviceInfo(supportsSilentSelfUpdates = true)).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, installer.installed.size)
        assertNull(updatesAlert())
    }

    /** Without a silent path the sweep only checks, and the user is told instead. */
    @Test
    fun anUpdateThatCannotBeInstalledSilentlyBecomesAnAlert() = runTest {
        val result = worker(deviceInfo = FakeDeviceInfo(supportsSilentSelfUpdates = false)).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(installer.installed.isEmpty())
        val alert = checkNotNull(updatesAlert())
        assertEquals("Example", alert.extras.getCharSequence(NotificationCompat.EXTRA_BIG_TEXT)?.toString())
    }

    @Test
    fun aFailedCheckAsksWorkManagerToRetry() = runTest {
        github.failure = AppError.Network()

        assertEquals(ListenableWorker.Result.retry(), worker(runAttemptCount = 0).doWork())
    }

    /** Retrying forever would keep a broken source hammering the network every backoff period. */
    @Test
    fun aFailedCheckGivesUpOnceTheAttemptsAreSpent() = runTest {
        github.failure = AppError.Network()

        assertEquals(ListenableWorker.Result.failure(), worker(runAttemptCount = 3).doWork())
    }

    @Test
    fun theProgressNotificationIsGoneWhenTheWorkerReturns() = runTest {
        notifications.notify(
            NotificationCenter.ID_UPDATE_PROGRESS,
            notifications.progressNotification(appName = null, percent = -1),
        )

        worker().doWork()

        assertNull(shadowOf(notificationManager).getNotification(NotificationCenter.ID_UPDATE_PROGRESS))
    }

    private fun updatesAlert() =
        shadowOf(notificationManager).getNotification(NotificationCenter.ID_UPDATES_AVAILABLE)

    private fun worker(
        deviceInfo: FakeDeviceInfo = FakeDeviceInfo(),
        runAttemptCount: Int = 0,
    ): UpdateCheckWorker {
        val sweep = sweep(deviceInfo)
        return TestListenableWorkerBuilder<UpdateCheckWorker>(application)
            .setRunAttemptCount(runAttemptCount)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ) = UpdateCheckWorker(appContext, workerParameters, sweep, notifications, NoopLogger)
            })
            .build()
    }

    /** The production sweep over fakes, as `RunUpdateSweepUseCaseTest` builds it. */
    private fun sweep(deviceInfo: FakeDeviceInfo): RunUpdateSweepUseCase {
        val settings = FakeSettingsRepository(AppSettings(installerMode = InstallerMode.Session))
        val telegram = FakeTelegramRepository()
        val selfUpdates = FakeSelfUpdateStateRepository()
        val selfInfo = FakeSelfAppInfo()
        val reconcile = ReconcileSelfUpdateUseCase(selfUpdates, apps, selfInfo, NoopLogger)
        return RunUpdateSweepUseCase(
            appsRepository = apps,
            settingsRepository = settings,
            checkUpdates = CheckUpdatesUseCase(
                resolveUpdate = ResolveUpdateUseCase(github, telegram, deviceInfo),
                settingsRepository = settings,
                // Every fixture app uses `com.example`: "the tracked app is on the device".
                packageInspector = FakePackageInspector(installedPackages = setOf("com.example")),
                logger = NoopLogger,
            ),
            installCoordinator = InstallCoordinator(
                installApp = InstallAppUseCase(
                    installer,
                    InstallScheduler(settings),
                    apps,
                    telegram,
                    selfUpdates,
                    reconcile,
                    selfInfo,
                    NoopLogger,
                ),
                scope = CoroutineScope(SupervisorJob()),
                logger = NoopLogger,
            ),
            reconcileSelfUpdate = reconcile,
            deviceInfo = deviceInfo,
            selfApp = selfInfo,
        )
    }
}
