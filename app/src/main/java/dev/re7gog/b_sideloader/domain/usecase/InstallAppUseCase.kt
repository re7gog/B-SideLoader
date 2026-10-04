package dev.re7gog.b_sideloader.domain.usecase

import dev.re7gog.b_sideloader.core.coroutines.suspendRunCatching
import dev.re7gog.b_sideloader.core.log.Logger
import dev.re7gog.b_sideloader.domain.device.SelfAppInfo
import dev.re7gog.b_sideloader.domain.device.isSelf
import dev.re7gog.b_sideloader.domain.error.AppError
import dev.re7gog.b_sideloader.domain.installer.InstallScheduler
import dev.re7gog.b_sideloader.domain.installer.InstallerGateway
import dev.re7gog.b_sideloader.domain.model.DownloadProgress
import dev.re7gog.b_sideloader.domain.model.DownloadRef
import dev.re7gog.b_sideloader.domain.model.DownloadedApk
import dev.re7gog.b_sideloader.domain.model.InstallOutcome
import dev.re7gog.b_sideloader.domain.model.InstallProgress
import dev.re7gog.b_sideloader.domain.model.PendingSelfUpdate
import dev.re7gog.b_sideloader.domain.model.TrackedApp
import dev.re7gog.b_sideloader.domain.model.UpdateCandidate
import dev.re7gog.b_sideloader.domain.repository.AppsRepository
import dev.re7gog.b_sideloader.domain.repository.SelfUpdateStateRepository
import dev.re7gog.b_sideloader.domain.repository.TelegramRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** What the caller of [InstallAppUseCase] observes. */
sealed interface AppInstallEvent {
    data class Progress(val progress: InstallProgress) : AppInstallEvent

    /**
     * Installed *and* persisted. [app] carries the row id assigned on first save and the version
     * marker that was just installed, so the caller can switch straight to "saved app" mode.
     *
     * The one exception is B-SideLoader installing itself, whose row is written by
     * [ReconcileSelfUpdateUseCase] in the next process instead — see [InstallAppUseCase].
     */
    data class Completed(val app: TrackedApp) : AppInstallEvent

    data class Failed(val error: AppError) : AppInstallEvent
}

/**
 * Installs a candidate and records the result.
 *
 * The install and the database write are one operation on purpose: the old code let each details
 * screen listen on a global install-event bus and save the app itself, which meant an unrelated
 * install finishing elsewhere could persist the screen that happened to be open. Here the writer
 * is the same flow that started the install, so there is nothing to correlate.
 *
 * The one install that cannot work that way is B-SideLoader updating itself: replacing the package
 * kills this process, so the write below never runs. For that case — and only that case — a
 * [PendingSelfUpdate] is written *before* the install, and the row is left to
 * [ReconcileSelfUpdateUseCase] in the next process, which records the release only if the version
 * code proves it landed.
 *
 * Downloading and installing are two steps, each gated by [InstallScheduler]: the download waits
 * for a free slot on the app's source, then the install waits for the installer, which takes one
 * app at a time. So several apps can download at once while their installs still queue up.
 *
 * The returned flow is cold and cancellable. Abandoning it cancels the download and drops both the
 * downloaded APK and any temporary Telegram copy.
 */
