package dev.re7gog.b_sideloader.ui.feature.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.re7gog.b_sideloader.core.log.Logger
import dev.re7gog.b_sideloader.domain.model.TrackedApp
import dev.re7gog.b_sideloader.domain.usecase.DeleteTrackedAppsUseCase
import dev.re7gog.b_sideloader.domain.usecase.ObserveTrackedAppsUseCase
import dev.re7gog.b_sideloader.domain.usecase.TrackedAppStatus
import dev.re7gog.b_sideloader.domain.usecase.UninstallAppsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The apps list.
 *
 * Holds only selection state; everything else is derived from the database and live package
 * changes. Notably it no longer calls `PackageManager` from the composable, nor keeps a
 * hand-incremented "refresh key" to notice installs — [ObserveTrackedAppsUseCase] pushes both.
 */
@HiltViewModel
class AppsListViewModel @Inject constructor(
    observeTrackedApps: ObserveTrackedAppsUseCase,
    private val deleteTrackedApps: DeleteTrackedAppsUseCase,
    private val uninstallApps: UninstallAppsUseCase,
    private val logger: Logger,
) : ViewModel() {

    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())

    /** The apps as last observed, for acting on a selection without re-querying. */
    private var lastKnownApps: List<TrackedAppStatus> = emptyList()

    val uiState: StateFlow<AppsListUiState> = combine(
        observeTrackedApps().catch { throwable ->
            logger.e(TAG, throwable) { "Apps list stream failed" }
            emit(emptyList())
        },
        selectedIds,
    ) { apps, selected ->
        lastKnownApps = apps
        // Drop ids of apps that disappeared, otherwise the selection count outlives the rows.
        val liveSelection = selected intersect apps.mapTo(mutableSetOf()) { it.app.id }
        AppsListUiState(
            apps = apps.toListItems(liveSelection),
            isLoading = false,
            selectedCount = liveSelection.size,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = AppsListUiState(),
    )

    fun onAppLongPress(id: Long) = toggleSelection(id)

    fun toggleSelection(id: Long) {
        selectedIds.update { if (id in it) it - id else it + id }
    }

    fun selectAll() {
        selectedIds.value = lastKnownApps.mapTo(mutableSetOf()) { it.app.id }
    }

    fun clearSelection() {
        selectedIds.value = emptySet()
    }

    /** Forgets the selected apps. Does not touch the device. */
    fun removeSelectedFromList() {
        val targets = selectedApps()
        clearSelection()
        viewModelScope.launch { deleteTrackedApps(targets) }
    }

    /** Uninstalls the selected apps that are actually installed. */
    fun uninstallSelected() {
        val targets = selectedApps().map { it.packageName }
        clearSelection()
        viewModelScope.launch { uninstallApps(targets) }
    }

    private fun selectedApps(): List<TrackedApp> {
        val selected = selectedIds.value
        return lastKnownApps.filter { it.app.id in selected }.map { it.app }
    }

    private companion object {
        const val TAG = "AppsList"
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
