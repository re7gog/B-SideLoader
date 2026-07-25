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
