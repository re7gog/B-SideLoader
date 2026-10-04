package dev.re7gog.b_sideloader.domain.model

import dev.re7gog.b_sideloader.domain.error.AppError

/**
 * Progress of a single install, from first byte to the package manager's verdict.
 *
 * Emitted as a `Flow<InstallProgress>` so the caller can render a determinate bar for the phases
 * that have a known size and a spinner for the ones that do not, instead of the single opaque
 * `Float` the old code used for everything.
 */
sealed interface InstallProgress {

    /** 0f..1f when the phase is measurable, `null` when it is indeterminate. */
    val fraction: Float?

    /**
     * Waiting for its turn: for a download slot on its source, or — downloaded — for the
     * installer, which takes one app at a time. Indeterminate.
     */
    data object Queued : InstallProgress {
        override val fraction: Float? get() = null
    }

    /** Resolving the download / opening a session. Indeterminate. */
    data object Preparing : InstallProgress {
        override val fraction: Float? get() = null
    }

    /** Pulling bytes from the network (HTTP) or from Telegram. */
    data class Downloading(override val fraction: Float) : InstallProgress

    /** Streaming bytes into the `PackageInstaller` session. */
    data class Staging(override val fraction: Float) : InstallProgress

    /** Session committed; waiting for the system (and possibly the user) to decide. */
    data object Committing : InstallProgress {
        override val fraction: Float? get() = null
    }

    /** Terminal value. The flow completes right after emitting this. */
    data class Finished(val outcome: InstallOutcome) : InstallProgress {
        override val fraction: Float? get() = null
    }
}

/** Progress of fetching an APK to local storage, the first half of an install. */
sealed interface DownloadProgress {
    data class Downloading(val fraction: Float) : DownloadProgress

    /** Terminal. The flow completes right after emitting this. */
    data class Downloaded(val apk: DownloadedApk) : DownloadProgress

    /** Terminal. A failure is a value here too, never an exception. */
    data class Failed(val error: AppError) : DownloadProgress
}

/**
 * An APK fetched to local storage and waiting to be installed.
 *
 * Downloads and installs are separate steps so downloads can run in parallel while installs stay
 * strictly one at a time. Whoever downloaded it must hand it back to
 * [dev.re7gog.b_sideloader.domain.installer.InstallerGateway.discard] once done with it.
 */
data class DownloadedApk(
    val path: String,
    val sizeBytes: Long,
    /** Where it came from, which decides who owns the file and how it is cleaned up. */
    val source: DownloadRef,
)

/**
 * An installer session opened *before* the download, so the user can approve the install while
 * it downloads instead of being asked once it has finished — and, approved, the install then needs
 * no dialog at all. Only opened where that spares the user a dialog; see
 * [dev.re7gog.b_sideloader.domain.installer.InstallerGateway.openPreapprovalSession].
 */
data class PreapprovalSession(
    val sessionId: Int,
    val packageName: String,
)

/** What the user answered when asked to approve an install up front. */
enum class PreapprovalDecision {
    /** The install will go through without asking again. */
    Approved,

    /** The user said no. The install stops, quietly — they just told us they do not want it. */
    Declined,

    /** Could not be asked (disabled on the device, or failed): install the usual way instead. */
    Unavailable,
}

/** How an install ended. */
sealed interface InstallOutcome {
    data class Success(val packageName: String?) : InstallOutcome
    data class Failure(val error: AppError) : InstallOutcome
}

/** How an uninstall ended. */
sealed interface UninstallOutcome {
    data class Success(val packageName: String) : UninstallOutcome
    data class Failure(val error: AppError) : UninstallOutcome
}

/** The installation backend the user picked in settings. */
enum class InstallerMode {
    /** Standard `PackageInstaller` session; the system asks the user to confirm. */
    Session,

    /** Silent install through a Shizuku/Sui service running as shell or root. */
    Shizuku,

    /** Silent install through Dhizuku, which owns the device-owner slot. */
    Dhizuku,
    ;

    /** Whether this mode needs an external privileged service. */
    val isPrivileged: Boolean get() = this != Session

    /** Whether the privileged path should go through Dhizuku instead of Shizuku. */
    val usesDhizuku: Boolean get() = this == Dhizuku

    companion object {
        val Default = Session

        fun fromStoredName(name: String?): InstallerMode =
            entries.firstOrNull { it.name == name } ?: Default
    }
}

/** What the privileged backend reported when asked for permission. */
sealed interface PrivilegedAccess {
    /** Usable. [via] says which identity the service runs as, which the UI surfaces. */
    data class Granted(val via: PrivilegedIdentity) : PrivilegedAccess

    data class Unavailable(val error: AppError.Privileged) : PrivilegedAccess
}

enum class PrivilegedIdentity { Adb, Root, DeviceOwner }

/** An APK the user picked from storage, parsed but not yet installed. */
data class LocalApk(
    val path: String,
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val sizeBytes: Long,
    /** Version of the same package already on this device, or null when it is not installed. */
    val installedVersionName: String? = null,
    val installedVersionCode: Long? = null,
) {
    /** Android refuses to replace an app with an older one; the user must uninstall first. */
    val isDowngrade: Boolean
        get() = installedVersionCode != null && versionCode < installedVersionCode

    val isReinstall: Boolean get() = installedVersionCode != null
}
