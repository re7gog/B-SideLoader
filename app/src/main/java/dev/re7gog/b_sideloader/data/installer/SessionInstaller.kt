package dev.re7gog.b_sideloader.data.installer

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.flow.FlowCollector
import java.io.InputStream

class SessionInstaller(private val context: Context) : ApkInstaller {
    override suspend fun installApk(
        stream: InputStream, lengthBytes: Long, progressCollector: FlowCollector<Float>
    ) {
        val packageInstaller = context.packageManager.packageInstaller
        var sessionId = -1
        try {
            if (lengthBytes == 0L) throw Exception("Download size is zero")
            val sessionParams = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                sessionParams.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            sessionId = packageInstaller.createSession(sessionParams)
            packageInstaller.openSession(sessionId).use { session ->
                session.openWrite(
                    "b_side_reg_install", 0, lengthBytes
                ).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead = 0
                    var totalBytesRead = 0L

                    stream.use { input ->
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            progressCollector.emit(totalBytesRead.toFloat() / lengthBytes)
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

    override suspend fun uninstallPackage(packageName: String) {
        val packageInstaller = context.packageManager.packageInstaller

        val intent = Intent(context, UninstallReceiver::class.java).apply {
            action = "dev.re7gog.b_sideloader.UNINSTALL_COMPLETE"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        packageInstaller.uninstall(packageName, pendingIntent.intentSender)
    }
}