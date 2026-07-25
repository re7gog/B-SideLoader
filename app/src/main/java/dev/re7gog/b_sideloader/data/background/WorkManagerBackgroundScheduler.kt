package dev.re7gog.b_sideloader.data.background

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.re7gog.b_sideloader.core.log.Logger
import dev.re7gog.b_sideloader.domain.background.BackgroundWorkScheduler
import dev.re7gog.b_sideloader.domain.model.AppSettings
import dev.re7gog.b_sideloader.domain.model.BackgroundMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.toJavaDuration

/**
 * Reconciles WorkManager and the monitor service with the current settings.
 *
 * Idempotent by construction: [sync] describes the desired end state rather than applying a delta,
 * so it can be called from `Application.onCreate`, from `BOOT_COMPLETED` and after every settings
 * change without ever double-scheduling. The old code scheduled once at startup and never
 * cancelled, so turning auto-updates off left the job running until the next reinstall.
 */
@Singleton
class WorkManagerBackgroundScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val logger: Logger,
) : BackgroundWorkScheduler {

    private val workManager get() = WorkManager.getInstance(context)

    override suspend fun sync(settings: AppSettings) {
        if (!settings.autoUpdate) {
            logger.i(TAG) { "auto-update off: cancelling all background work" }
            cancelPeriodic()
            UpdateMonitorService.stop(context)
            return
        }

        when (settings.backgroundMode) {
            BackgroundMode.Persistent -> {
                // The service does the checking; a periodic job on top would duplicate every check.
                cancelPeriodic()
                UpdateMonitorService.start(context, logger)
            }

            BackgroundMode.Periodic -> {
                UpdateMonitorService.stop(context)
                enqueuePeriodic(settings)
            }
        }
    }

    override suspend fun runOnce() {
        val request = OneTimeWorkRequestBuilder<UpdateCheckWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager.enqueueUniqueWork(ONE_SHOT_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private fun enqueuePeriodic(settings: AppSettings) {
        val interval = maxOf(settings.checkInterval, AppSettings.MIN_CHECK_INTERVAL)
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiredNetworkType(
                if (settings.allowMeteredNetwork) NetworkType.CONNECTED else NetworkType.UNMETERED
            )
            .build()

        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(interval.toJavaDuration())
            .setConstraints(constraints)
            .build()

        // UPDATE, not KEEP: a changed constraint (e.g. the metered-network preference) has to
        // reach the already-enqueued job, which KEEP would silently discard.
        workManager.enqueueUniquePeriodicWork(
            UpdateCheckWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
        logger.i(TAG) { "periodic check every $interval, metered=${settings.allowMeteredNetwork}" }
    }

    private fun cancelPeriodic() {
        workManager.cancelUniqueWork(UpdateCheckWorker.WORK_NAME)
    }

    private companion object {
        const val TAG = "BgScheduler"
        const val ONE_SHOT_NAME = "check_updates_now"
    }
}
