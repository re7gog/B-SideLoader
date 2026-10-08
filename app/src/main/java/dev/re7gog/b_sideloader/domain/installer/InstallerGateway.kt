package dev.re7gog.b_sideloader.domain.installer

import dev.re7gog.b_sideloader.domain.model.DownloadProgress
import dev.re7gog.b_sideloader.domain.model.DownloadRef
import dev.re7gog.b_sideloader.domain.model.DownloadedApk
import dev.re7gog.b_sideloader.domain.model.InstallProgress
import dev.re7gog.b_sideloader.domain.model.InstallerMode
import dev.re7gog.b_sideloader.domain.model.LocalApk
import dev.re7gog.b_sideloader.domain.model.PreapprovalDecision
import dev.re7gog.b_sideloader.domain.model.PreapprovalSession
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
     * Fetches [source] to local storage, finishing with [DownloadProgress.Downloaded] or
     * [DownloadProgress.Failed]. Touches no installer session, so any number of these may run at
     * once; how many actually do is [InstallScheduler]'s call. A download that fails or is
     * cancelled leaves nothing behind for the caller to clean up.
     */
    fun download(source: DownloadRef): Flow<DownloadProgress>

    /**
     * Installs an APK [download] fetched, emitting every phase and finishing with
     * [InstallProgress.Finished]. Never throws for an install failure — a failure is a
     * [dev.re7gog.b_sideloader.domain.model.InstallOutcome.Failure] value — so a caller cannot
     * accidentally treat "user declined" as a crash. Does not discard [apk].
     *
     * With [preapproved], installs into that session — which the user approved, so no dialog —
     * and takes it over: the caller must not abandon it afterwards.
     *
     * @param interactive whether the user may be asked to confirm the install. When not — the
     *   background sweep — an install the system will not take silently is dropped and ends as
     *   [dev.re7gog.b_sideloader.domain.model.InstallOutcome.NeedsConfirmation]. When so, the
     *   question still waits only so long for an answer, and ends the same way if none comes.
     */
    fun installDownloaded(
        apk: DownloadedApk,
        interactive: Boolean,
        preapproved: PreapprovalSession? = null,
    ): Flow<InstallProgress>

    /**
     * Whether updating the installed [packageName] now would ask the user to confirm it — known
     * before anything is downloaded, so the background sweep can leave such an update to the user
     * instead of fetching it for nothing. False with a privileged installer.
     *
     * A prediction: "no" can still turn out to need a confirmation once the APK is known (the
     * system also checks its target SDK), which [installDownloaded] then reports.
     */
    suspend fun requiresConfirmation(packageName: String): Boolean

    /**
     * Opens a session for installing [packageName], ahead of its download, so the user can be
     * asked for approval up front with [requestPreapproval].
     *
     * Null wherever asking would not spare a dialog later: before Android 14, with a privileged
     * installer (silent already), for an app that is not installed (its label, which the request
     * must carry, is unknown until the APK is), and for an app this app is already the installer of
     * record for (updated silently already — asking would *add* a dialog).
     */
    suspend fun openPreapprovalSession(packageName: String): PreapprovalSession?

    /** Asks the user to approve installing into [session], and suspends until they answer. */
    suspend fun requestPreapproval(session: PreapprovalSession): PreapprovalDecision

    /** Drops a session that will not be installed into. Safe if the system already dropped it. */
    suspend fun abandon(session: PreapprovalSession)

    /**
     * Drops this gateway's copy of [apk]. Safe to call more than once. A Telegram file is TDLib's,
     * not ours, so it is left to [dev.re7gog.b_sideloader.domain.repository.TelegramRepository].
     */
    suspend fun discard(apk: DownloadedApk)

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
