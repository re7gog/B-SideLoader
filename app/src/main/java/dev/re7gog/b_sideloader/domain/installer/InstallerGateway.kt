package dev.re7gog.b_sideloader.domain.installer

import dev.re7gog.b_sideloader.domain.model.DownloadRef
import dev.re7gog.b_sideloader.domain.model.InstallProgress
import dev.re7gog.b_sideloader.domain.model.InstallerMode
import dev.re7gog.b_sideloader.domain.model.LocalApk
import dev.re7gog.b_sideloader.domain.model.PrivilegedAccess
import dev.re7gog.b_sideloader.domain.model.UninstallOutcome
import kotlinx.coroutines.flow.Flow

/**
 * Installing and removing packages, without exposing `PackageInstaller`, Shizuku or Dhizuku.
 *
 * The flows returned here are cold and fully cancellable: abandoning the collector abandons the
 * download and the install session, which is what makes "leave the details screen mid-download"
 * safe.
 */
interface InstallerGateway {

    /**
     * Fetches [source] and installs it, emitting every phase and finishing with
     * [InstallProgress.Finished]. Never throws for an install failure — a failure is a
     * [dev.re7gog.b_sideloader.domain.model.InstallOutcome.Failure] value — so a caller cannot
     * accidentally treat "user declined" as a crash.
     */
    fun install(source: DownloadRef): Flow<InstallProgress>

    /** Installs an APK already on disk (manual install). */
    fun installLocal(apk: LocalApk): Flow<InstallProgress>

    /** Removes [packageName] from the device. */
    suspend fun uninstall(packageName: String): UninstallOutcome

    /** Whether the privileged backend behind [mode] is present and permitted. */
    suspend fun checkPrivilegedAccess(mode: InstallerMode): PrivilegedAccess
}

/** Read-only questions about what is on the device. */
interface PackageInspector {
    fun isInstalled(packageName: String): Boolean

    /** Installed version marker, or `null` when the package is absent. */
    fun installedVersion(packageName: String): InstalledPackage?

    /** Launches the app. Returns false when it has no launchable activity. */
    fun launch(packageName: String): Boolean

    /** Emits whenever any package is installed or removed, so lists can re-read their state. */
    val packageChanges: Flow<PackageChange>
}

data class InstalledPackage(
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
)

sealed interface PackageChange {
    data class Installed(val packageName: String?) : PackageChange
    data class Removed(val packageName: String?) : PackageChange
}

/** Copies a user-picked APK somewhere the installer can stream it from, and reads its manifest. */
interface ApkStagingArea {
    /** [uri] is an opaque content URI string; the data layer knows how to open it. */
    suspend fun stage(uri: String): LocalApk

    /** Drops any staged copy. Safe to call when nothing is staged. */
    suspend fun clear()
}
