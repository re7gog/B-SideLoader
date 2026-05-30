package dev.re7gog.b_sideloader.domain.logic

import android.graphics.drawable.Drawable
import dev.re7gog.b_sideloader.data.installer.ShizukuPermission
import kotlinx.coroutines.flow.Flow
import java.io.File

interface IInstallManager {
    suspend fun downloadAndInstall(url: String): Flow<Float>
    suspend fun installFromFile(file: File, lengthBytes: Long): Flow<Float>
    suspend fun checkPrivilegedPermission(): ShizukuPermission
    suspend fun uninstallPackage(packageName: String)
    fun isPackageInstalled(packageName: String): Boolean
    fun getAppIcon(packageName: String): Drawable?
}