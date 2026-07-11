package dev.re7gog.b_sideloader.data.background

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.re7gog.b_sideloader.data.settings.SettingsManager
import dev.re7gog.b_sideloader.data.updater.UpdatesManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val updatesManager: UpdatesManager,
    private val settingsManager: SettingsManager
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        return try {
            val canInstall = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ||
                    settingsManager.installerMode.first().isPrivileged
            if (canInstall) {
                // Promotion to a foreground service can be refused (Android 12+ restrictions);
                // don't let that abort the whole check — fall back to a plain background run.
                runCatching { setForeground(progressForegroundInfo(null, 0)) }
                updatesManager.checkAllAndInstall { app, progress ->
                    runCatching { setForeground(progressForegroundInfo(app, progress)) }
                }
            } else {
                updatesManager.checkAllUpdates { apps ->
                    NotificationHelper.showUpdateAlert(applicationContext, apps)
                }
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Update check worker failed: ${e.message}")
            // Transient failures (network, API) — let WorkManager back off and retry a few times.
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    private fun progressForegroundInfo(app: String?, progress: Int): ForegroundInfo {
        val notification = NotificationHelper.buildProgressNotification(applicationContext, app, progress)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NotificationHelper.MONITOR_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NotificationHelper.MONITOR_NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val TAG = "UpdateCheckWorker"
        private const val MAX_RETRIES = 3
    }
}
