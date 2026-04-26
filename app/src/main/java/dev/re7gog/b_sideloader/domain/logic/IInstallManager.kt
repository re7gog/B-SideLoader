package dev.re7gog.b_sideloader.domain.logic

import dev.re7gog.b_sideloader.data.installer.ShizukuPermission
import kotlinx.coroutines.flow.Flow
import java.io.File

interface IInstallManager {
    suspend fun downloadAndInstall(url: String): Flow<Float>
    suspend fun installFromFile(file: File, lengthBytes: Long): Flow<Float>
    suspend fun checkPrivilegedPermission(): ShizukuPermission
}