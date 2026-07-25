package dev.re7gog.b_sideloader.ui.feature.apps

import app.cash.turbine.test
import dev.re7gog.b_sideloader.core.log.NoopLogger
import dev.re7gog.b_sideloader.domain.usecase.DeleteTrackedAppsUseCase
import dev.re7gog.b_sideloader.domain.usecase.ObserveTrackedAppsUseCase
import dev.re7gog.b_sideloader.domain.usecase.UninstallAppsUseCase
import dev.re7gog.b_sideloader.testing.FakeAppsRepository
import dev.re7gog.b_sideloader.testing.FakeInstallerGateway
import dev.re7gog.b_sideloader.testing.FakePackageInspector
import dev.re7gog.b_sideloader.testing.MainDispatcherRule
import dev.re7gog.b_sideloader.testing.githubApp
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

    private fun viewModel() = AppsListViewModel(
        observeTrackedApps = ObserveTrackedAppsUseCase(apps, packages),
        deleteTrackedApps = DeleteTrackedAppsUseCase(apps),
        uninstallApps = UninstallAppsUseCase(installer, packages),
        logger = NoopLogger,
    )

    @Test
    fun `emits the tracked apps with their installed state`() = runTest {
        viewModel().uiState.test {
            skipItems(1) // initial empty state
            val state = awaitItem()

            assertEquals(listOf("Alpha", "Beta"), state.apps.map { it.name })
            assertTrue(state.apps.first { it.name == "Alpha" }.isInstalled)
            assertFalse(state.apps.first { it.name == "Beta" }.isInstalled)
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
            skipItems(2)

            packages.markInstalled("com.beta")

            val updated = awaitItem()
            assertTrue(updated.apps.first { it.name == "Beta" }.isInstalled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selection mode turns on with the first selection`() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            skipItems(2)

            viewModel.toggleSelection(1L)

            val selected = awaitItem()
            assertTrue(selected.inSelectionMode)
            assertEquals(1, selected.selectedCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggling the same app twice clears the selection`() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            skipItems(2)
            viewModel.toggleSelection(1L)
            skipItems(1)

            viewModel.toggleSelection(1L)

            assertFalse(awaitItem().inSelectionMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `removing the selection deletes only those apps`() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            skipItems(2)
            viewModel.toggleSelection(1L)
            skipItems(1)

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
            skipItems(2)
            viewModel.selectAll()
            skipItems(1)

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
            skipItems(2)
            viewModel.toggleSelection(2L)
            skipItems(1)

            apps.deleteAll(listOf(apps.getApps().first { it.id == 2L }))

            val state = awaitItem()
            assertEquals(0, state.selectedCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun kotlinx.coroutines.test.TestScope.runCurrent() =
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
}
