package dev.re7gog.b_sideloader.data.installer.session

import dev.re7gog.b_sideloader.domain.model.InstallerMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where asking up front spares the user a dialog — and so is worth doing — and where it is not. */
class PreapprovalPolicyTest {

    private fun spares(
        sdkInt: Int = ANDROID_14,
        mode: InstallerMode = InstallerMode.Session,
        installerOfRecord: String? = "com.android.vending",
        updateOwner: String? = null,
    ) = PreapprovalPolicy.sparesADialog(sdkInt, mode, installerOfRecord, updateOwner, SELF)

    @Test
    fun `an app another store installed is worth asking about`() {
        assertTrue(spares())
    }

    /** Sideloaded with no installer recorded: the commit would ask, so asking early helps. */
    @Test
    fun `an app with no recorded installer is worth asking about`() {
        assertTrue(spares(installerOfRecord = null))
    }

    /** Already updated silently — asking would *add* a dialog. */
    @Test
    fun `an app this app installed is not`() {
        assertFalse(spares(installerOfRecord = SELF))
    }

    /** Ours to install, but another store claimed its updates: the commit would ask. */
    @Test
    fun `an app whose updates another store owns is worth asking about`() {
        assertTrue(spares(installerOfRecord = SELF, updateOwner = "com.android.vending"))
    }

    @Test
    fun `an app whose updates this app owns is not`() {
        assertFalse(spares(installerOfRecord = SELF, updateOwner = SELF))
    }

    @Test
    fun `privileged installers are silent already`() {
        assertFalse(spares(mode = InstallerMode.Shizuku))
        assertFalse(spares(mode = InstallerMode.Dhizuku))
    }

    @Test
    fun `not before Android 14`() {
        assertFalse(spares(sdkInt = ANDROID_14 - 1))
    }

    private companion object {
        const val SELF = "dev.re7gog.b_sideloader"
        const val ANDROID_14 = 34
    }
}
