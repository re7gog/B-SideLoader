package dev.re7gog.b_sideloader.data.background

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.re7gog.b_sideloader.core.coroutines.suspendRunCatching
import dev.re7gog.b_sideloader.core.log.Logger
import dev.re7gog.b_sideloader.domain.usecase.RunUpdateSweepUseCase
import dev.re7gog.b_sideloader.domain.usecase.SweepProgress
import kotlinx.coroutines.CancellationException

/**
 * The deferred update check.
 *
 * WorkManager owns the retry policy, so the worker only has to classify its outcome:
 * [androidx.work.ListenableWorker.Result.retry] for something that might work later, `failure`
 * once the attempts are spent. Cancellation is rethrown rather than reported as a failure — a
 * worker that WorkManager stopped has not failed, and reporting it as such would burn a retry.
 */
@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val runUpdateSweep: RunUpdateSweepUseCase,
    private val notifications: NotificationCenter,
    private val logger: Logger,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            setForegroundQuietly(null, INDETERMINATE)

            val report = runUpdateSweep { progress ->
                when (progress) {
                    is SweepProgress.Checking -> setForegroundQuietly(progress.appName, INDETERMINATE)
                    is SweepProgress.Installing -> setForegroundQuietly(
                        progress.appName,
                        progress.fraction?.let { (it * PERCENT).toInt() } ?: INDETERMINATE,
                    )
                }
            }

            // Anything the sweep found but could not install itself becomes a user-facing alert.
            val notInstalled = report.withUpdates - report.installed.toSet()
            notifications.showUpdatesAvailable(notInstalled)
            notifications.cancelProgress()

            logger.i(TAG) {
                "checked=${report.checked} updates=${report.withUpdates.size} " +
                    "installed=${report.installed.size} failed=${report.failed.size}"
            }
            if (report.failed.isEmpty()) Result.success() else retryOrFail()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.e(TAG, e) { "Update check failed" }
            retryOrFail()
        } finally {
            notifications.cancelProgress()
        }
    }

    private fun retryOrFail(): Result =
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()

    /**
     * Promotion to a foreground service can be refused (Android 12+ background-start
     * restrictions, or a revoked notification permission). That must not abort the check — it
     * just means the sweep runs without a visible progress notification.
     */
    private suspend fun setForegroundQuietly(appName: String?, percent: Int) {
        suspendRunCatching { setForeground(foregroundInfo(appName, percent)) }
    }

    private fun foregroundInfo(appName: String?, percent: Int): ForegroundInfo {
        val notification = notifications.progressNotification(appName, percent)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NotificationCenter.ID_UPDATE_PROGRESS,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NotificationCenter.ID_UPDATE_PROGRESS, notification)
        }
    }

    companion object {
        const val WORK_NAME = "check_updates"
        private const val TAG = "UpdateWorker"
        private const val MAX_ATTEMPTS = 3
        private const val PERCENT = 100
        private const val INDETERMINATE = -1
    }
}
