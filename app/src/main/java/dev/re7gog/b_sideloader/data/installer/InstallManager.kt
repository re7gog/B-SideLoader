package dev.re7gog.b_sideloader.data.installer

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.re7gog.b_sideloader.data.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import javax.inject.Inject

class InstallManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    settingsManager: SettingsManager
) {
    private val managerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val installerMode = settingsManager.installerMode.stateIn(
        managerScope, SharingStarted.Eagerly, InstallerMode.SESSION
    )

    private suspend fun installApkWithMode(
        stream: InputStream, lengthBytes: Long, collector: FlowCollector<Float>
    ) {
        val mode = installerMode.value
        if (mode.isPrivileged) {
            val shizukuInstaller = ShizukuInstaller(context, mode.useDhizuku)
            shizukuInstaller.init()
            shizukuInstaller.installApk(stream, lengthBytes, collector)
            shizukuInstaller.exit()
        } else {
            SessionInstaller(context).installApk(stream, lengthBytes, collector)
        }
    }

    fun downloadAndInstall(
        url: String
    ): Flow<Float> = flow {
        val request = Request.Builder().url(url).build()
        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("Download failed: ${response.code}")
        response.use {
            val stream = it.body.byteStream()
            val lengthBytes = it.body.contentLength()
            installApkWithMode(stream, lengthBytes, this@flow)
        }
    }.flowOn(Dispatchers.IO)

    fun installFromFile(
        file: File, lengthBytes: Long
    ): Flow<Float> = flow {
        if (!file.exists()) throw Exception("File does not exist")
        file.inputStream().use {
            installApkWithMode(it, lengthBytes, this@flow)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun checkPrivilegedPermission(useDhizuku: Boolean): ShizukuPermission {
        val shizukuInstaller = ShizukuInstaller(context, useDhizuku)
        shizukuInstaller.init()
        val res = shizukuInstaller.checkPermission()
        shizukuInstaller.exit()
        return res
    }

    suspend fun uninstallPackage(packageName: String) {
        val mode = installerMode.value
        if (mode.isPrivileged) {
            val shizukuInstaller = ShizukuInstaller(context, mode.useDhizuku)
            shizukuInstaller.init()
            shizukuInstaller.uninstallPackage(packageName)
            shizukuInstaller.exit()
        } else {
            SessionInstaller(context).uninstallPackage(packageName)
        }
    }

    fun isPackageInstalled(packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName, PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun getAppIcon(packageName: String): Drawable? {
        return try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }
}