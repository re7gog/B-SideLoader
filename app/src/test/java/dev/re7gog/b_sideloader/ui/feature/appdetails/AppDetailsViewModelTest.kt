package dev.re7gog.b_sideloader.ui.feature.appdetails

import dev.re7gog.b_sideloader.core.log.NoopLogger
import dev.re7gog.b_sideloader.domain.installer.PackageInspector
import dev.re7gog.b_sideloader.domain.model.AppSource
import dev.re7gog.b_sideloader.domain.model.AppVersion
import dev.re7gog.b_sideloader.domain.model.InstallOutcome
import dev.re7gog.b_sideloader.domain.model.InstallProgress
import dev.re7gog.b_sideloader.domain.model.PendingSelfUpdate
import dev.re7gog.b_sideloader.domain.repository.AppsRepository
import dev.re7gog.b_sideloader.domain.usecase.DeleteTrackedAppsUseCase
import dev.re7gog.b_sideloader.domain.usecase.InstallAppUseCase
import dev.re7gog.b_sideloader.domain.usecase.InstallCoordinator
import dev.re7gog.b_sideloader.domain.usecase.InstallKey
import dev.re7gog.b_sideloader.domain.usecase.ListUpdateCandidatesUseCase
import dev.re7gog.b_sideloader.domain.usecase.OpenInstalledAppUseCase
import dev.re7gog.b_sideloader.domain.usecase.SaveTrackedAppUseCase
import dev.re7gog.b_sideloader.domain.usecase.UninstallAppsUseCase
import dev.re7gog.b_sideloader.testing.FakeAppsRepository
import dev.re7gog.b_sideloader.testing.FakeDeviceInfo
import dev.re7gog.b_sideloader.testing.FakeGithubRepository
import dev.re7gog.b_sideloader.testing.FakeInstallerGateway
import dev.re7gog.b_sideloader.testing.FakePackageInspector
import dev.re7gog.b_sideloader.testing.FakeSelfAppInfo
import dev.re7gog.b_sideloader.testing.FakeSelfUpdateStateRepository
import dev.re7gog.b_sideloader.testing.FakeTelegramRepository
import dev.re7gog.b_sideloader.testing.MainDispatcherRule
import dev.re7gog.b_sideloader.testing.asset
import dev.re7gog.b_sideloader.testing.githubApp
import dev.re7gog.b_sideloader.testing.reconcileSelfUpdate
import dev.re7gog.b_sideloader.testing.release
import dev.re7gog.b_sideloader.testing.selfApp
import dev.re7gog.b_sideloader.testing.telegramApp
import dev.re7gog.b_sideloader.testing.updateCandidate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The details page, in particular its editable name. */
class AppDetailsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val apps = FakeAppsRepository(listOf(githubApp(id = 1, name = "Example")))
    private val github = FakeGithubRepository(
        releases = listOf(release("v1.0", assets = arrayOf(asset("app.apk")))),
    )
    private val telegram = FakeTelegramRepository()
    private val installer = FakeInstallerGateway()
    private val packages = FakePackageInspector(installedPackages = setOf("com.example"))

    /**
     * The app-wide installs, as the apps list would see them. Built by the first [viewModel]
     * call, from that call's repositories.
     */
    private lateinit var installs: InstallCoordinator

    private fun viewModel(
        args: AppDetailsArgs = AppDetailsArgs.Saved(1L),
        appsRepository: AppsRepository = apps,
        packageInspector: PackageInspector = packages,
        selfUpdates: FakeSelfUpdateStateRepository = FakeSelfUpdateStateRepository(),
        selfInfo: FakeSelfAppInfo = FakeSelfAppInfo(),
    ): AppDetailsViewModel {
        val reconcile = reconcileSelfUpdate(appsRepository, selfUpdates, selfInfo)
        installs = InstallCoordinator(
            installApp = InstallAppUseCase(
                installer,
                appsRepository,
                telegram,
                selfUpdates,
                reconcile,
                selfInfo,
                NoopLogger,
            ),
            scope = CoroutineScope(SupervisorJob() + mainDispatcherRule.dispatcher),
            logger = NoopLogger,
        )
        return AppDetailsViewModel(
            args = args,
            appsRepository = appsRepository,
            githubRepository = github,
            telegramRepository = telegram,
            listCandidates = ListUpdateCandidatesUseCase(github, telegram),
            installCoordinator = installs,
            reconcileSelfUpdate = reconcile,
            saveTrackedApp = SaveTrackedAppUseCase(appsRepository),
            deleteTrackedApps = DeleteTrackedAppsUseCase(appsRepository),
            uninstallApps = UninstallAppsUseCase(installer, packageInspector),
            openInstalledApp = OpenInstalledAppUseCase(packageInspector),
            packageInspector = packageInspector,
            deviceInfo = FakeDeviceInfo(),
            logger = NoopLogger,
        )
    }

    @Test
    fun `renaming a saved app marks it as having unsaved changes`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onNameChange("Renamed")

        assertEquals("Renamed", viewModel.uiState.value.app?.name)
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)
        assertEquals(PrimaryAction.SaveChanges, viewModel.uiState.value.primaryAction)
    }

    @Test
    fun `saving persists the new name`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onNameChange("Renamed")

        viewModel.onPrimaryAction()
        advanceUntilIdle()

        assertEquals("Renamed", apps.getApps().single().name)
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)
    }

    /**
     * Nothing about *which* APK wins depends on the name, so a rename must not spend a request.
     * The filter fields share the same edit path, which is what makes this worth pinning down.
     */
    @Test
    fun `renaming does not re-query the source`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        val callsAfterLoad = github.releaseCallCount

        viewModel.onNameChange("Renamed")
        advanceUntilIdle()

        assertEquals(callsAfterLoad, github.releaseCallCount)
    }

    /** Changing a filter, by contrast, is exactly the thing that has to re-resolve. */
    @Test
    fun `changing a filter does re-query the source`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        val callsAfterLoad = github.releaseCallCount

        viewModel.onAssetIncludeChange("arm64")
        advanceUntilIdle()

        assertTrue(github.releaseCallCount > callsAfterLoad)
    }

    /** A nameless row in the apps list is unusable, so an empty name blocks the primary action. */
    @Test
    fun `a blank name disables the primary action`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onNameChange("   ")

        val state = viewModel.uiState.value
        assertFalse(state.isNameValid)
        assertFalse(state.isPrimaryEnabled)
    }

    /**
     * An app added from a forum topic is named after the group. The topic name alone ("Releases",
     * "APK") says nothing about which app it is, and the same group can host several topics.
     */
    @Test
    fun `a new telegram app takes the name it was opened with`() = runTest {
        val viewModel = viewModel(
            args = AppDetailsArgs.NewTelegram(chatId = -100L, topicId = 7, title = "Cool Apps"),
        )
        advanceUntilIdle()

        assertEquals("Cool Apps", viewModel.uiState.value.app?.name)
        assertEquals(PrimaryAction.SaveAndInstall, viewModel.uiState.value.primaryAction)
    }

    // ---- add again ----

    private val searchedGithub = AppDetailsArgs.NewGithub(owner = "octocat", repo = "example", name = "Example")

    /** A source nobody has added yet is the ordinary first-time case. */
    @Test
    fun `a source that is not tracked yet offers save and install`() = runTest {
        val viewModel = viewModel(
            args = AppDetailsArgs.NewGithub(owner = "octocat", repo = "other", name = "Other"),
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.alreadyAdded)
        assertEquals(PrimaryAction.SaveAndInstall, viewModel.uiState.value.primaryAction)
    }

    /**
     * Search never opens the saved row. Whatever is stored for the source — name, filters, the
     * version that is installed — plays no part: the page is a new app, and only the button's
     * label says the source is already tracked.
     */
    @Test
    fun `a tracked source opened from search is a new app that only differs in its label`() = runTest {
        val tracked = FakeAppsRepository(
            listOf(githubApp(id = 1, name = "Saved name", assetInclude = "arm64", version = AppVersion("v0.1"))),
        )
        val viewModel = viewModel(args = searchedGithub, appsRepository = tracked)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.alreadyAdded)
        assertEquals(PrimaryAction.AddAgain, state.primaryAction)
        assertTrue(state.isPrimaryEnabled)
        assertFalse(state.isSaved)
        assertFalse(state.isInstalled)
        assertFalse(state.hasUnsavedChanges)
        assertEquals("Example", state.app?.name)
        assertEquals("", state.app?.assetFilter?.include)
        assertEquals("", state.app?.packageName)
        assertFalse(state.app?.version?.isKnown == true)
    }

    /** The reported bug: editing a filter on the page must not turn Add again into Save changes. */
    @Test
    fun `editing filters keeps add again`() = runTest {
        val viewModel = viewModel(args = searchedGithub)
        advanceUntilIdle()

        viewModel.onAssetIncludeChange("app")
        viewModel.onReleaseExcludeChange("beta")
        viewModel.onNameChange("Second app")
        advanceUntilIdle()

        assertEquals(PrimaryAction.AddAgain, viewModel.uiState.value.primaryAction)
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)
    }

    /** Only search starts a new app; the saved app's own page from the apps list is unchanged. */
    @Test
    fun `the apps list page still offers open`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.alreadyAdded)
        assertEquals(PrimaryAction.Open, viewModel.uiState.value.primaryAction)
    }

    @Test
    fun `adding again installs a second row and leaves the first one alone`() = runTest {
        installer.outcome = InstallOutcome.Success("com.example.second")
        val original = apps.getApps().single()
        val viewModel = viewModel(args = searchedGithub)
        advanceUntilIdle()

        viewModel.onAssetIncludeChange("app")
        advanceUntilIdle()
        viewModel.onPrimaryAction()
        advanceUntilIdle()

        val rows = apps.getApps()
        assertEquals(listOf("com.example", "com.example.second"), rows.map { it.packageName })
        assertEquals(original, rows[0])
        assertEquals("app", rows[1].assetFilter.include)
        // Saved and installed through this page, so from here it is an ordinary saved app.
        assertEquals(rows[1], viewModel.uiState.value.app)
        assertEquals(PrimaryAction.Open, viewModel.uiState.value.primaryAction)
    }

    @Test
    fun `a tracked telegram channel opened from search is a new app too`() = runTest {
        val tracked = FakeAppsRepository(
            listOf(telegramApp(id = 1, chatId = -100L, topicId = 0, messageInclude = "other app")),
        )
        val viewModel = viewModel(
            args = AppDetailsArgs.NewTelegram(chatId = -100L, topicId = 0, title = "Cool Apps"),
            appsRepository = tracked,
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(PrimaryAction.AddAgain, state.primaryAction)
        assertFalse(state.isSaved)
        assertEquals("Cool Apps", state.app?.name)
        assertEquals(AppSource.Telegram(chatId = -100L, topicId = 0), state.app?.source)
        assertEquals(1, tracked.getApps().size)
    }

    /**
     * An update started from the apps list used to leave this page looking idle. It now shows the
     * same install, and settles on the new version when it lands.
     */
    @Test
    fun `an install started from the list shows on the page`() = runTest {
        val gate = CompletableDeferred<Unit>()
        installer.beforeVerdict = { gate.await() }
        val viewModel = viewModel()
        advanceUntilIdle()

        // What the apps list does for "Update".
        installs.install(apps.getApp(1L)!!, updateCandidate("v2.0"))
        runCurrent()

        assertEquals(InstallProgress.Downloading(0.5f), viewModel.uiState.value.install)

        gate.complete(Unit)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.install)
        assertEquals("v2.0", state.app?.version?.raw)
        assertFalse(state.hasUnsavedChanges)
    }

    /** Edits made on the page survive an install the list ran from the stored row. */
    @Test
    fun `an install from the list keeps unsaved edits on the page`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onNameChange("Renamed")

        installs.install(apps.getApp(1L)!!, updateCandidate("v2.0"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Renamed", state.app?.name)
        assertEquals("v2.0", state.app?.version?.raw)
        assertTrue(state.hasUnsavedChanges)
    }

    /** And what this page starts is the shared install the list reads. */
    @Test
    fun `an install started on the page is visible app-wide`() = runTest {
        installer.beforeVerdict = { CompletableDeferred<Unit>().await() }
        // Not on the device, so the primary action installs rather than opens.
        val viewModel = viewModel(packageInspector = FakePackageInspector())
        advanceUntilIdle()

        viewModel.onPrimaryAction()
        runCurrent()

        assertEquals(InstallProgress.Downloading(0.5f), installs.installs.value[InstallKey.App(1L)])
        assertEquals(InstallProgress.Downloading(0.5f), viewModel.uiState.value.install)
    }

    /**
     * The page shows the stored version and compares candidates against it. For B-SideLoader's
     * own row that is only right after the startup reconciliation, so the page waits for it.
     */
    @Test
    fun `B-SideLoader's own page shows the version that just landed`() = runTest {
        val apps = FakeAppsRepository(listOf(selfApp(id = 1L, version = AppVersion("v1.0"))))
        val selfUpdates = FakeSelfUpdateStateRepository(
            rememberedVersionCode = 1L,
            pending = PendingSelfUpdate(appId = 1L, releaseName = AppVersion("v1.0.1")),
        )

        val viewModel = viewModel(
            appsRepository = apps,
            selfUpdates = selfUpdates,
            selfInfo = FakeSelfAppInfo(versionCode = 2L),
        )
        advanceUntilIdle()

        assertEquals("v1.0.1", viewModel.uiState.value.app?.version?.raw)
    }
}
