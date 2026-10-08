package dev.re7gog.b_sideloader.data.background

import android.app.ActivityManager
import android.app.Application
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.re7gog.b_sideloader.core.log.NoopLogger
import dev.re7gog.b_sideloader.domain.background.DeviceVendor
import dev.re7gog.b_sideloader.testing.FakeDeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * Detecting what the device allows, and opening the one screen that can change it.
 *
 * The OEM autostart screens are the fragile part: they have no API, get renamed between ROM
 * versions, and launching one that does not exist crashes. So the cases below pin down that only
 * the detected vendor's screens are tried, that a missing one is skipped, and that "could not open
 * anything" is reported instead of thrown.
 */
@RunWith(AndroidJUnit4::class)
class AndroidBackgroundRestrictionsTest {

    private val application: Application = ApplicationProvider.getApplicationContext()

    private fun restrictions(manufacturer: String = "google") =
        AndroidBackgroundRestrictions(application, FakeDeviceInfo(manufacturer = manufacturer), NoopLogger)

    @Test
    fun theVendorComesFromTheManufacturer() {
        assertEquals(DeviceVendor.Xiaomi, restrictions("POCO").vendor)
        assertEquals(DeviceVendor.Other, restrictions("google").vendor)
    }

    @Test
    fun reportsTheBatteryOptimizationExemption() {
        val power = application.getSystemService(Context.POWER_SERVICE) as PowerManager
        assertFalse(restrictions().isIgnoringBatteryOptimizations())

        shadowOf(power).setIgnoringBatteryOptimizations(application.packageName, true)

        assertTrue(restrictions().isIgnoringBatteryOptimizations())
    }

    @Test
    fun reportsTheRestrictedStandbyBucket() {
        val activityManager = application.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        shadowOf(activityManager).setBackgroundRestricted(true)

        assertTrue(restrictions().isBackgroundRestricted())
    }

    @Test
    fun reportsBlockedNotifications() {
        val notifications = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        shadowOf(notifications).setNotificationsEnabled(false)

        assertFalse(restrictions().areNotificationsEnabled())
    }

    @Test
    fun anAutostartScreenThatDoesNotExistIsNotOffered() {
        val restrictions = restrictions("xiaomi")

        assertFalse(restrictions.hasAutoStartSettings())
        assertFalse(restrictions.openAutoStartSettings())
        assertNull(shadowOf(application).nextStartedActivity)
    }

    @Test
    fun opensTheVendorsAutostartScreenWhenItExists() {
        installActivity(XIAOMI_AUTOSTART)
        val restrictions = restrictions("xiaomi")

        assertTrue(restrictions.hasAutoStartSettings())
        assertTrue(restrictions.openAutoStartSettings())

        val started = checkNotNull(shadowOf(application).nextStartedActivity)
        assertEquals(XIAOMI_AUTOSTART, started.component)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    /** Huawei renamed the screen across EMUI versions; the first one present on the device wins. */
    @Test
    fun fallsBackToALaterCandidateWhenTheFirstWasRenamedAway() {
        val protectedApps = ComponentName(
            "com.huawei.systemmanager",
            "com.huawei.systemmanager.optimize.process.ProtectActivity",
        )
        installActivity(protectedApps)

        assertTrue(restrictions("huawei").openAutoStartSettings())

        assertEquals(protectedApps, shadowOf(application).nextStartedActivity?.component)
    }

    /** A Xiaomi package on a Pixel is not a reason to send a Pixel user into it. */
    @Test
    fun onlyTheDetectedVendorsScreensAreTried() {
        installActivity(XIAOMI_AUTOSTART)

        assertFalse(restrictions("google").hasAutoStartSettings())
    }

    @Test
    fun asksForTheBatteryExemptionForThisPackage() {
        assertTrue(restrictions().requestIgnoreBatteryOptimizations())

        val started = checkNotNull(shadowOf(application).nextStartedActivity)
        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, started.action)
        assertEquals("package:${application.packageName}".toUri(), started.data)
    }

    @Test
    fun doesNotAskForAnExemptionItAlreadyHas() {
        val power = application.getSystemService(Context.POWER_SERVICE) as PowerManager
        shadowOf(power).setIgnoringBatteryOptimizations(application.packageName, true)

        assertTrue(restrictions().requestIgnoreBatteryOptimizations())

        assertNull(shadowOf(application).nextStartedActivity)
    }

    @Test
    fun opensThisAppsNotificationSettings() {
        assertTrue(restrictions().openNotificationSettings())

        val started = checkNotNull(shadowOf(application).nextStartedActivity)
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, started.action)
        assertEquals(application.packageName, started.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }

    /** A ROM without the settings screen must yield "could not open", not an ActivityNotFound crash. */
    @Test
    fun aScreenThatCannotBeLaunchedIsReportedAsNotOpened() {
        shadowOf(application).checkActivities(true)

        assertFalse(restrictions().openAppSettings())
    }

    private fun installActivity(component: ComponentName) {
        shadowOf(application.packageManager).addActivityIfNotPresent(component)
    }

    private companion object {
        val XIAOMI_AUTOSTART = ComponentName(
            "com.miui.securitycenter",
            "com.miui.permcenter.autostart.AutoStartManagementActivity",
        )
    }
}
