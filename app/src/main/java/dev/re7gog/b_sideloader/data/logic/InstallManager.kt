package dev.re7gog.b_sideloader.data.logic

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.re7gog.b_sideloader.data.logic.installers.SessionInstaller
import dev.re7gog.b_sideloader.data.logic.installers.ShizukuInstaller
import dev.re7gog.b_sideloader.data.logic.installers.ShizukuPermission
import dev.re7gog.b_sideloader.domain.logic.IInstallManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject

class InstallManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    settingsManager: SettingsManager
) : IInstallManager {
    private val managerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val useShizuku = settingsManager.useShizuku.stateIn(
        managerScope, SharingStarted.Eagerly, false
    )

    override suspend fun downloadAndInstall(
        url: String
    ): Flow<Float> = flow {
        val request = Request.Builder().url(url).build()
        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("Download failed: ${response.code}")
        response.use {
            val stream = it.body.byteStream()
            val lengthBytes = it.body.contentLength()

            if (useShizuku.value) {
                val shizukuInstaller = ShizukuInstaller(context)
                shizukuInstaller.init()
                shizukuInstaller.installApk(stream, lengthBytes, this@flow)
                shizukuInstaller.exit()
            } else {
                SessionInstaller(context).installApk(stream, lengthBytes, this@flow)
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun installFromFile(
        file: File, lengthBytes: Long
    ): Flow<Float> = flow {
        if (!file.exists()) throw Exception("File does not exist")
        file.inputStream().use {
            if (useShizuku.value) {
                val shizukuInstaller = ShizukuInstaller(context)
                shizukuInstaller.init()
                shizukuInstaller.installApk(it, lengthBytes, this@flow)
                shizukuInstaller.exit()
            } else {
                SessionInstaller(context).installApk(it, lengthBytes, this@flow)
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun checkPrivilegedPermission(): ShizukuPermission {
        val shizukuInstaller = ShizukuInstaller(context)
        shizukuInstaller.init()
        val res = shizukuInstaller.checkPermission()
        shizukuInstaller.exit()
        return res
    }
}