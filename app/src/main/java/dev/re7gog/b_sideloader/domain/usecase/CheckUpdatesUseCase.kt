package dev.re7gog.b_sideloader.domain.usecase

import dev.re7gog.b_sideloader.core.coroutines.rethrowIfCancellation
import dev.re7gog.b_sideloader.core.log.Logger
import dev.re7gog.b_sideloader.domain.error.AppError
import dev.re7gog.b_sideloader.domain.installer.PackageInspector
import dev.re7gog.b_sideloader.domain.model.AppSettings
import dev.re7gog.b_sideloader.domain.model.TrackedApp
import dev.re7gog.b_sideloader.domain.model.UpdateCheck
import dev.re7gog.b_sideloader.domain.repository.SettingsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

/** What a batch update check found for one app. */
data class UpdateCheckOutcome(
    val app: TrackedApp,
    /** `null` when the app was [skipped] or the lookup failed. */
    val check: UpdateCheck?,
    val error: AppError? = null,
    /** True when no request was made at all because the package is not on the device. */
    val skipped: Boolean = false,
) {
    val hasUpdate: Boolean get() = check?.hasUpdate == true
}

/**
 * Checks many apps against their sources in one pass.
 *
 * Two rules live here rather than at each call site, because there are now three of them (the
 * apps list, the periodic worker and the monitor service) and they must not disagree:
 *
 *  1. **Only installed apps are queried.** A tracked app the user never installed — or uninstalled
 *     outside B-SideLoader — has nothing to update, so asking GitHub or Telegram about it spends a
 *     request (and, unauthenticated, a slice of an hourly quota) to learn nothing. Skipped apps are
 *     still returned, flagged, so a caller can tell "not checked" from "no candidate".
 *  2. **Concurrency is a setting, not a hard-coded choice.** Sequential is the default: a burst of
 *     requests is exactly what trips GitHub's rate limit, and the failure mode there is that
 *     *every* app fails at once. With [AppSettings.parallelUpdateChecks] on, lookups run up to
 *     [AppSettings.MAX_PARALLEL_CHECKS] at a time — the cap matters because OkHttp itself only
 *     allows 5 concurrent calls per host, so unbounded fan-out would just queue inside OkHttp while
 *     making the rate limiter angrier.
 *
 * One app's failure is recorded and never aborts the rest; cancellation always propagates.
 */
class CheckUpdatesUseCase @Inject constructor(
    private val resolveUpdate: ResolveUpdateUseCase,
    private val settingsRepository: SettingsRepository,
    private val packageInspector: PackageInspector,
    private val logger: Logger,
) {
    /**
     * @param onProgress called once per app as it settles, with the app that just finished and how
     *   many are done out of how many will be queried. Under parallel checks the order is
     *   arbitrary, so the app is "one that just finished", not "the current one".
     */
    suspend operator fun invoke(
        apps: List<TrackedApp>,
        onProgress: suspend (app: TrackedApp, done: Int, total: Int) -> Unit = { _, _, _ -> },
    ): List<UpdateCheckOutcome> {
        if (apps.isEmpty()) return emptyList()

        val (checkable, skipped) = apps.partition { packageInspector.isInstalled(it.packageName) }
        val skippedOutcomes = skipped.map { UpdateCheckOutcome(it, check = null, skipped = true) }
        if (checkable.isEmpty()) return skippedOutcomes

        val checked = if (settingsRepository.current().parallelUpdateChecks) {
            checkInParallel(checkable, onProgress)
        } else {
            checkSequentially(checkable, onProgress)
        }
        return checked + skippedOutcomes
    }

    private suspend fun checkSequentially(
        apps: List<TrackedApp>,
        onProgress: suspend (TrackedApp, Int, Int) -> Unit,
    ): List<UpdateCheckOutcome> = apps.mapIndexed { index, app ->
        checkOne(app).also { onProgress(app, index + 1, apps.size) }
    }

    private suspend fun checkInParallel(
        apps: List<TrackedApp>,
        onProgress: suspend (TrackedApp, Int, Int) -> Unit,
    ): List<UpdateCheckOutcome> = coroutineScope {
        val permits = Semaphore(AppSettings.MAX_PARALLEL_CHECKS)
        // onProgress is a suspend callback shared by every worker; serialize it so a caller can
        // update a notification or UI state from it without inventing its own lock.
        val progressLock = Mutex()
        var done = 0

        apps.map { app ->
            async {
                permits.withPermit { checkOne(app) }.also {
                    progressLock.withLock { onProgress(app, ++done, apps.size) }
                }
            }
        }.awaitAll()
    }

    private suspend fun checkOne(app: TrackedApp): UpdateCheckOutcome = try {
        UpdateCheckOutcome(app = app, check = resolveUpdate(app))
    } catch (e: Throwable) {
        e.rethrowIfCancellation()
        logger.w(TAG, e) { "Update check failed for ${app.name}" }
        UpdateCheckOutcome(app = app, check = null, error = e as? AppError ?: AppError.Unexpected(e))
    }

    private companion object {
        const val TAG = "CheckUpdates"
    }
}
