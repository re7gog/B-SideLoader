package dev.re7gog.b_sideloader.data.logic

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import dev.re7gog.b_sideloader.R

object NotificationHelper {
    const val INSTALL_CHANNEL_ID = "install_progress_channel"
    const val UPDATE_ALERT_CHANNEL_ID = "update_alerts_channel"
    const val DOWNLOAD_NOTIFICATION_ID = 1984

    fun init(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val installChannel = NotificationChannel(
            INSTALL_CHANNEL_ID,
            "Download and install",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows current progress of downloading and installing APK"
            setShowBadge(false)
        }
        val updateChannel = NotificationChannel(
            UPDATE_ALERT_CHANNEL_ID,
            "Update notifications",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifies when there is an app update"
        }

        notificationManager.createNotificationChannels(listOf(installChannel, updateChannel))
    }

    fun getInstallNotificationBuilder(context: Context, appName: String): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, INSTALL_CHANNEL_ID)
            .setSmallIcon(R.drawable.download_24px)
            .setContentTitle(appName)
            .setContentText("Downloading and installing...")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
    }
}