package dev.re7gog.b_sideloader.domain.usecase

import dev.re7gog.b_sideloader.domain.installer.PackageInspector
import dev.re7gog.b_sideloader.domain.model.TrackedApp
import dev.re7gog.b_sideloader.domain.repository.AppsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

/** A tracked app together with whether it is currently on the device. */
data class TrackedAppStatus(
    val app: TrackedApp,
    val isInstalled: Boolean,
)

/**
 * The apps list.
 *
 * Combines the database with live package-change broadcasts, so installing or removing an app —
 * from inside B-SideLoader or from anywhere else on the device — updates the list immediately.
 * The old code polled `PackageManager` from the composable on every recomposition and needed a
 * hand-incremented "refresh key" to notice changes at all.
 */
class ObserveTrackedAppsUseCase @Inject constructor(
    private val appsRepository: AppsRepository,
    private val packageInspector: PackageInspector,
) {
    operator fun invoke(): Flow<List<TrackedAppStatus>> = combine(
        appsRepository.observeApps(),
        // onStart so the first combine does not wait for a package change that may never come.
        packageInspector.packageChanges.map { }.onStart { emit(Unit) },
    ) { apps, _ ->
        apps.map { TrackedAppStatus(it, packageInspector.isInstalled(it.packageName)) }
    }
}