class InstallAppUseCase @Inject constructor(
    private val installerGateway: InstallerGateway,
    private val scheduler: InstallScheduler,
    private val appsRepository: AppsRepository,
    private val telegramRepository: TelegramRepository,
    private val selfUpdates: SelfUpdateStateRepository,
    private val reconcileSelfUpdate: ReconcileSelfUpdateUseCase,
    private val selfApp: SelfAppInfo,
    private val logger: Logger,
) {
    operator fun invoke(app: TrackedApp, candidate: UpdateCandidate): Flow<AppInstallEvent> = flow {
        val isSelfUpdate = selfApp.isSelf(app) && app.isSaved
        if (isSelfUpdate) recordSelfUpdate(app, candidate)
        scheduler.track {
            emit(AppInstallEvent.Progress(InstallProgress.Queued))
            val apk = scheduler.download(app.source.kind) {
                download(candidate.download, isSelfUpdate)
            } ?: return@track
            try {
                emit(AppInstallEvent.Progress(InstallProgress.Queued))
                // B-SideLoader's own update replaces this process, so it waits for the others.
                scheduler.install(last = isSelfUpdate) { install(app, candidate, apk, isSelfUpdate) }
            } finally {
                // Also when cancelled while waiting for the installer.
                withContext(NonCancellable) { installerGateway.discard(apk) }
            }
        }
    }.onCompletion {
        // Runs on success, failure *and* cancellation. TDLib keeps a full copy of every file it
        // downloads; without this the cache grows by one APK per install attempt.
        releaseTelegramCopy(candidate.download)
    }

    /** Fetches the APK, or reports why not and returns null. */
    private suspend fun FlowCollector<AppInstallEvent>.download(
        source: DownloadRef,
        isSelfUpdate: Boolean,
    ): DownloadedApk? {
        emit(AppInstallEvent.Progress(InstallProgress.Preparing))
        var error: AppError = AppError.Storage("The download ended without a file")
        var apk: DownloadedApk? = null
        installerGateway.download(source).collect { progress ->
            when (progress) {
                is DownloadProgress.Downloading ->
                    emit(AppInstallEvent.Progress(InstallProgress.Downloading(progress.fraction)))

                is DownloadProgress.Downloaded -> apk = progress.apk
                is DownloadProgress.Failed -> error = progress.error
            }
        }
        if (apk == null) fail(error, isSelfUpdate)
        return apk
    }

    private suspend fun FlowCollector<AppInstallEvent>.install(
        app: TrackedApp,
        candidate: UpdateCandidate,
        apk: DownloadedApk,
        isSelfUpdate: Boolean,
    ) {
        installerGateway.installDownloaded(apk).collect { progress ->
            if (progress !is InstallProgress.Finished) {
                emit(AppInstallEvent.Progress(progress))
                return@collect
            }
            when (val outcome = progress.outcome) {
                is InstallOutcome.Success -> {
                    val installed = if (isSelfUpdate) {
                        // Not written here: the version code of this process cannot prove the
                        // replace happened, and the reconciliation in the next one can. The copy
                        // only keeps the screen from offering the update again meanwhile.
                        app.copy(version = candidate.version)
                    } else {
                        persist(app, candidate, outcome)
                    }
                    emit(AppInstallEvent.Completed(installed))
                }

                is InstallOutcome.Failure -> fail(outcome.error, isSelfUpdate)
            }
        }
    }

    private suspend fun FlowCollector<AppInstallEvent>.fail(error: AppError, isSelfUpdate: Boolean) {
        // Seen by the process that started it, so there is nothing to judge later.
        if (isSelfUpdate) selfUpdates.clearPending()
        emit(AppInstallEvent.Failed(error))
    }

    private suspend fun persist(
        app: TrackedApp,
        candidate: UpdateCandidate,
        outcome: InstallOutcome.Success,
    ): TrackedApp {
        val installed = app.copy(
            version = candidate.version,
            // A freshly searched app has no package name until the installer reports one.
            packageName = outcome.packageName?.takeIf { it.isNotBlank() } ?: app.packageName,
        )
        return if (installed.isSaved) {
            appsRepository.update(installed)
            installed
        } else {
            installed.copy(id = appsRepository.add(installed))
        }
    }

    /**
     * Notes what is about to be installed over this very process.
     *
     * Only saved apps qualify: the record points at a row id, and an app that has never been saved
     * has none. In practice the app's own row is seeded on first run, so it is always saved.
     *
     * Reconciling first is what makes the record safe to write: once this process has reconciled
     * it never judges a record again, so this one waits for the next process — the one whose
     * version code can tell whether it landed. Normally that already happened at startup, and
     * this returns at once.
     *
     * Cancelling the install leaves the record behind; that is deliberate rather than sloppy, as
     * the next reconciliation drops a record whose version code never went up.
     */
    private suspend fun recordSelfUpdate(app: TrackedApp, candidate: UpdateCandidate) {
        reconcileSelfUpdate()
        logger.i(TAG) { "Installing over ourselves; recording ${candidate.version} as pending" }
        selfUpdates.markPending(PendingSelfUpdate(appId = app.id, releaseName = candidate.version))
    }

    private suspend fun releaseTelegramCopy(download: DownloadRef) {
        if (download !is DownloadRef.TelegramFile) return
        suspendRunCatching { telegramRepository.discardLocalCopy(download.fileId) }
            .onFailure { logger.w(TAG, it) { "Could not drop Telegram copy of file ${download.fileId}" } }
    }

    private companion object {
        const val TAG = "InstallApp"
    }
}
