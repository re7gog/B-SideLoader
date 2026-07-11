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
            context.getString(R.string.notif_channel_alerts_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notif_channel_alerts_desc)
        }

        val updateWithProgressChannel = NotificationChannel(
            UPDATE_PROGRESS_CHANNEL_ID,
            context.getString(R.string.notif_channel_progress_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notif_channel_progress_desc)
        }

        val monitorChannel = NotificationChannel(
            UPDATE_MONITOR_CHANNEL_ID,
            context.getString(R.string.notif_channel_monitor_name),
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = context.getString(R.string.notif_channel_monitor_desc)
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
            .setContentTitle(context.getString(R.string.notif_monitor_watching))
            .setContentText(text)
            .setContentIntent(contentIntent(context, 0, doUpdate = false))
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    /** Determinate-progress notification shown while an update is downloading/installing. */
    fun buildProgressNotification(context: Context, app: String?, progress: Int): Notification {
        val appName = app ?: context.getString(R.string.notif_apps_generic)
        return NotificationCompat.Builder(context, UPDATE_PROGRESS_CHANNEL_ID)
            .setSmallIcon(R.drawable.update_24px)
            .setContentTitle(context.getString(R.string.notif_progress_title))
            .setContentText(context.getString(R.string.notif_progress_text, appName))
            .setOngoing(true)
            .setProgress(100, progress, false)
            .build()
    }

    /** User-facing alert that new versions are available (non-silent installers). */
    fun showUpdateAlert(context: Context, apps: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, UPDATE_ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.update_24px)
            .setContentTitle(context.getString(R.string.notif_update_available_title))
            .setContentText(context.getString(R.string.notif_update_available_text, apps))
            .setContentIntent(contentIntent(context, apps.hashCode(), doUpdate = true))
            .setAutoCancel(true)
            .build()
        notificationManager.notify(apps.hashCode(), notification)
    }
}
