package dev.re7gog.b_sideloader.domain.usecase

import dev.re7gog.b_sideloader.domain.device.DeviceInfo
import dev.re7gog.b_sideloader.domain.device.SelfAppInfo
import dev.re7gog.b_sideloader.domain.device.isSelf
import dev.re7gog.b_sideloader.domain.error.AppError
import dev.re7gog.b_sideloader.domain.model.TrackedApp
import dev.re7gog.b_sideloader.domain.model.UpdateCandidate
import dev.re7gog.b_sideloader.domain.repository.AppsRepository
import dev.re7gog.b_sideloader.domain.repository.SettingsRepository
import javax.inject.Inject

/** What the sweep is allowed to do. */
enum class SweepMode {
    /** Only report which apps have updates. */
    CheckOnly,

    /** Also download and install them. */
    CheckAndInstall,
    ;

    companion object {
        /**
         * Installing without a visible prompt needs either a privileged installer, or Android 12+
         * where a self-installed app can be updated silently. Anywhere else the sweep can only
         * notify, because a session commit would throw an install dialog at a user who is not
         * looking at the phone.
         */
        fun forEnvironment(privilegedInstaller: Boolean, silentSelfUpdates: Boolean): SweepMode =
            if (privilegedInstaller || silentSelfUpdates) CheckAndInstall else CheckOnly
    }
}

/** Progress callbacks, so a worker or service can keep its notification honest. */
sealed interface SweepProgress {
    /**
     * One app finished being checked. [appName] is the app that just settled — with parallel
     * checks on, that is not necessarily the one that started last, so treat it as a label rather
     * than as "the current app".
     */
    data class Checking(val appName: String, val done: Int, val total: Int) : SweepProgress

    data class Installing(val appName: String, val fraction: Float?) : SweepProgress
}

/** What one sweep did. */
data class SweepReport(
    /** Apps actually queried. Tracked apps that are not on the device are not counted. */
    val checked: Int = 0,
    val withUpdates: List<String> = emptyList(),
    val installed: List<String> = emptyList(),
    val failed: List<FailedApp> = emptyList(),
) {
    val hasUpdates: Boolean get() = withUpdates.isNotEmpty()

    data class FailedApp(val appName: String, val error: AppError)
}

/**
 * Checks every tracked app and, when allowed, installs what is new.
 *
 * The check phase is delegated to [CheckUpdatesUseCase], which owns both the "skip apps that are
 * not installed" rule and the sequential/parallel choice. The install phase stays strictly
 * sequential no matter what that setting says: `PackageInstaller` sessions — and, on the
 * unprivileged path, the confirmation dialogs they raise — do not overlap sanely.
 *
 * Installs go through [InstallCoordinator], the same queue the screens use. So a background update
 * waits for one the user started instead of overlapping it, never installs an app the user is
 * already installing (it waits for that install's result), and shows its progress on the apps
 * list and the app's page like any other install.
 *
 * Failure of one app never aborts the sweep — a rate-limited repository or a channel the user left
 * must not stop the other twenty apps from updating — but cancellation always propagates, so
 * WorkManager stopping the worker actually stops the work.
 *
 * B-SideLoader's own update, if there is one, is installed last: replacing the package kills the
 * worker or service running this sweep, and anything queued behind it would simply never happen.
 * And its own row is reconciled before anything is read, so a sweep in the process that a
 * self-update just started does not find — and install — that same update again.
 */
class RunUpdateSweepUseCase @Inject constructor(
    private val appsRepository: AppsRepository,
    private val settingsRepository: SettingsRepository,
    private val checkUpdates: CheckUpdatesUseCase,
    private val installCoordinator: InstallCoordinator,
    private val reconcileSelfUpdate: ReconcileSelfUpdateUseCase,
    private val deviceInfo: DeviceInfo,
    private val selfApp: SelfAppInfo,
) {
    suspend operator fun invoke(
        mode: SweepMode = SweepMode.CheckAndInstall,
        onProgress: suspend (SweepProgress) -> Unit = {},
    ): SweepReport {
        val settings = settingsRepository.current()
        val effectiveMode = when (mode) {
            SweepMode.CheckOnly -> SweepMode.CheckOnly
            SweepMode.CheckAndInstall -> SweepMode.forEnvironment(
                privilegedInstaller = settings.installerMode.isPrivileged,
                silentSelfUpdates = deviceInfo.supportsSilentSelfUpdates,
            )
        }

        reconcileSelfUpdate()
        val apps = appsRepository.getApps().filter { it.autoUpdate }
        val outcomes = checkUpdates(apps) { app, done, total ->
            onProgress(SweepProgress.Checking(app.name, done, total))
        }

        val updatable = outcomes.filter { it.hasUpdate }
        var report = SweepReport(
            checked = outcomes.count { !it.skipped },
            withUpdates = updatable.map { it.app.name },
            failed = outcomes.mapNotNull { outcome ->
                outcome.error?.let { SweepReport.FailedApp(outcome.app.name, it) }
            },
        )
        if (effectiveMode == SweepMode.CheckOnly) return report

        // Stable sort, so everything else keeps its check order and only this app moves to the end.
        updatable.sortedBy { selfApp.isSelf(it.app) }.forEach { outcome ->
            // hasUpdate implies a candidate, but read it defensively rather than asserting.
            val candidate = outcome.check?.candidate ?: return@forEach
            report = installOne(outcome.app, candidate, report, onProgress)
        }
        return report
    }

    /**
     * The coordinator never throws but for cancellation, which must propagate; every other failure
     * comes back as an [InstallResult.Failed].
     */
    private suspend fun installOne(
        app: TrackedApp,
        candidate: UpdateCandidate,
        report: SweepReport,
        onProgress: suspend (SweepProgress) -> Unit,
    ): SweepReport {
        val result = installCoordinator.installAndAwait(app, candidate) { progress ->
            onProgress(SweepProgress.Installing(app.name, progress.fraction))
        }
        return when (result) {
            is InstallResult.Installed -> report.copy(installed = report.installed + app.name)
            is InstallResult.Failed ->
                report.copy(failed = report.failed + SweepReport.FailedApp(app.name, result.error))
            // The user's own install of this app was cancelled under it: neither installed nor
            // failed, and still in `withUpdates` for the next sweep to pick up.
            null -> report
        }
    }
}
