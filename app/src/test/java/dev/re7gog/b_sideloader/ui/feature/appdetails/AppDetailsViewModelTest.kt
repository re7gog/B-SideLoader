package dev.re7gog.b_sideloader.ui.feature.appdetails

import dev.re7gog.b_sideloader.core.log.NoopLogger
import dev.re7gog.b_sideloader.domain.installer.PackageInspector
import dev.re7gog.b_sideloader.domain.repository.AppsRepository
import dev.re7gog.b_sideloader.domain.usecase.DeleteTrackedAppsUseCase
import dev.re7gog.b_sideloader.domain.usecase.InstallAppUseCase
import dev.re7gog.b_sideloader.domain.usecase.ListUpdateCandidatesUseCase
import dev.re7gog.b_sideloader.domain.usecase.OpenInstalledAppUseCase
import dev.re7gog.b_sideloader.domain.usecase.SaveTrackedAppUseCase
import dev.re7gog.b_sideloader.domain.usecase.UninstallAppsUseCase
import dev.re7gog.b_sideloader.testing.FakeAppsRepository
import dev.re7gog.b_sideloader.testing.FakeDeviceInfo
import dev.re7gog.b_sideloader.testing.FakeGithubRepository
import dev.re7gog.b_sideloader.testing.FakeInstallerGateway
import dev.re7gog.b_sideloader.testing.FakePackageInspector
import dev.re7gog.b_sideloader.testing.FakeTelegramRepository
import dev.re7gog.b_sideloader.testing.MainDispatcherRule
import dev.re7gog.b_sideloader.testing.asset
import dev.re7gog.b_sideloader.testing.githubApp
import dev.re7gog.b_sideloader.testing.release
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun viewModel(
        args: AppDetailsArgs = AppDetailsArgs.Saved(1L),
        appsRepository: AppsRepository = apps,
        packageInspector: PackageInspector = packages,
    ) = AppDetailsViewModel(
        args = args,
        appsRepository = appsRepository,
        githubRepository = github,
        telegramRepository = telegram,
        listCandidates = ListUpdateCandidatesUseCase(github, telegram),
        installApp = InstallAppUseCase(installer, appsRepository, telegram, NoopLogger),
        saveTrackedApp = SaveTrackedAppUseCase(appsRepository),
        deleteTrackedApps = DeleteTrackedAppsUseCase(appsRepository),
        uninstallApps = UninstallAppsUseCase(installer, packageInspector),
        openInstalledApp = OpenInstalledAppUseCase(packageInspector),
        packageInspector = packageInspector,
        deviceInfo = FakeDeviceInfo(),
        logger = NoopLogger,
    )

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
}
