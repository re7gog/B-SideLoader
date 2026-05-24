package dev.re7gog.b_sideloader.ui.features.apps_list

import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.re7gog.b_sideloader.domain.logic.IInstallManager
import dev.re7gog.b_sideloader.domain.model.AppWithDetails
import dev.re7gog.b_sideloader.domain.repository.AppsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AppsListViewModel @Inject constructor(
    appsRepository: AppsRepository,
    private val installManager: IInstallManager
) : ViewModel() {
    val appsState: StateFlow<List<AppWithDetails>> = appsRepository.getAllAppsStream()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun isPackageInstalled(packageName: String): Boolean {
        return installManager.isPackageInstalled(packageName)
    }

    fun getAppIcon(packageName: String): Drawable? {
        return installManager.getAppIcon(packageName)
    }

    /*
    fun deleteApp(appEntity: AppEntity) {
        viewModelScope.launch {
            appsRepository.deleteApp(appEntity)
        }
    }
    */
}