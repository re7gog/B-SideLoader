package dev.re7gog.b_sideloader.data.installer.session

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which updates the standard installer takes without asking — the prediction the background sweep
 * relies on to leave the rest to the user instead of downloading them for a dialog nobody sees.
 */
class UserActionPolicyTest {

    private fun requiresUserAction(
        sdkInt: Int = ANDROID_12,
        packageName: String = "com.example",
        installed: Boolean = true,
        installerOfRecord: String? = SELF,
        updateOwner: String? = null,
        mayUpdateWithoutUserAction: Boolean = true,
        mayRequestInstalls: Boolean = true,
    ) = UserActionPolicy.isRequired(
        UpdateFacts(
            sdkInt = sdkInt,
            self = SELF,
            packageName = packageName,
            installed = installed,
            installerOfRecord = installerOfRecord,
            updateOwner = updateOwner,
            mayUpdateWithoutUserAction = mayUpdateWithoutUserAction,
            mayRequestInstalls = mayRequestInstalls,
        )
    )

    @Test
    fun `an app this app installed updates silently`() {
        assertFalse(requiresUserAction())
    }

    /** The case behind the stuck background update: installed from the Telegram client. */
    @Test
    fun `an app another app installed asks`() {
        assertTrue(requiresUserAction(installerOfRecord = "org.telegram.messenger"))
    }

    @Test
    fun `an app with no recorded installer asks`() {
        assertTrue(requiresUserAction(installerOfRecord = null))
    }

    @Test
    fun `this app updates itself silently, whoever installed it`() {
        assertFalse(requiresUserAction(packageName = SELF, installerOfRecord = "com.android.chrome"))
    }

    /** Without the permission Android 12+ ignores USER_ACTION_NOT_REQUIRED altogether. */
    @Test
    fun `without the permission everything asks`() {
        assertTrue(requiresUserAction(mayUpdateWithoutUserAction = false))
        assertTrue(requiresUserAction(packageName = SELF, mayUpdateWithoutUserAction = false))
    }

    @Test
    fun `with installs from this app turned off everything asks`() {
        assertTrue(requiresUserAction(mayRequestInstalls = false))
    }

    @Test
    fun `before Android 12 everything asks`() {
        assertTrue(requiresUserAction(sdkInt = ANDROID_12 - 1))
    }

    @Test
    fun `a first install asks`() {
        assertTrue(requiresUserAction(installed = false, installerOfRecord = null))
    }

    @Test
    fun `an update owner other than this app asks, even when this app installed it`() {
        assertTrue(requiresUserAction(updateOwner = "com.android.vending"))
        assertTrue(requiresUserAction(packageName = SELF, updateOwner = "com.android.vending"))
    }

    /** With an owner recorded, the owner is who may update silently, not the installer of record. */
    @Test
    fun `an app whose updates this app owns updates silently`() {
        assertFalse(requiresUserAction(installerOfRecord = "com.android.vending", updateOwner = SELF))
    }

    private companion object {
        const val SELF = "dev.re7gog.b_sideloader"
        const val ANDROID_12 = 31
    }
}
