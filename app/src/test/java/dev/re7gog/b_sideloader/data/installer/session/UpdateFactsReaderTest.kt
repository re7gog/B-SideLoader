package dev.re7gog.b_sideloader.data.installer.session

import android.Manifest
import android.app.Application
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** The facts the "will this update ask?" rule is judged on, as the device reports them. */
@RunWith(AndroidJUnit4::class)
class UpdateFactsReaderTest {

    private val application: Application = ApplicationProvider.getApplicationContext()
    private val packageManager = shadowOf(application.packageManager)
    private val reader = UpdateFactsReader(application)

    @Before
    fun allowInstalls() {
        shadowOf(application).grantPermissions(Manifest.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION)
        packageManager.setCanRequestPackageInstalls(true)
    }

    @Test
    fun anAppThisAppInstalledUpdatesSilently() {
        install("com.example", installer = application.packageName)

        val facts = reader.read("com.example")

        assertTrue(facts.installed)
        assertEquals(application.packageName, facts.installerOfRecord)
        assertTrue(facts.mayUpdateWithoutUserAction)
        assertFalse(UserActionPolicy.isRequired(facts))
    }

    @Test
    fun anAppTheTelegramClientInstalledAsks() {
        install("com.example", installer = "org.telegram.messenger")

        assertTrue(UserActionPolicy.isRequired(reader.read("com.example")))
    }

    @Test
    fun anAppThatIsNotInstalledIsReadAsSuch() {
        val facts = reader.read("com.missing")

        assertFalse(facts.installed)
        assertTrue(UserActionPolicy.isRequired(facts))
    }

    @Test
    fun withInstallsFromThisAppTurnedOffEverythingAsks() {
        install("com.example", installer = application.packageName)
        packageManager.setCanRequestPackageInstalls(false)

        assertTrue(UserActionPolicy.isRequired(reader.read("com.example")))
    }

    private fun install(packageName: String, installer: String) {
        packageManager.installPackage(PackageInfo().apply { this.packageName = packageName })
        packageManager.setInstallSourceInfo(packageName, installer, installer)
    }
}
