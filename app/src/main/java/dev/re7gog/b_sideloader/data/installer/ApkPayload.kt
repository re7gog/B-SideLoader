package dev.re7gog.b_sideloader.data.installer

import dev.re7gog.b_sideloader.domain.error.AppError
import dev.re7gog.b_sideloader.domain.error.InstallFailure
import kotlinx.coroutines.ensureActive
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.coroutineContext

/**
 * The bytes to install, plus how many of them there are.
 *
 * [Closeable] so the underlying HTTP response or file handle is released even when the install is
 * cancelled halfway through.
 */
class ApkPayload(
    val lengthBytes: Long,
    private val stream: InputStream,
    private val onClose: () -> Unit = {},
) : Closeable {

    fun openStream(): InputStream = stream

    override fun close() {
        runCatching { stream.close() }
        runCatching { onClose() }
    }
}

/**
 * Copies an APK into an installer session, reporting progress and honouring cancellation.
 *
 * `ensureActive()` on every chunk is what makes leaving the screen mid-install actually stop the
 * transfer: `InputStream.read` is not itself cancellable, so without an explicit check a cancelled
 * coroutine would keep pulling a 150 MB file to completion in the background.
 */
suspend fun ApkPayload.copyInto(
    output: OutputStream,
    onProgress: suspend (Float) -> Unit,
) {
    if (lengthBytes <= 0L) {
        throw AppError.Install(InstallFailure.BadPayload, "Download reported a size of zero bytes")
    }
    val buffer = ByteArray(COPY_BUFFER_BYTES)
    var copied = 0L
    var lastReported = -1

    openStream().use { input ->
        while (true) {
            coroutineContext.ensureActive()
            val read = input.read(buffer)
            if (read == -1) break
            output.write(buffer, 0, read)
            copied += read

            // Emitting per 8 KiB chunk would post ~19 000 recompositions for a 150 MB APK; a
            // percentage point is as fine-grained as any progress bar can show.
            val percent = ((copied * PERCENT) / lengthBytes).toInt()
            if (percent != lastReported) {
                lastReported = percent
                onProgress(copied.toFloat() / lengthBytes)
            }
        }
    }
    output.flush()

    if (copied < lengthBytes) {
        throw AppError.Install(
            InstallFailure.BadPayload,
            "Transfer ended early: $copied of $lengthBytes bytes",
        )
    }
}

private const val COPY_BUFFER_BYTES = 64 * 1024
private const val PERCENT = 100L
