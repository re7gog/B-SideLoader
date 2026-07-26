package dev.re7gog.b_sideloader.data.installer

import android.content.pm.PackageInstaller
import dev.re7gog.b_sideloader.domain.error.AppError
import dev.re7gog.b_sideloader.domain.error.InstallFailure
import dev.re7gog.b_sideloader.domain.model.InstallOutcome
import dev.re7gog.b_sideloader.domain.model.UninstallOutcome

/**
 * One way of getting an APK onto the device. Implemented once for the standard
 * `PackageInstaller` session and once for the privileged (Shizuku/Dhizuku) path.
 *
 * Both return an outcome rather than throwing for an install failure: "the user pressed Cancel"
 * is a normal result, not an exceptional one, and modelling it as a value keeps every caller from
 * having to distinguish it from a genuine error.
 */
interface ApkInstallerBackend {

    /** @param onProgress fraction of [payload] written into the session, 0f..1f. */
    suspend fun install(payload: ApkPayload, onProgress: suspend (Float) -> Unit): InstallOutcome

    suspend fun uninstall(packageName: String): UninstallOutcome
}

/** Maps a `PackageInstaller.STATUS_*` value to the domain's failure vocabulary. */
internal fun installFailureFor(status: Int, message: String?): AppError.Install {
    val reason = when (status) {
        PackageInstaller.STATUS_FAILURE_ABORTED -> InstallFailure.Aborted
        PackageInstaller.STATUS_FAILURE_STORAGE -> InstallFailure.Storage
        PackageInstaller.STATUS_FAILURE_INVALID -> InstallFailure.BadPayload
        PackageInstaller.STATUS_FAILURE_CONFLICT -> conflictFailureFor(message)
        else -> InstallFailure.Rejected
    }
    return AppError.Install(reason, message)
}

/**
 * Splits `STATUS_FAILURE_CONFLICT` into the cases the user can actually act on.
 *
 * The public status is a single bucket, but the framework still puts the legacy
 * `INSTALL_FAILED_*` name in `EXTRA_STATUS_MESSAGE`, which is the only signal available. It is a
 * diagnostic string rather than an API, so this matches loosely and falls back to the generic
 * [InstallFailure.Conflict] instead of guessing — previously *every* conflict was reported as a
 * downgrade, which sent users to uninstall a perfectly current app whenever a signature differed.
 */
private fun conflictFailureFor(message: String?): InstallFailure {
    val text = message.orEmpty().uppercase()
    return when {
        text.contains(DOWNGRADE_MARKER) -> InstallFailure.Downgrade
        SIGNATURE_MARKERS.any { text.contains(it) } -> InstallFailure.SignatureMismatch
        else -> InstallFailure.Conflict
    }
}

/** Covers both `INSTALL_FAILED_VERSION_DOWNGRADE` and `INSTALL_FAILED_PERMISSION_MODEL_DOWNGRADE`. */
private const val DOWNGRADE_MARKER = "DOWNGRADE"

private val SIGNATURE_MARKERS = listOf(
    "INSTALL_FAILED_UPDATE_INCOMPATIBLE",
    "INSTALL_FAILED_SHARED_USER_INCOMPATIBLE",
    "SIGNATURES DO NOT MATCH",
    "INCONSISTENT CERTIFICATES",
)

/** Turns a received `PackageInstaller` verdict into an [InstallOutcome]. */
internal fun PackageInstallerEvent.toInstallOutcome(): InstallOutcome =
    if (status == PackageInstaller.STATUS_SUCCESS) {
        InstallOutcome.Success(packageName)
    } else {
        InstallOutcome.Failure(installFailureFor(status, message))
    }

/** Turns a received verdict into an [UninstallOutcome]. */
internal fun PackageInstallerEvent.toUninstallOutcome(requested: String): UninstallOutcome =
    if (status == PackageInstaller.STATUS_SUCCESS) {
        UninstallOutcome.Success(packageName ?: requested)
    } else {
        UninstallOutcome.Failure(installFailureFor(status, message))
    }
