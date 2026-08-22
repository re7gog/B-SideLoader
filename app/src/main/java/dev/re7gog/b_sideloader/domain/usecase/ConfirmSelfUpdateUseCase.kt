package dev.re7gog.b_sideloader.domain.usecase

import dev.re7gog.b_sideloader.core.log.Logger
import dev.re7gog.b_sideloader.domain.device.SelfAppInfo
import dev.re7gog.b_sideloader.domain.model.PendingSelfUpdate
import dev.re7gog.b_sideloader.domain.repository.AppsRepository
import dev.re7gog.b_sideloader.domain.repository.PendingSelfUpdateRepository
import javax.inject.Inject

/**
 * Records a self-update that finished after the process it was started from had already been
 * killed.
 *
 * Every other install writes its version from [InstallAppUseCase], right after `PackageInstaller`
 * reports success. B-SideLoader updating itself never gets there: replacing the package kills the
 * app, so the flow, the coroutine and the process are gone before the verdict arrives. Without
 * this the row keeps the old version for ever and the app offers itself the same update on every
 * check.
 *
 * So the install writes ahead ([PendingSelfUpdate]) and this runs in the *new* version's process —
 * from `MY_PACKAGE_REPLACED`, which fires immediately after the replace, and again from
 * `Application.onCreate` for the cases where that broadcast never arrives (a ROM that drops it, or
 * an install that only completes after a reboot).
 *
 * A record that did not land is dropped rather than kept: the user declined the system dialog, or
 * the download failed while the process was dead. Keeping it would mean an unrelated later update
 * — installed from anywhere — could be mistaken for this one.
 */
class ConfirmSelfUpdateUseCase @Inject constructor(
    private val pendingSelfUpdates: PendingSelfUpdateRepository,
    private val appsRepository: AppsRepository,
    private val selfApp: SelfAppInfo,
    private val logger: Logger,
) {
    /** Throws [dev.re7gog.b_sideloader.domain.error.AppError] only if the database write fails. */
    suspend operator fun invoke() {
        val pending = pendingSelfUpdates.get() ?: return
        if (!landed(pending)) {
            logger.d(TAG) { "Self-update to ${pending.version} did not land; dropping the record" }
            pendingSelfUpdates.clear()
            return
        }

        val app = appsRepository.getApp(pending.appId)
        when {
            app == null -> logger.w(TAG) { "App ${pending.appId} is gone; nothing to record" }
            app.version == pending.version -> Unit // Already written by an earlier confirmation.
            else -> {
                logger.i(TAG) { "Recording self-update ${app.version} -> ${pending.version}" }
                appsRepository.update(
                    app.copy(version = pending.version, packageName = pending.packageName),
                )
            }
        }
        pendingSelfUpdates.clear()
    }

    /**
     * Whether the package that is running now is the one the record was written for.
     *
     * The version name is the primary signal — a release of this app is named after its
     * `versionName`, so the two are directly comparable. The version code is the fallback for a
     * release that broke that convention: Android refuses to replace an app with an equal or older
     * code, so a changed code can only mean the package was replaced since the record was written.
     */
    private fun landed(pending: PendingSelfUpdate): Boolean =
        selfApp.versionName == pending.version.raw ||
            selfApp.versionCode != pending.previousVersionCode

    private companion object {
        const val TAG = "SelfUpdate"
    }
}
