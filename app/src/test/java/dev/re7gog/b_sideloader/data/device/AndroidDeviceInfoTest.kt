package dev.re7gog.b_sideloader.data.device

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild

/**
 * The one reader of `android.os.Build`. Robolectric resets [ShadowBuild] between tests, so each
 * case sets only the fields it is about.
 */
@RunWith(AndroidJUnit4::class)
class AndroidDeviceInfoTest {

    @Test
    fun readsTheAbisTheDeviceReports() {
        ShadowBuild.setSupportedAbis(arrayOf("arm64-v8a", "armeabi-v7a"))

        assertEquals(listOf("arm64-v8a", "armeabi-v7a"), AndroidDeviceInfo().supportedAbis)
    }

    /** Vendor matching is on the lower-cased manufacturer; ROMs are not consistent about case. */
    @Test
    fun anAggressiveRomIsRecognisedWhateverItsCasing() {
        ShadowBuild.setManufacturer("Xiaomi")

        val info = AndroidDeviceInfo()

        assertEquals("xiaomi", info.manufacturer)
        assertTrue(info.hasAggressiveBackgroundLimits)
    }

    @Test
    fun stockAndroidHasNoAggressiveLimits() {
        ShadowBuild.setManufacturer("Google")

        assertFalse(AndroidDeviceInfo().hasAggressiveBackgroundLimits)
    }

    /** "Xiaomi Xiaomi 13T Pro" is what a naive concatenation produces for TDLib's device list. */
    @Test
    fun theDisplayNameDoesNotRepeatAVendorTheModelAlreadyStartsWith() {
        ShadowBuild.setManufacturer("Xiaomi")
        ShadowBuild.setModel("xiaomi 13T Pro")

        assertEquals("Xiaomi 13T Pro", AndroidDeviceInfo().displayName)
    }

    @Test
    fun theDisplayNamePrefixesTheVendorOtherwise() {
        ShadowBuild.setManufacturer("samsung")
        ShadowBuild.setModel("SM-S918B")

        assertEquals("Samsung SM-S918B", AndroidDeviceInfo().displayName)
    }

    @Test
    fun silentSelfUpdatesAreAvailableFromAndroid12() {
        assertTrue(AndroidDeviceInfo().supportsSilentSelfUpdates)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun silentSelfUpdatesAreUnavailableBeforeAndroid12() {
        val info = AndroidDeviceInfo()

        assertEquals(Build.VERSION_CODES.R, info.sdkInt)
        assertFalse(info.supportsSilentSelfUpdates)
    }
}
