package dev.re7gog.b_sideloader.data.installer

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** An APK the user picked from storage, copied into cache and parsed for the confirm dialog. */
data class StagedApk(
    val file: File,
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val sizeBytes: Long,
    val icon: Drawable?,
    /** Version of the same package already on this device, or null if it isn't installed. */
    val installedVersionName: String?,
    val installedVersionCode: Long?
) {
    /** Android refuses to replace an app with an older one; the user must uninstall first. */
    val isDowngrade: Boolean
        get() = installedVersionCode != null && versionCode < installedVersionCode
}

/**
 * Copies a user-picked APK out of its content URI and reads its manifest. Staging through a
 * real file is required twice over: PackageManager can only parse an archive by path, and the
 * installers stream it from disk rather than from a URI we may lose permission to.
 */
class ApkStager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val stagingDir: File
        get() = File(context.cacheDir, STAGING_DIR_NAME).apply { mkdirs() }

    suspend fun stage(uri: Uri): StagedApk = withContext(Dispatchers.IO) {
        clear()
        val file = File(stagingDir, STAGED_FILE_NAME)
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use(input::copyTo)
        } ?: throw IllegalArgumentException("Can't read the selected file")

        val pm = context.packageManager
        val info = pm.getPackageArchiveInfo(file.absolutePath, 0)
        val appInfo = info?.applicationInfo
        if (info == null || appInfo == null) {
            clear()
            throw IllegalArgumentException("The selected file is not a valid APK")
        }
        // loadLabel/loadIcon resolve against the app's own resources, which are only reachable
        // once these point at the archive instead of the (not yet) installed app
        appInfo.sourceDir = file.absolutePath
        appInfo.publicSourceDir = file.absolutePath

        val installed = installedPackageInfo(info.packageName)
        StagedApk(
            file = file,
            packageName = info.packageName,
            label = runCatching { appInfo.loadLabel(pm).toString() }
                .getOrNull()?.takeIf { it.isNotBlank() } ?: info.packageName,
            versionName = info.versionName ?: "unknown",
            versionCode = info.versionCodeCompat(),
            sizeBytes = file.length(),
            icon = runCatching { appInfo.loadIcon(pm) }.getOrNull(),
            installedVersionName = installed?.versionName,
            installedVersionCode = installed?.versionCodeCompat()
        )
    }

    /** Drops the staged copy. Safe to call when nothing is staged. */
    fun clear() {
        stagingDir.listFiles()?.forEach { it.delete() }
    }

    private fun installedPackageInfo(packageName: String): PackageInfo? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                packageName, PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            context.packageManager.getPackageInfo(packageName, 0)
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.versionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode
        else versionCode.toLong()

    private companion object {
        const val STAGING_DIR_NAME = "manual_install"
        const val STAGED_FILE_NAME = "picked.apk"
    }
}
