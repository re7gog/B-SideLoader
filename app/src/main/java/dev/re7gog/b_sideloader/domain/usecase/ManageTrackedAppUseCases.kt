package dev.re7gog.b_sideloader.domain.usecase

import dev.re7gog.b_sideloader.domain.installer.InstallerGateway
import dev.re7gog.b_sideloader.domain.installer.PackageInspector
import dev.re7gog.b_sideloader.domain.model.TrackedApp
import dev.re7gog.b_sideloader.domain.model.UninstallOutcome
import dev.re7gog.b_sideloader.domain.repository.AppsRepository
import javax.inject.Inject

/**
 * Saves an edited app. Inserts when it has never been saved, updates otherwise, and always returns
 * the app as it now exists in the database (with its row id).
 */
class SaveTrackedAppUseCase @Inject constructor(
    private val appsRepository: AppsRepository,
) {
    suspend operator fun invoke(app: TrackedApp): TrackedApp =
        if (app.isSaved) {
            appsRepository.update(app)
            app
        } else {
            app.copy(id = appsRepository.add(app))
        }
}

/** Forgets apps. Does not touch what is installed on the device. */
class DeleteTrackedAppsUseCase @Inject constructor(
    private val appsRepository: AppsRepository,
) {
    suspend operator fun invoke(apps: Collection<TrackedApp>) {
        if (apps.isEmpty()) return
        appsRepository.deleteAll(apps)
    }

    suspend operator fun invoke(app: TrackedApp) = invoke(listOf(app))
}

/**
 * Removes apps from the device, skipping the ones that are not installed.
 *
 * Returns one outcome per app that was actually attempted, so a caller can report "3 removed,
 * 1 refused" instead of a single boolean.
 */
class UninstallAppsUseCase @Inject constructor(
    private val installerGateway: InstallerGateway,
    private val packageInspector: PackageInspector,
) {
    suspend operator fun invoke(packageNames: Collection<String>): List<UninstallOutcome> =
        packageNames
            .filter { it.isNotBlank() && packageInspector.isInstalled(it) }
            .map { installerGateway.uninstall(it) }

    suspend operator fun invoke(packageName: String): UninstallOutcome? =
        invoke(listOf(packageName)).firstOrNull()
}

/** Launches an installed app. Returns false when it has no launchable activity. */
class OpenInstalledAppUseCase @Inject constructor(
    private val packageInspector: PackageInspector,
) {
    operator fun invoke(packageName: String): Boolean =
        packageName.isNotBlank() && packageInspector.launch(packageName)
}
