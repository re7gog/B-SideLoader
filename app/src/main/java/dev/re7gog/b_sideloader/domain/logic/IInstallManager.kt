package dev.re7gog.b_sideloader.domain.logic

import dev.re7gog.b_sideloader.data.logic.installers.ShizukuPermission
import kotlinx.coroutines.flow.Flow
import java.io.File

interface IInstallManager {
    suspend fun downloadAndInstall(url: String, privileged: Boolean = false): Flow<Float>
    suspend fun installFromFile(file: File, lengthBytes: Long, privileged: Boolean = false): Flow<Float>
    suspend fun checkPrivilegedPermission(): ShizukuPermission
}