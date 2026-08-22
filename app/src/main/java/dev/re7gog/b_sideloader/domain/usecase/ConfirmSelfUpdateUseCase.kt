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
     * Whether the package on the device was replaced since the record was written.
     *
     * The install time is the signal that works in every case, including the one that matters most
     * here: the *first* install through B-SideLoader is a reinstall of the build already running,
     * which leaves the version code untouched. The version code is kept as a second opinion for a
     * ROM that reports a stale install time.
     *
     * A release name cannot be used for this — the releases of this app are named `v1.0.0` while
     * `versionName` is `1.0.0`, so the two never compare equal.
     */
    private fun landed(pending: PendingSelfUpdate): Boolean =
        selfApp.lastUpdateTime != pending.previousLastUpdateTime ||
            selfApp.versionCode != pending.previousVersionCode

    private companion object {
        const val TAG = "SelfUpdate"
    }
}
