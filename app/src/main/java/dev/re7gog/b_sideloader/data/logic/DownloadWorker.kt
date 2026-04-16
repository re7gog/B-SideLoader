package dev.re7gog.b_sideloader.data.logic

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.re7gog.b_sideloader.domain.logic.IInstallManager

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val installManager: IInstallManager
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val url = inputData.getString("URL") ?: return Result.failure()

        setForeground(createForegroundInfo(0f))

        return try {
            installManager.downloadAndInstall(url)
                .collect { progress ->
                    setProgress(workDataOf("PROGRESS" to progress))
                    setForeground(createForegroundInfo(progress))
                }
            Result.success()
        } catch (_: Exception) {
            Result.failure()
        }
    }

    private fun createForegroundInfo(progress: Float): ForegroundInfo {
        val notification = NotificationHelper.getInstallNotificationBuilder(applicationContext, "B-SideLoader")
            .setProgress(100, (progress * 100).toInt(), false)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NotificationHelper.DOWNLOAD_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NotificationHelper.DOWNLOAD_NOTIFICATION_ID, notification)
        }
    }
}