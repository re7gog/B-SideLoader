package dev.re7gog.b_sideloader.data.installer

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.re7gog.b_sideloader.data.error.toAppError
import dev.re7gog.b_sideloader.domain.error.AppError
import dev.re7gog.b_sideloader.domain.error.InstallFailure
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.days

/**
 * Downloads release assets into the cache, ready to be installed.
 *
 * The bytes used to go straight from the socket into the installer session, which made download
 * and install one inseparable step — and since installs must run one at a time, so did downloads.
 * Landing them in a file first is what lets several download at once while their installs queue.
 * It costs disk space for as long as an APK waits for the installer, and no more:
 * [dev.re7gog.b_sideloader.domain.installer.InstallerGateway.discard] deletes it afterwards.
 */
@Singleton
class HttpApkSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val client: OkHttpClient,
) {
    private val downloadDir: File
        get() = File(context.cacheDir, DOWNLOAD_DIR).apply { mkdirs() }

    /**
     * Downloads [url] to a new file in the cache and returns it; the caller owns it from then on.
     * Nothing is left behind when this fails or is cancelled.
     */
    suspend fun download(url: String, onProgress: suspend (Float) -> Unit): File {
        pruneAbandoned()
        val target = File(downloadDir, "${UUID.randomUUID()}.apk")
        try {
            open(url).use { payload ->
                target.outputStream().use { output -> payload.copyInto(output, onProgress) }
            }
            return target
        } catch (e: Throwable) {
            target.delete()
            throw e
        }
    }

    /** Whether [file] is one of this class's downloads, which only it may delete. */
    fun owns(file: File): Boolean = file.parentFile?.canonicalFile == downloadDir.canonicalFile

    /**
     * The in-flight call is cancelled when the surrounding coroutine is, which is what stops the
     * download when it is abandoned — closing the stream alone would not, because OkHttp's read is
     * already blocked in the socket.
     */
    private suspend fun open(url: String): ApkPayload {
        val call = client.newCall(Request.Builder().url(url).build())
        currentCoroutineContext().job.invokeOnCompletion { call.cancel() }

        val response = try {
            call.execute()
        } catch (e: Throwable) {
            throw e.toAppError()
        }

        if (!response.isSuccessful) {
            response.close()
            throw AppError.Http(response.code, response.message)
        }

        val body = response.body
        val length = body.contentLength()
        if (length <= 0L) {
            response.close()
            // Without a length progress cannot be measured nor a truncated transfer caught, and a
            // chunked response from a redirect target usually means we followed the wrong URL.
            throw AppError.Install(
                InstallFailure.BadPayload,
                "Server did not report a download size",
            )
        }
        return ApkPayload(
            lengthBytes = length,
            stream = body.byteStream(),
            onClose = { response.close() },
        )
    }

    /**
     * Drops downloads a dead process left behind. Only old ones: a recent file may be another
     * download in progress, or an APK still waiting for the installer.
     */
    private fun pruneAbandoned() {
        val cutoff = System.currentTimeMillis() - ABANDONED_AFTER.inWholeMilliseconds
        downloadDir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    }

    private companion object {
        const val DOWNLOAD_DIR = "downloads"
        val ABANDONED_AFTER = 1.days
    }
}
