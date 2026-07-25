package dev.re7gog.b_sideloader.data.installer

import dev.re7gog.b_sideloader.data.error.toAppError
import dev.re7gog.b_sideloader.domain.error.AppError
import dev.re7gog.b_sideloader.domain.error.InstallFailure
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opens a release asset as an [ApkPayload], streaming rather than buffering: the bytes go straight
 * from the socket into the installer session, so a 200 MB APK never exists in memory or in the
 * cache directory.
 */
@Singleton
class HttpApkSource @Inject constructor(
    private val client: OkHttpClient,
) {
    /**
     * The caller owns the returned payload and must close it.
     *
     * The in-flight call is cancelled when the surrounding coroutine is, which is what stops the
     * download when the user leaves the screen — closing the stream alone would not, because
     * OkHttp's read is already blocked in the socket.
     */
    suspend fun open(url: String): ApkPayload {
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
            // Without a length the installer session cannot be sized, and a chunked response from
            // a redirect target usually means we followed the wrong URL.
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
}
