package dev.re7gog.b_sideloader.domain.usecase

import dev.re7gog.b_sideloader.domain.device.DeviceInfo
import dev.re7gog.b_sideloader.domain.device.SelfAppInfo
import dev.re7gog.b_sideloader.domain.device.isSelf
import dev.re7gog.b_sideloader.domain.error.AppError
import dev.re7gog.b_sideloader.domain.model.TrackedApp
import dev.re7gog.b_sideloader.domain.model.UpdateCandidate
import dev.re7gog.b_sideloader.domain.repository.AppsRepository
import dev.re7gog.b_sideloader.domain.repository.SettingsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
         * where an app this app installed can be updated silently. Anywhere else the sweep can
         * only leave every update to the user, because a session commit would need a dialog
         * nobody is there to answer. Even where it can install, each update that would ask is
         * left to the user too; see [InstallAppUseCase].
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

    /**
     * Progress of one install. Several apps can download at once, so consecutive values may be
     * about different apps; treat [appName] as a label, like [Checking.appName].
     */
    data class Installing(val appName: String, val fraction: Float?) : SweepProgress
}

/** What one sweep did. */
data class SweepReport(
    /** Apps actually queried. Tracked apps that are not on the device are not counted. */
    val checked: Int = 0,
    val withUpdates: List<String> = emptyList(),
    val installed: List<String> = emptyList(),
    val failed: List<FailedApp> = emptyList(),
    /**
     * Updates found but not installed, left for the user to install: every one under
     * [SweepMode.CheckOnly], and otherwise those that need confirming, failed, or were cancelled.
     */
    val waiting: List<WaitingApp> = emptyList(),
) {
    val hasUpdates: Boolean get() = withUpdates.isNotEmpty()

    data class FailedApp(val appName: String, val error: AppError)

    /** An app with an update for the user to install; [id] is its row. */
    data class WaitingApp(val id: Long, val name: String)
}

/**
 * Checks every tracked app and, when allowed, installs what is new.
 *
 * The check phase is delegated to [CheckUpdatesUseCase], which owns both the "skip apps that are
 * not installed" rule and the sequential/parallel choice. Every update is then handed over at
 * once, and [dev.re7gog.b_sideloader.domain.installer.InstallScheduler] decides what actually
 * runs: downloads in parallel within per-source limits, installs strictly one at a time —
 * `PackageInstaller` sessions, and on the unprivileged path the dialogs they raise, do not overlap
 * sanely.
 *
 * Installs go through [InstallCoordinator], the same one the screens use. So a background update
 * shares the limits with one the user started, never installs an app the user is already
 * installing (it waits for that install's result), and shows its progress on the apps list and
 * the app's page like any other install.
 *
 * Failure of one app never aborts the sweep — a rate-limited repository or a channel the user left
 * must not stop the other twenty apps from updating — but cancellation always propagates, so
 * WorkManager stopping the worker actually stops the work.
 *
 * Nothing in a sweep asks the user anything. An update that would need them to confirm it — an app
 * another installer put on the device, say — is not downloaded, not failed and not retried: it is
 * [SweepReport.waiting], for the worker to offer in a notification that installs it in the app.
 *
 * B-SideLoader's own update, if there is one, is started only after every other install has
 * finished: replacing the package kills the worker or service running this sweep, and anything
 * still downloading or queued would simply never happen.
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
        if (effectiveMode == SweepMode.CheckOnly) {
            return report.copy(waiting = updatable.map { it.app.waiting() })
        }

        // hasUpdate implies a candidate, but read it defensively rather than asserting.
        val (self, others) = updatable
            .mapNotNull { outcome -> outcome.check?.candidate?.let { outcome.app to it } }
            .partition { (app, _) -> selfApp.isSelf(app) }
        // Shared by every concurrent install; serialized so a caller can update a notification
        // from it without inventing its own lock.
        val progressLock = Mutex()
        val reportProgress: suspend (SweepProgress) -> Unit = { progressLock.withLock { onProgress(it) } }

        val results = coroutineScope {
            others.map { (app, candidate) ->
                async { app to installOne(app, candidate, reportProgress) }
            }.awaitAll()
        } + self.map { (app, candidate) -> app to installOne(app, candidate, reportProgress) }
        // In check order, whatever order the installs finished in.
        results.forEach { (app, result) -> report = report.with(app, result) }
        return report
    }

    /**
     * The coordinator never throws but for cancellation, which must propagate; every other failure
     * comes back as an [InstallResult.Failed].
     */
    private suspend fun installOne(
        app: TrackedApp,
        candidate: UpdateCandidate,
        onProgress: suspend (SweepProgress) -> Unit,
    ): InstallResult? = installCoordinator.installAndAwait(app, candidate) { progress ->
        onProgress(SweepProgress.Installing(app.name, progress.fraction))
    }

    private fun SweepReport.with(app: TrackedApp, result: InstallResult?): SweepReport =
        when (result) {
            is InstallResult.Installed -> copy(installed = installed + app.name)
            is InstallResult.Failed -> copy(
                failed = failed + SweepReport.FailedApp(app.name, result.error),
                waiting = waiting + app.waiting(),
            )
            // Not a failure: nothing to retry until the user is there to confirm it.
            is InstallResult.NeedsConfirmation -> copy(waiting = waiting + app.waiting())
            // The user's own install of this app was cancelled under it: neither installed nor
            // failed, and still in `withUpdates` for the next sweep to pick up.
            null -> copy(waiting = waiting + app.waiting())
        }

    private fun TrackedApp.waiting() = SweepReport.WaitingApp(id, name)
}
