package dev.re7gog.b_sideloader

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.re7gog.b_sideloader.core.coroutines.suspendRunCatching
import dev.re7gog.b_sideloader.core.log.Logger
import dev.re7gog.b_sideloader.data.background.NotificationCenter
import dev.re7gog.b_sideloader.data.di.ApplicationScope
import dev.re7gog.b_sideloader.data.telegram.TdlibClient
import dev.re7gog.b_sideloader.domain.usecase.SyncBackgroundWorkUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class BSideApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var notificationCenter: NotificationCenter

    @Inject
    lateinit var syncBackgroundWork: SyncBackgroundWorkUseCase

    @Inject
    lateinit var tdlibClient: TdlibClient

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var logger: Logger

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        notificationCenter.ensureChannels()

        applicationScope.launch {
            // TDLib needs its client alive before anything asks for an auth state, and the
            // scheduled work has to be reconciled with whatever the settings now say.
            suspendRunCatching { tdlibClient.start() }
                .onFailure { logger.e(TAG) { "TDLib failed to start: ${it.message}" } }
            suspendRunCatching { syncBackgroundWork() }
                .onFailure { logger.e(TAG) { "Background work sync failed: ${it.message}" } }
        }
    }

    private companion object {
        const val TAG = "Application"
    }
}
