package dev.re7gog.b_sideloader.data.logic

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.re7gog.b_sideloader.data.logic.installers.SessionInstaller
import dev.re7gog.b_sideloader.data.logic.installers.ShizukuInstaller
import dev.re7gog.b_sideloader.domain.logic.IInstallManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

class InstallManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) : IInstallManager {
    override suspend fun downloadAndInstall(
        url: String
    ): Flow<Int> = flow {
        val request = Request.Builder().url(url).build()
        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("Download failed: ${response.code}")
        // TODO: Add install mode switch
        //response.use { SessionInstaller(context).installApkFromDownload(it.body, this@flow) }
        response.use {
            val shizukuInstaller = ShizukuInstaller(context)
            shizukuInstaller.init()
            //shizukuInstaller.checkPermission()
            shizukuInstaller.installApkFromDownload(it.body, this@flow)
            shizukuInstaller.exit()
        }
    }.flowOn(Dispatchers.IO)
}