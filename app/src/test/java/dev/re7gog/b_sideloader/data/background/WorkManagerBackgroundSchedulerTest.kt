package dev.re7gog.b_sideloader.data.background

import android.app.Application
import android.content.ComponentName
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import dev.re7gog.b_sideloader.core.log.NoopLogger
import dev.re7gog.b_sideloader.domain.model.AppSettings
import dev.re7gog.b_sideloader.domain.model.BackgroundMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * What ends up scheduled for each combination of settings.
 *
 * `sync` promises to be idempotent and to describe an end state, so every case asserts on what
 * WorkManager and the service are left with — not on which calls were made.
 */
@RunWith(AndroidJUnit4::class)
class WorkManagerBackgroundSchedulerTest {

    private val application: Application = ApplicationProvider.getApplicationContext()
    private val scheduler = WorkManagerBackgroundScheduler(application, NoopLogger)
    private val workManager get() = WorkManager.getInstance(application)

    @Before
    fun initialiseWorkManager() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            application,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
    }

    @Test
    fun periodicModeSchedulesOneBatteryFriendlyUnmeteredJob() = runTest {
        scheduler.sync(AppSettings(backgroundMode = BackgroundMode.Periodic))

        val job = periodicJob()
        assertEquals(WorkInfo.State.ENQUEUED, job.state)
        assertEquals(NetworkType.UNMETERED, job.constraints.requiredNetworkType)
        assertTrue(job.constraints.requiresBatteryNotLow())
        assertEquals(6.hours.inWholeMilliseconds, job.periodicityInfo?.repeatIntervalMillis)
    }

    /** UPDATE rather than KEEP: a changed preference has to reach the job that is already queued. */
    @Test
    fun resyncingUpdatesTheQueuedJobInsteadOfAddingOne() = runTest {
        scheduler.sync(AppSettings(allowMeteredNetwork = false))
        val before = periodicJob()

        scheduler.sync(AppSettings(allowMeteredNetwork = true))

        val after = periodicJob()
        assertEquals(before.id, after.id)
        assertEquals(NetworkType.CONNECTED, after.constraints.requiredNetworkType)
    }

    /** WorkManager refuses shorter periods; the scheduler clamps rather than letting it throw. */
    @Test
    fun anIntervalBelowWorkManagersMinimumIsClamped() = runTest {
        scheduler.sync(AppSettings(checkInterval = 1.minutes))

        assertEquals(15.minutes.inWholeMilliseconds, periodicJob().periodicityInfo?.repeatIntervalMillis)
    }

    @Test
    fun persistentModeStartsTheMonitorAndDropsThePeriodicJob() = runTest {
        scheduler.sync(AppSettings(backgroundMode = BackgroundMode.Periodic))

        scheduler.sync(AppSettings(backgroundMode = BackgroundMode.Persistent))

        assertEquals(WorkInfo.State.CANCELLED, periodicJob().state)
        assertEquals(monitorService, shadowOf(application).nextStartedService?.component)
    }

    @Test
    fun switchingBackToPeriodicStopsTheMonitor() = runTest {
        scheduler.sync(AppSettings(backgroundMode = BackgroundMode.Persistent))

        scheduler.sync(AppSettings(backgroundMode = BackgroundMode.Periodic))

        assertEquals(monitorService, shadowOf(application).nextStoppedService?.component)
        assertEquals(WorkInfo.State.ENQUEUED, periodicJob().state)
    }

    /** Before `sync` existed, turning auto-update off left the job running until a reinstall. */
    @Test
    fun turningAutoUpdateOffCancelsEverything() = runTest {
        scheduler.sync(AppSettings(autoUpdate = true))

        scheduler.sync(AppSettings(autoUpdate = false))

        assertEquals(WorkInfo.State.CANCELLED, periodicJob().state)
        assertEquals(monitorService, shadowOf(application).nextStoppedService?.component)
        assertNull(shadowOf(application).nextStartedService)
    }

    private fun periodicJob(): WorkInfo =
        workManager.getWorkInfosForUniqueWork(UpdateCheckWorker.WORK_NAME).get().single()

    private val monitorService = ComponentName(application, UpdateMonitorService::class.java)
}
