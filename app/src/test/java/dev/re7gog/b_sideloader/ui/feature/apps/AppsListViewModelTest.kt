package dev.re7gog.b_sideloader.ui.feature.apps

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import dev.re7gog.b_sideloader.core.log.NoopLogger
import dev.re7gog.b_sideloader.domain.model.AppSettings
import dev.re7gog.b_sideloader.domain.model.AppVersion
import dev.re7gog.b_sideloader.domain.usecase.CheckUpdatesUseCase
import dev.re7gog.b_sideloader.domain.usecase.DeleteTrackedAppsUseCase
import dev.re7gog.b_sideloader.domain.usecase.InstallAppUseCase
import dev.re7gog.b_sideloader.domain.usecase.ObserveTrackedAppsUseCase
import dev.re7gog.b_sideloader.domain.usecase.ResolveUpdateUseCase
import dev.re7gog.b_sideloader.domain.usecase.UninstallAppsUseCase
import dev.re7gog.b_sideloader.testing.FakeAppsRepository
import dev.re7gog.b_sideloader.testing.FakeDeviceInfo
import dev.re7gog.b_sideloader.testing.FakeGithubRepository
import dev.re7gog.b_sideloader.testing.FakeInstallerGateway
import dev.re7gog.b_sideloader.testing.FakePackageInspector
import dev.re7gog.b_sideloader.testing.FakeSettingsRepository
import dev.re7gog.b_sideloader.testing.FakeTelegramRepository
import dev.re7gog.b_sideloader.testing.MainDispatcherRule
import dev.re7gog.b_sideloader.testing.asset
import dev.re7gog.b_sideloader.testing.githubApp
import dev.re7gog.b_sideloader.testing.release
import dev.re7gog.b_sideloader.testing.telegramApp
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppsListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val apps = FakeAppsRepository(
        listOf(
            githubApp(id = 1, name = "Alpha", packageName = "com.alpha"),
            telegramApp(id = 2, name = "Beta", packageName = "com.beta"),
        ),
    )
    private val packages = FakePackageInspector(installedPackages = setOf("com.alpha"))
    private val installer = FakeInstallerGateway()
    private val github = FakeGithubRepository()
    private val telegram = FakeTelegramRepository()
    private val settings = FakeSettingsRepository()

    private fun viewModel() = AppsListViewModel(
        observeTrackedApps = ObserveTrackedAppsUseCase(apps, packages),
        appsRepository = apps,
        checkUpdates = CheckUpdatesUseCase(
            resolveUpdate = ResolveUpdateUseCase(github, telegram, FakeDeviceInfo()),
            settingsRepository = settings,
            packageInspector = packages,
            logger = NoopLogger,
        ),
        installApp = InstallAppUseCase(installer, apps, telegram, NoopLogger),
        deleteTrackedApps = DeleteTrackedAppsUseCase(apps),
        uninstallApps = UninstallAppsUseCase(installer, packages),
        settingsRepository = settings,
        logger = NoopLogger,
    )

    @Test
    fun `emits the tracked apps with their installed state`() = runTest {
        viewModel().uiState.test {
            val state = awaitState { it.apps.size == 2 }

            assertEquals(setOf("Alpha", "Beta"), state.apps.mapTo(mutableSetOf()) { it.name })
            assertTrue(state.apps.first { it.name == "Alpha" }.isInstalled)
            assertFalse(state.apps.first { it.name == "Beta" }.isInstalled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Apps that are not on the device sink below the ones that are. */
    @Test
    fun `not installed apps are ordered last`() = runTest {
        viewModel().uiState.test {
            val state = awaitState { it.apps.size == 2 }

            assertEquals(listOf("Alpha", "Beta"), state.apps.map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * The list used to poll `PackageManager` from the composable and needed a hand-incremented
     * key to notice anything. Now an install anywhere on the device updates the row.
     */
    @Test
    fun `installing a package updates the row without a manual refresh`() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            awaitState { it.apps.size == 2 }

            packages.markInstalled("com.beta")

            val updated = awaitState { it.apps.first { app -> app.name == "Beta" }.isInstalled }
            assertTrue(updated.apps.first { it.name == "Beta" }.isInstalled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selection mode turns on with the first selection`() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            awaitState { it.apps.size == 2 }

            viewModel.toggleSelection(1L)

            val selected = awaitState { it.inSelectionMode }
            assertEquals(1, selected.selectedCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggling the same app twice clears the selection`() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            awaitState { it.apps.size == 2 }
            viewModel.toggleSelection(1L)
            awaitState { it.inSelectionMode }

            viewModel.toggleSelection(1L)

            assertFalse(awaitState { !it.inSelectionMode }.inSelectionMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `removing the selection deletes only those apps`() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            awaitState { it.apps.size == 2 }
            viewModel.toggleSelection(1L)
            awaitState { it.inSelectionMode }

            viewModel.removeSelectedFromList()
            runCurrent()

            assertEquals(listOf(2L), apps.getApps().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Uninstalling must skip apps that are tracked but not actually on the device. */
    @Test
    fun `uninstalling skips apps that are not installed`() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            awaitState { it.apps.size == 2 }
            viewModel.selectAll()
            awaitState { it.selectedCount == 2 }

            viewModel.uninstallSelected()
            runCurrent()

            assertEquals(listOf("com.alpha"), installer.uninstalled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** A selected app that disappears must not keep inflating the selection count. */
    @Test
    fun `selection of a deleted app is dropped`() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            awaitState { it.apps.size == 2 }
            viewModel.toggleSelection(2L)
            awaitState { it.inSelectionMode }

            apps.deleteAll(listOf(apps.getApps().first { it.id == 2L }))

            val state = awaitState { it.apps.size == 1 }
            assertEquals(0, state.selectedCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * The whole point of the check-on-open: only the installed app may cost a request. "Beta" is
     * tracked but absent from the device, so its source is never asked.
     */
    @Test
    fun `the check on open only queries installed apps`() = runTest {
        github.releases = listOf(release("v2.0", assets = arrayOf(asset("app.apk"))))
        apps.update(githubApp(id = 1, name = "Alpha", packageName = "com.alpha", version = AppVersion("v1.0")))

        val viewModel = viewModel()
        viewModel.uiState.test {
            val state = awaitState { it.apps.any { app -> app.canUpdate } }

            assertEquals(1, github.releaseCallCount)
            assertEquals(1, state.updatableCount)
            assertTrue(state.showUpdateAll)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** An app with an update outranks one without, regardless of name order. */
    @Test
    fun `apps with updates are ordered first`() = runTest {
        github.releases = listOf(release("v2.0", assets = arrayOf(asset("app.apk"))))
        apps.update(githubApp(id = 1, name = "Alpha", packageName = "com.alpha", version = AppVersion("v2.0")))
        apps.add(githubApp(id = 0, name = "Zulu", packageName = "com.zulu", version = AppVersion("v1.0")))
        packages.markInstalled("com.zulu")

        val viewModel = viewModel()
        viewModel.uiState.test {
            val state = awaitState { it.apps.firstOrNull()?.canUpdate == true }

            assertEquals("Zulu", state.apps.first().name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** The hint is a one-shot: dismissing it writes through to the settings store. */
    @Test
    fun `dismissing the long press hint persists it`() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            awaitState { it.showLongPressHint }

            viewModel.dismissLongPressHint()
            runCurrent()

            assertTrue(settings.current().longPressHintSeen)
            assertFalse(awaitState { !it.showLongPressHint }.showLongPressHint)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Already dismissed means never shown again, even on a fresh ViewModel. */
    @Test
    fun `the long press hint stays hidden once seen`() = runTest {
        val seen = FakeSettingsRepository(AppSettings(longPressHintSeen = true))
        val viewModel = AppsListViewModel(
            observeTrackedApps = ObserveTrackedAppsUseCase(apps, packages),
            appsRepository = apps,
            checkUpdates = CheckUpdatesUseCase(
                resolveUpdate = ResolveUpdateUseCase(github, telegram, FakeDeviceInfo()),
                settingsRepository = seen,
                packageInspector = packages,
                logger = NoopLogger,
            ),
            installApp = InstallAppUseCase(installer, apps, telegram, NoopLogger),
            deleteTrackedApps = DeleteTrackedAppsUseCase(apps),
            uninstallApps = UninstallAppsUseCase(installer, packages),
            settingsRepository = seen,
            logger = NoopLogger,
        )

        viewModel.uiState.test {
            val state = awaitState { it.apps.size == 2 }
            assertFalse(state.showLongPressHint)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Waits for the first state that satisfies [predicate].
     *
     * Deliberately not `skipItems(n)`: this state is a `combine` of four flows, so the number of
     * intermediate emissions is an implementation detail. Counting them made every test fail the
     * moment a new input was added — and, worse, made them assert against the wrong item rather
     * than fail outright.
     */
    private suspend fun ReceiveTurbine<AppsListUiState>.awaitState(
        predicate: (AppsListUiState) -> Boolean,
    ): AppsListUiState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    private fun kotlinx.coroutines.test.TestScope.runCurrent() =
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
}
