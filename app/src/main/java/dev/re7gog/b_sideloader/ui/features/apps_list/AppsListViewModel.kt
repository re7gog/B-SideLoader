package dev.re7gog.b_sideloader.ui.features.apps_list

import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.re7gog.b_sideloader.data.installer.InstallEventManager
import dev.re7gog.b_sideloader.data.installer.InstallManager
import dev.re7gog.b_sideloader.domain.model.AppWithDetails
import dev.re7gog.b_sideloader.domain.repository.AppsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppsListViewModel @Inject constructor(
    private val appsRepository: AppsRepository,
    private val installManager: InstallManager
) : ViewModel() {
    val appsState: StateFlow<List<AppWithDetails>> = appsRepository.getAllAppsStream()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    // Ids of the apps currently ticked in selection mode. Empty => not in selection mode.
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    // Bumped whenever an app is installed/uninstalled so the list re-evaluates each item's
    // installed state and icon (both are read synchronously from PackageManager).
    private val _installedRefreshKey = MutableStateFlow(0)
    val installedRefreshKey: StateFlow<Int> = _installedRefreshKey.asStateFlow()

    init {
        viewModelScope.launch {
            merge(
                InstallEventManager.installEvents,
                InstallEventManager.uninstallEvents
            ).collect { _installedRefreshKey.update { it + 1 } }
        }
    }

    fun toggleSelection(id: Long) {
        _selectedIds.update { if (id in it) it - id else it + id }
    }

    fun selectAll() {
        _selectedIds.value = appsState.value.map { it.app.id }.toSet()
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    /** Removes the selected apps from the database (does not touch the device). */
    fun deleteSelectedFromDb() {
        val toDelete = appsState.value.filter { it.app.id in _selectedIds.value }
        clearSelection()
        viewModelScope.launch {
            toDelete.forEach { appsRepository.deleteApp(it.app) }
        }
    }

    /** Uninstalls the selected apps that are actually installed on the device. */
    fun uninstallSelected() {
        val toUninstall = appsState.value
            .filter { it.app.id in _selectedIds.value }
            .filter { installManager.isPackageInstalled(it.app.packageName) }
        clearSelection()
        viewModelScope.launch(Dispatchers.IO) {
            toUninstall.forEach { installManager.uninstallPackage(it.app.packageName) }
        }
    }

    fun isPackageInstalled(packageName: String): Boolean {
        return installManager.isPackageInstalled(packageName)
    }

    fun getAppIcon(packageName: String): Drawable? {
        return installManager.getAppIcon(packageName)
    }
}
