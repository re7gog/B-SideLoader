package dev.re7gog.b_sideloader.data.background

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object NotificationHelper {
    const val UPDATE_ALERT_CHANNEL_ID = "update_alerts_channel"
    const val UPDATE_PROGRESS_CHANNEL_ID = "update_progress_channel"

    fun init(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val updateChannel = NotificationChannel(
            UPDATE_ALERT_CHANNEL_ID,
            "Updates available notifications",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifies when there are apps updates"
        }

        val updateWithProgressChannel = NotificationChannel(
            UPDATE_PROGRESS_CHANNEL_ID,
            "Updates in progress notifications",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifies when there are apps updates in progress"
        }

        notificationManager.createNotificationChannel(updateChannel)
        notificationManager.createNotificationChannel(updateWithProgressChannel)
    }
}