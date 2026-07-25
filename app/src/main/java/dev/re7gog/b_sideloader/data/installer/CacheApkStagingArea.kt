package dev.re7gog.b_sideloader.data.installer

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.re7gog.b_sideloader.core.coroutines.DispatcherProvider
import dev.re7gog.b_sideloader.core.coroutines.runCatchingCancellable
import dev.re7gog.b_sideloader.domain.error.AppError
import dev.re7gog.b_sideloader.domain.installer.ApkStagingArea
import dev.re7gog.b_sideloader.domain.model.LocalApk
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copies a user-picked APK out of its content URI into the cache and reads its manifest.
 *
 * Staging through a real file is required twice over: `PackageManager` can only parse an archive
 * by path, and the installer streams from disk rather than from a URI whose permission grant may
 * be revoked the moment the picker's activity result is consumed.
 */
@Singleton
class CacheApkStagingArea @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) : ApkStagingArea {

    private val stagingDir: File
        get() = File(context.cacheDir, STAGING_DIR).apply { mkdirs() }

    override suspend fun stage(uri: String): LocalApk = withContext(dispatchers.io) {
        clearBlocking()
        val target = File(stagingDir, STAGED_FILE)
        val source = context.contentResolver.openInputStream(uri.toUri())
            ?: throw AppError.Storage("The selected file could not be opened")
        source.use { input -> target.outputStream().use(input::copyTo) }

        val packageManager = context.packageManager
        val archive = packageManager.getPackageArchiveInfo(target.absolutePath, 0)
        val applicationInfo = archive?.applicationInfo
        if (archive == null || applicationInfo == null) {
            clearBlocking()
            throw AppError.Storage("The selected file is not a valid APK")
        }
        // loadLabel resolves against the archive's own resources, which are only reachable once
        // these point at the file rather than at a (not yet) installed app.
        applicationInfo.sourceDir = target.absolutePath
        applicationInfo.publicSourceDir = target.absolutePath

        val installed = installedInfo(archive.packageName)
        LocalApk(
            path = target.absolutePath,
            packageName = archive.packageName,
            label = runCatchingCancellable { applicationInfo.loadLabel(packageManager).toString() }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: archive.packageName,
            versionName = archive.versionName.orEmpty(),
            versionCode = archive.versionCodeCompat(),
            sizeBytes = target.length(),
            installedVersionName = installed?.versionName,
            installedVersionCode = installed?.versionCodeCompat(),
        )
    }

    override suspend fun clear() = withContext(dispatchers.io) { clearBlocking() }

    private fun clearBlocking() {
        stagingDir.listFiles()?.forEach { it.delete() }
    }

    private fun installedInfo(packageName: String) = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(packageName, 0)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private companion object {
        const val STAGING_DIR = "manual_install"
        const val STAGED_FILE = "picked.apk"
    }
}
