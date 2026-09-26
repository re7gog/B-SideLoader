package dev.re7gog.b_sideloader.data.installer

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import dev.re7gog.b_sideloader.domain.installer.InstalledPackage
import dev.re7gog.b_sideloader.domain.installer.PackageChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class AndroidPackageInspectorTest {

    private val application: Application = ApplicationProvider.getApplicationContext()
    private val packageManager = shadowOf(application.packageManager)

    private fun inspector(scope: CoroutineScope) = AndroidPackageInspector(application, scope)

    @Test
    fun anUnknownOrBlankPackageIsNotInstalled() = runTest {
        val inspector = inspector(backgroundScope)

        assertFalse(inspector.isInstalled("com.missing"))
        assertFalse(inspector.isInstalled(""))
        assertNull(inspector.installedVersion("com.missing"))
    }

    @Test
    fun reportsTheInstalledVersionIncludingALongVersionCode() = runTest {
        packageManager.installPackage(
            PackageInfo().apply {
                packageName = "com.example"
                versionName = "2.0"
                longVersionCode = 5_000_000_000L
            }
        )
        val inspector = inspector(backgroundScope)

        assertTrue(inspector.isInstalled("com.example"))
        assertEquals(
            InstalledPackage(packageName = "com.example", versionName = "2.0", versionCode = 5_000_000_000L),
            inspector.installedVersion("com.example"),
        )
    }

    @Test
    fun anAppWithoutALauncherActivityCannotBeLaunched() = runTest {
        packageManager.installPackage(PackageInfo().apply { packageName = "com.example" })

        assertFalse(inspector(backgroundScope).launch("com.example"))
        assertNull(shadowOf(application).nextStartedActivity)
    }

    @Test
    fun launchingStartsTheAppsLauncherActivityInANewTask() = runTest {
        val launcher = ComponentName("com.example", "com.example.MainActivity")
        packageManager.addActivityIfNotPresent(launcher)
        packageManager.addIntentFilterForActivity(
            launcher,
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
        )

        assertTrue(inspector(backgroundScope).launch("com.example"))

        val started = checkNotNull(shadowOf(application).nextStartedActivity)
        assertEquals(launcher, started.component)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun packageBroadcastsBecomeChanges() = runTest {
        val inspector = inspector(backgroundScope)

        inspector.packageChanges.test {
            runCurrent() // lets the shared flow start and register its receiver

            broadcast(Intent.ACTION_PACKAGE_ADDED, "com.added")
            assertEquals(PackageChange.Installed("com.added"), awaitItem())

            broadcast(Intent.ACTION_PACKAGE_REPLACED, "com.updated")
            assertEquals(PackageChange.Installed("com.updated"), awaitItem())

            broadcast(Intent.ACTION_PACKAGE_FULLY_REMOVED, "com.removed")
            assertEquals(PackageChange.Removed("com.removed"), awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    /** "A backgrounded app holds no receiver at all": gone a few seconds after the last collector. */
    @Test
    fun theReceiverIsUnregisteredOnceNobodyIsListening() = runTest {
        val inspector = inspector(backgroundScope)

        inspector.packageChanges.test {
            runCurrent()
            assertEquals(1, packageReceivers())
            cancelAndIgnoreRemainingEvents()
        }
        advanceTimeBy(STOP_TIMEOUT_MS + 1)

        assertEquals(0, packageReceivers())
    }

    private fun TestScope.broadcast(action: String, packageName: String) {
        application.sendBroadcast(Intent(action, Uri.parse("package:$packageName")))
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()
    }

    private fun packageReceivers(): Int = shadowOf(application).registeredReceivers.count {
        it.intentFilter.hasAction(Intent.ACTION_PACKAGE_ADDED)
    }

    private companion object {
        /** `SharingStarted.WhileSubscribed` timeout in [AndroidPackageInspector]. */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
