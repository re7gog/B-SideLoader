package dev.re7gog.b_sideloader.data.background

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.data.settings.SettingsManager
import dev.re7gog.b_sideloader.data.updater.UpdatesManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * Persistent foreground service that periodically checks for app updates. Unlike the
 * WorkManager path, it keeps the process alive with an ongoing notification, which is far
 * more reliable on aggressive OEM ROMs (Xiaomi/Huawei/Oppo/etc.) that routinely kill
 * background jobs. Opt-in only, because it costs battery.
 */
@AndroidEntryPoint
class UpdateForegroundService : Service() {
    @Inject
    lateinit var updatesManager: UpdatesManager

    @Inject
    lateinit var settingsManager: SettingsManager

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var loopJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground(getString(R.string.notif_monitor_watching))
        // Guard against re-delivered START_STICKY restarts spawning multiple loops.
        if (loopJob?.isActive != true) {
            loopJob = scope.launch { monitorLoop() }
        }
        return START_STICKY
    }

    private suspend fun monitorLoop() {
        while (scope.isActive) {
            try {
                runCheck()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed: ${e.message}")
            }
            updateNotification(getString(R.string.notif_monitor_watching))
            delay(CHECK_INTERVAL_MS.milliseconds)
        }
    }

    private suspend fun runCheck() {
        updateNotification(getString(R.string.notif_monitor_checking))
        val canInstall = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ||
                settingsManager.installerMode.first().isPrivileged
        if (canInstall) {
            updatesManager.checkAllAndInstall { app, _ ->
                updateNotification(getString(R.string.notif_monitor_updating, app))
            }
        } else {
            updatesManager.checkAllUpdates { apps ->
                NotificationHelper.showUpdateAlert(applicationContext, apps)
            }
        }
    }

    private fun startAsForeground(text: String) {
        val notification = NotificationHelper.buildMonitorNotification(applicationContext, text)
        val type = when {
            // dataSync foreground services are capped (~6h/day) on Android 14+, which would
            // kill a persistent monitor; specialUse fits an always-on updater and has no cap.
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            else -> 0
        }
        ServiceCompat.startForeground(this, NotificationHelper.MONITOR_NOTIFICATION_ID, notification, type)
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(
            NotificationHelper.MONITOR_NOTIFICATION_ID,
            NotificationHelper.buildMonitorNotification(applicationContext, text)
        )
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "UpdateFgService"
        private val CHECK_INTERVAL_MS = TimeUnit.HOURS.toMillis(6)

        /** Starts the service as a foreground service, tolerating background-start restrictions. */
        fun start(context: Context) {
            val intent = Intent(context, UpdateForegroundService::class.java)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                // Android 12+ can forbid starting a foreground service from the background
                // (e.g. right after boot without an exemption). It will start next time the
                // app is opened; nothing else we can safely do here.
                Log.e(TAG, "Could not start update service: ${e.message}")
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, UpdateForegroundService::class.java))
        }
    }
}
