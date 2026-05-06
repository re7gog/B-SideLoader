package dev.re7gog.b_sideloader

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import dev.re7gog.b_sideloader.data.background.NotificationHelper
import dev.re7gog.b_sideloader.data.background.UpdateCheckWorker
import dev.re7gog.b_sideloader.data.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class BSideApplication: Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var settingsManager: SettingsManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.init(this)
        CoroutineScope(Dispatchers.Default).launch { schedulePeriodicUpdateCheck() }
    }

    private suspend fun schedulePeriodicUpdateCheck() {
        if (!(settingsManager.useAutoupdates.firstOrNull() ?: true)) return
        val constrains = Constraints.Builder().setRequiresBatteryNotLow(true)
        if (!(settingsManager.useMobileData.firstOrNull() ?: true)) {
            constrains.setRequiredNetworkType(NetworkType.UNMETERED)
        }
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(6, TimeUnit.HOURS)
            .setConstraints(constrains.build()).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "check_updates",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}