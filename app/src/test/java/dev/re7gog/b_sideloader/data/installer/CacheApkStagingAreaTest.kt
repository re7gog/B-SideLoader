package dev.re7gog.b_sideloader.data.installer

import android.app.Application
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.re7gog.b_sideloader.BuildConfig
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.core.coroutines.DefaultDispatcherProvider
import dev.re7gog.b_sideloader.domain.error.AppError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Properties

/**
 * Copying a picked file out of its content URI and reading the manifest from the copy.
 *
 * The "real APK" here is the resource APK the Android Gradle plugin builds for these very tests:
 * it carries this app's merged manifest, so its package, label and version are known exactly.
 */
@RunWith(AndroidJUnit4::class)
class CacheApkStagingAreaTest {

    private val application: Application = ApplicationProvider.getApplicationContext()
    private val stagingArea = CacheApkStagingArea(application, DefaultDispatcherProvider())
    private val stagingDir = File(application.cacheDir, "manual_install")

    @Test
    fun readsTheManifestOfAPickedApk() = runTest {
        val uri = pick(testResourceApk().readBytes())

        val apk = stagingArea.stage(uri)

        assertEquals(BuildConfig.APPLICATION_ID, apk.packageName)
        assertEquals(application.getString(R.string.app_name), apk.label)
        assertEquals(BuildConfig.VERSION_NAME, apk.versionName)
        assertEquals(BuildConfig.VERSION_CODE.toLong(), apk.versionCode)
        assertEquals(testResourceApk().length(), apk.sizeBytes)
        assertTrue(File(apk.path).exists())
        // Robolectric "installs" the app under test, so the archive is an update of itself.
        assertEquals(BuildConfig.VERSION_CODE.toLong(), apk.installedVersionCode)
    }

    @Test
    fun aFileThatIsNotAnApkIsRejectedAndNotLeftBehind() = runTest {
        val uri = pick("definitely not a zip".toByteArray())

        val error = runCatching { stagingArea.stage(uri) }.exceptionOrNull()

        assertTrue(error is AppError.Storage)
        assertEquals("The selected file is not a valid APK", error?.message)
        assertTrue(stagingDir.listFiles().orEmpty().isEmpty())
    }

    /** Only one file is ever staged; a second pick replaces the first instead of accumulating. */
    @Test
    fun stagingAgainReplacesThePreviousCopy() = runTest {
        stagingArea.stage(pick(testResourceApk().readBytes(), name = "first.apk"))

        stagingArea.stage(pick(testResourceApk().readBytes(), name = "second.apk"))

        assertEquals(1, stagingDir.listFiles().orEmpty().size)
    }

    /**
     * The copy is planted rather than staged: once Robolectric's package parser has read an
     * archive it keeps the file open, and Windows refuses to delete an open file. A device does
     * not have that problem, and neither does `clear` — only the test host would.
     */
    @Test
    fun clearRemovesTheStagedCopy() = runTest {
        stagingDir.mkdirs()
        File(stagingDir, "picked.apk").writeBytes(ByteArray(16))

        stagingArea.clear()

        assertTrue(stagingDir.listFiles().orEmpty().isEmpty())
    }

    /** Registers [bytes] behind a content URI, the way a document picker hands a file over. */
    private fun pick(bytes: ByteArray, name: String = "picked.apk"): String {
        val uri = "content://com.example.documents/document/$name".toUri()
        shadowOf(application.contentResolver).registerInputStream(uri, ByteArrayInputStream(bytes))
        return uri.toString()
    }

    /**
     * `apk-for-local-test.ap_`, located through the config AGP generates for Robolectric. Read via
     * the system class loader: Robolectric's sandbox loader does not serve that resource.
     */
    private fun testResourceApk(): File {
        val config = Properties().apply {
            val stream = checkNotNull(ClassLoader.getSystemResourceAsStream(TEST_CONFIG)) {
                "$TEST_CONFIG is missing; is unitTests.isIncludeAndroidResources on?"
            }
            stream.use(::load)
        }
        return File(config.getProperty("android_resource_apk")).absoluteFile
    }

    private companion object {
        const val TEST_CONFIG = "com/android/tools/test_config.properties"
    }
}
