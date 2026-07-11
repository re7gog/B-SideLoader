package dev.re7gog.b_sideloader.data.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dev.re7gog.b_sideloader.MainActivity
import dev.re7gog.b_sideloader.R

object NotificationHelper {
    const val UPDATE_ALERT_CHANNEL_ID = "update_alerts_channel"
    const val UPDATE_PROGRESS_CHANNEL_ID = "update_progress_channel"
    const val UPDATE_MONITOR_CHANNEL_ID = "update_monitor_channel"

    /** Stable id for the persistent foreground-service notification. */
    const val MONITOR_NOTIFICATION_ID = 424242

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

        val monitorChannel = NotificationChannel(
            UPDATE_MONITOR_CHANNEL_ID,
            "Background update monitoring",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Persistent notification shown while the app watches for updates in the background"
        }

        notificationManager.createNotificationChannel(updateChannel)
        notificationManager.createNotificationChannel(updateWithProgressChannel)
        notificationManager.createNotificationChannel(monitorChannel)
    }

    /** PendingIntent that opens the app. [doUpdate] triggers the in-app update flow. */
    private fun contentIntent(context: Context, requestCode: Int, doUpdate: Boolean): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            if (doUpdate) putExtra("DO_UPDATE", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Ongoing, low-key notification for the persistent monitoring service. */
    fun buildMonitorNotification(context: Context, text: String): Notification {
        return NotificationCompat.Builder(context, UPDATE_MONITOR_CHANNEL_ID)
            .setSmallIcon(R.drawable.update_24px)
            .setContentTitle("Watching for app updates")
            .setContentText(text)
            .setContentIntent(contentIntent(context, 0, doUpdate = false))
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    /** Determinate-progress notification shown while an update is downloading/installing. */
    fun buildProgressNotification(context: Context, app: String?, progress: Int): Notification {
        return NotificationCompat.Builder(context, UPDATE_PROGRESS_CHANNEL_ID)
            .setSmallIcon(R.drawable.update_24px)
            .setContentTitle("Updates in progress")
            .setContentText("Updating ${app ?: "apps"}")
            .setOngoing(true)
            .setProgress(100, progress, false)
            .build()
    }

    /** User-facing alert that new versions are available (non-silent installers). */
    fun showUpdateAlert(context: Context, apps: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, UPDATE_ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.update_24px)
            .setContentTitle("Updates available")
            .setContentText("New version of$apps available")
            .setContentIntent(contentIntent(context, apps.hashCode(), doUpdate = true))
            .setAutoCancel(true)
            .build()
        notificationManager.notify(apps.hashCode(), notification)
    }
}
