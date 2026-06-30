package dev.re7gog.b_sideloader.data.background

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.re7gog.b_sideloader.MainActivity
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.data.settings.SettingsManager
import dev.re7gog.b_sideloader.data.updater.UpdatesManager
import kotlinx.coroutines.flow.firstOrNull

@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val updatesManager: UpdatesManager,
    private val settingsManager: SettingsManager
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val install = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S || settingsManager.useShizuku.firstOrNull() ?: false
        if (install) {
            setForeground(updateProgressNotification(null, 0))
            updatesManager.checkAllAndInstall { string, i -> setForeground(updateProgressNotification(string, i))  }
        } else {
            updatesManager.checkAllUpdates { showUpdateNotification(it) }
        }
        return Result.success()
    }

    private fun showUpdateNotification(apps: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            putExtra("DO_UPDATE", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            apps.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, NotificationHelper.UPDATE_ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.update_24px)
            .setContentTitle("Updates available")
            .setContentText("New version of $apps available")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(apps.hashCode(), notification)
    }

    private fun updateProgressNotification(app: String?, progress: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, NotificationHelper.UPDATE_PROGRESS_CHANNEL_ID)
            .setSmallIcon(R.drawable.update_24px)
            .setContentTitle("Updates in progress")
            .setContentText("New version of ${app ?: "some apps"} available")
            .setOngoing(true)
            .setProgress(100, progress, false)
            .build()
        return ForegroundInfo(app.hashCode(), notification)
    }
}