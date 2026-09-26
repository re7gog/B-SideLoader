package dev.re7gog.b_sideloader.data.background

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.re7gog.b_sideloader.MainActivity
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.core.log.NoopLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class NotificationCenterTest {

    private val application: Application = ApplicationProvider.getApplicationContext()
    private val manager = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val center = NotificationCenter(application, NoopLogger)

    @Test
    fun createsOneChannelPerKindOfNotification() {
        center.ensureChannels()

        val importance = shadowOf(manager).notificationChannels.associate { it.id to it.importance }
        assertEquals(
            mapOf(
                NotificationCenter.CHANNEL_UPDATES_AVAILABLE to NotificationManager.IMPORTANCE_DEFAULT,
                NotificationCenter.CHANNEL_UPDATE_PROGRESS to NotificationManager.IMPORTANCE_LOW,
                // The persistent service's ongoing notification must stay out of the way.
                NotificationCenter.CHANNEL_MONITOR to NotificationManager.IMPORTANCE_MIN,
            ),
            importance,
        )
    }

    @Test
    fun theUpdatesAlertNamesTheAppsAndOpensTheAppToUpdateThem() {
        grantNotifications()

        center.showUpdatesAvailable(listOf("Alpha", "Beta"))

        val alert = checkNotNull(posted(NotificationCenter.ID_UPDATES_AVAILABLE))
        assertEquals(NotificationCenter.CHANNEL_UPDATES_AVAILABLE, alert.channelId)
        assertEquals(
            application.getString(R.string.notif_update_available_text, "Alpha, Beta"),
            alert.extras.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString(),
        )
        assertTrue(alert.flags and Notification.FLAG_AUTO_CANCEL != 0)

        val opens = shadowOf(alert.contentIntent).savedIntent
        assertEquals(ComponentName(application, MainActivity::class.java), opens.component)
        assertTrue(opens.getBooleanExtra(MainActivity.EXTRA_RUN_UPDATE_CHECK, false))
    }

    @Test
    fun nothingIsPostedWhenNothingNeedsUpdating() {
        grantNotifications()

        center.showUpdatesAvailable(emptyList())

        assertNull(posted(NotificationCenter.ID_UPDATES_AVAILABLE))
    }

    /** Android 13+ drops the notification without the runtime permission; so must we, quietly. */
    @Test
    fun withoutThePermissionTheAlertIsDroppedInsteadOfThrowing() {
        center.showUpdatesAvailable(listOf("Alpha"))

        assertNull(posted(NotificationCenter.ID_UPDATES_AVAILABLE))
    }

    /** Below Android 13 the permission is implicitly held. */
    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun beforeAndroid13NoRuntimePermissionIsNeeded() {
        center.showUpdatesAvailable(listOf("Alpha"))

        assertNotNull(posted(NotificationCenter.ID_UPDATES_AVAILABLE))
    }

    @Test
    fun aNegativePercentageMeansIndeterminateProgress() {
        val indeterminate = center.progressNotification(appName = "Alpha", percent = -1)
        val halfway = center.progressNotification(appName = "Alpha", percent = 50)

        assertTrue(indeterminate.extras.getBoolean(NotificationCompat.EXTRA_PROGRESS_INDETERMINATE))
        assertFalse(halfway.extras.getBoolean(NotificationCompat.EXTRA_PROGRESS_INDETERMINATE))
        assertEquals(50, halfway.extras.getInt(NotificationCompat.EXTRA_PROGRESS))
    }

    @Test
    fun progressWithoutAnAppNameUsesTheGenericLabel() {
        val notification = center.progressNotification(appName = null, percent = 10)

        assertEquals(
            application.getString(
                R.string.notif_progress_text,
                application.getString(R.string.notif_apps_generic),
            ),
            notification.extras.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString(),
        )
    }

    @Test
    fun cancelProgressRemovesOnlyTheProgressNotification() {
        grantNotifications()
        center.notify(NotificationCenter.ID_UPDATE_PROGRESS, center.progressNotification("Alpha", 10))
        center.showUpdatesAvailable(listOf("Alpha"))

        center.cancelProgress()

        assertNull(posted(NotificationCenter.ID_UPDATE_PROGRESS))
        assertNotNull(posted(NotificationCenter.ID_UPDATES_AVAILABLE))
    }

    @Test
    fun theMonitorNotificationIsOngoingAndSilent() {
        val notification = center.monitorNotification("Watching")

        assertEquals(NotificationCenter.CHANNEL_MONITOR, notification.channelId)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals("Watching", notification.extras.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString())
    }

    private fun grantNotifications() =
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

    private fun posted(id: Int): Notification? = shadowOf(manager).getNotification(id)
}
