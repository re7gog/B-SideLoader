package dev.re7gog.b_sideloader.data.logic.installers

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import kotlinx.coroutines.flow.FlowCollector
import okhttp3.ResponseBody
import kotlin.math.round

class SessionInstaller(private val context: Context) : ApkInstaller {
    override suspend fun installApkFromDownload(
        download: ResponseBody, progressCollector: FlowCollector<Int>
    ) {
        val packageInstaller = context.packageManager.packageInstaller
        var sessionId = -1
        try {
            val totalBytes = download.contentLength()
            if (totalBytes == 0L) throw Exception("Download size is zero")
            val sessionParams = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            )
            sessionId = packageInstaller.createSession(sessionParams)
            packageInstaller.openSession(sessionId).use { session ->
                session.openWrite(
                    "b_side_install", 0, totalBytes
                ).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead = 0
                    var totalBytesRead = 0L

                    download.byteStream().use { input ->
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            val progress = round(totalBytesRead.toFloat() / totalBytes * 100).toInt()
                            progressCollector.emit(progress)
                        }
                    }
                }
                val intent = Intent(context, InstallReceiver::class.java).apply {
                    action = "dev.re7gog.b_sideloader.INSTALL_COMPLETE"
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context, sessionId, intent,
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                @SuppressLint("RequestInstallPackagesPolicy")
                session.commit(pendingIntent.intentSender)
            }
        } catch (e: Exception) {
            if (sessionId != -1) packageInstaller.abandonSession(sessionId)
            throw e
        }
    }
}