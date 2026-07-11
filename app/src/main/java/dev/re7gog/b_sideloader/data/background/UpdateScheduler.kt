package dev.re7gog.b_sideloader.data.background

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.re7gog.b_sideloader.data.settings.SettingsManager
import kotlinx.coroutines.flow.firstOrNull
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for how background update checking is wired up. Reads the current
 * settings and reconciles both the WorkManager periodic job and the persistent foreground
 * service so that toggling a setting always takes effect immediately (the old code only ever
 * scheduled once at startup and never cancelled stale work).
 */
@Singleton
class UpdateScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager
) {
    /** Reconciles background work with the current settings. Safe to call repeatedly. */
    suspend fun sync() {
        val autoupdates = settingsManager.useAutoupdates.firstOrNull() ?: true
        if (!autoupdates) {
            cancelPeriodicWork()
            UpdateForegroundService.stop(context)
            return
        }

        val useService = settingsManager.useForegroundService.firstOrNull() ?: false
        if (useService) {
            // The persistent service does the checking; the periodic worker would be redundant.
            cancelPeriodicWork()
            UpdateForegroundService.start(context)
        } else {
            UpdateForegroundService.stop(context)
            val useMobileData = settingsManager.useMobileData.firstOrNull() ?: false
            enqueuePeriodicWork(useMobileData)
        }
    }

    private fun enqueuePeriodicWork(useMobileData: Boolean) {
        val constraints = Constraints.Builder().setRequiresBatteryNotLow(true)
        if (!useMobileData) {
            constraints.setRequiredNetworkType(NetworkType.UNMETERED)
        } else {
            constraints.setRequiredNetworkType(NetworkType.CONNECTED)
        }
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints.build())
            .build()
        // UPDATE (not KEEP) so changed constraints — e.g. metered-network preference — apply.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun cancelPeriodicWork() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    companion object {
        const val WORK_NAME = "check_updates"
    }
}
