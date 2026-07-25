package dev.re7gog.b_sideloader.domain.usecase

import dev.re7gog.b_sideloader.core.coroutines.suspendRunCatching
import dev.re7gog.b_sideloader.core.log.Logger
import dev.re7gog.b_sideloader.domain.error.AppError
import dev.re7gog.b_sideloader.domain.installer.InstallerGateway
import dev.re7gog.b_sideloader.domain.model.DownloadRef
import dev.re7gog.b_sideloader.domain.model.InstallOutcome
import dev.re7gog.b_sideloader.domain.model.InstallProgress
import dev.re7gog.b_sideloader.domain.model.TrackedApp
import dev.re7gog.b_sideloader.domain.model.UpdateCandidate
import dev.re7gog.b_sideloader.domain.repository.AppsRepository
import dev.re7gog.b_sideloader.domain.repository.TelegramRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import javax.inject.Inject

/** What the caller of [InstallAppUseCase] observes. */
sealed interface AppInstallEvent {
    data class Progress(val progress: InstallProgress) : AppInstallEvent

    /**
     * Installed *and* persisted. [app] carries the row id assigned on first save and the version
     * marker that was just installed, so the caller can switch straight to "saved app" mode.
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
 * The returned flow is cold and cancellable. Abandoning it cancels the download and drops any
 * temporary Telegram copy — see [onCompletion] below.
 */
class InstallAppUseCase @Inject constructor(
    private val installerGateway: InstallerGateway,
    private val appsRepository: AppsRepository,
    private val telegramRepository: TelegramRepository,
    private val logger: Logger,
) {
    operator fun invoke(app: TrackedApp, candidate: UpdateCandidate): Flow<AppInstallEvent> = flow {
        installerGateway.install(candidate.download).collect { progress ->
            if (progress !is InstallProgress.Finished) {
                emit(AppInstallEvent.Progress(progress))
                return@collect
            }
            when (val outcome = progress.outcome) {
                is InstallOutcome.Success -> emit(AppInstallEvent.Completed(persist(app, candidate, outcome)))
                is InstallOutcome.Failure -> emit(AppInstallEvent.Failed(outcome.error))
            }
        }
    }.onCompletion {
        // Runs on success, failure *and* cancellation. TDLib keeps a full copy of every file it
        // downloads; without this the cache grows by one APK per install attempt.
        releaseTelegramCopy(candidate.download)
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

    private suspend fun releaseTelegramCopy(download: DownloadRef) {
        if (download !is DownloadRef.TelegramFile) return
        suspendRunCatching { telegramRepository.discardLocalCopy(download.fileId) }
            .onFailure { logger.w(TAG, it) { "Could not drop Telegram copy of file ${download.fileId}" } }
    }

    private companion object {
        const val TAG = "InstallApp"
    }
}
