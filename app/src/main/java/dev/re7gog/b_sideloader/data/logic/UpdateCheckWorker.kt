package dev.re7gog.b_sideloader.data.logic

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.re7gog.b_sideloader.MainActivity
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.data.remote.GithubApi
import dev.re7gog.b_sideloader.domain.model.AppWithDetails
import dev.re7gog.b_sideloader.domain.repository.AppsRepository
import kotlinx.coroutines.flow.first

@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: AppsRepository,
    private val githubApi: GithubApi
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val apps = repository.getAllAppsStream().first()
        for (app in apps) {
            try {
                val ownerRepo = app.githubDetails?.fullName?.split("/") ?: listOf("", "")
                val assets = githubApi.getReleases(owner = ownerRepo[0], repo = ownerRepo[1])[0].assets
                // TODO: Check if new version
                showUpdateNotification(app, findCurrentAbiApk(assets) ?: "")
            } catch (_: Exception) { continue }
        }
        return Result.success()
    }


    private fun showUpdateNotification(app: AppWithDetails, downloadUrl: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val updateIntent = Intent(applicationContext, DownloadReceiver::class.java).apply {
            action = "ACTION_START_UPDATE"
            putExtra("APK_URL", downloadUrl)
            putExtra("APP_NAME", app.app.name)
        }

        val updatePendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            app.app.id.hashCode(),
            updateIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // TODO: Receive app id from notification
        val contentIntent = Intent(applicationContext, MainActivity::class.java).apply {
            putExtra("APP_ID", app.app.id)
        }

        val contentPendingIntent = PendingIntent.getActivity(
            applicationContext,
            app.app.id.hashCode() + 1,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, NotificationHelper.UPDATE_ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.download_24px)
            .setContentTitle("Update available")
            .setContentText("New version of ${app.app.name} is available")
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(R.drawable.download_24px, "Update now", updatePendingIntent)
            .build()

        notificationManager.notify(app.app.id.hashCode(), notification)
    }
}