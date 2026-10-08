package dev.re7gog.b_sideloader.data.ai

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import dev.re7gog.b_sideloader.core.coroutines.suspendRunCatching
import dev.re7gog.b_sideloader.core.log.Logger
import dev.re7gog.b_sideloader.domain.error.AiFailure
import dev.re7gog.b_sideloader.domain.error.AppError
import dev.re7gog.b_sideloader.domain.model.AiPrompt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.transformWhile
import javax.inject.Inject
import javax.inject.Singleton

/** Where the on-device model stands on this phone. */
enum class OnDeviceStatus { Available, Downloadable, Downloading, Unavailable }

/** The on-device model, behind a seam so the gateway can be tested without AICore. */
interface OnDeviceModel {
    /** Never throws: a phone without AICore is simply [OnDeviceStatus.Unavailable]. */
    suspend fun status(): OnDeviceStatus

    /** Downloads the model, emitting bytes so far; completes when it is ready. */
    fun download(): Flow<Long>

    suspend fun generate(prompt: AiPrompt): String
}

/**
 * Gemini Nano through ML Kit's Prompt API and AICore.
 *
 * One client for the process: creating it is cheap, but every new one loads the model into memory
 * again on first use. AICore refuses inference while the app is in the background
 * (`BACKGROUND_USE_BLOCKED`), which is why only screens call this.
 */
@Singleton
class GeminiNanoModel @Inject constructor(
    private val logger: Logger,
) : OnDeviceModel {

    private val client: GenerativeModel by lazy { Generation.getClient() }

    override suspend fun status(): OnDeviceStatus =
        suspendRunCatching { client.checkStatus() }
            .onFailure { logger.w(TAG, it) { "Gemini Nano status unavailable" } }
            .map { status ->
                when (status) {
                    FeatureStatus.AVAILABLE -> OnDeviceStatus.Available
                    FeatureStatus.DOWNLOADABLE -> OnDeviceStatus.Downloadable
                    FeatureStatus.DOWNLOADING -> OnDeviceStatus.Downloading
                    else -> OnDeviceStatus.Unavailable
                }
            }
            .getOrDefault(OnDeviceStatus.Unavailable)

    override fun download(): Flow<Long> = client.download()
        .transformWhile { status ->
            when (status) {
                is DownloadStatus.DownloadStarted -> emit(0L)
                is DownloadStatus.DownloadProgress -> emit(status.totalBytesDownloaded)
                is DownloadStatus.DownloadFailed -> throw status.e.toAppError()
                else -> Unit
            }
            status !is DownloadStatus.DownloadCompleted
        }
        .catch { throw if (it is GenAiException) it.toAppError() else it }

    override suspend fun generate(prompt: AiPrompt): String {
        // One text part rather than a SystemInstruction: those need Gemini Nano v3, and the
        // instructions read just as well ahead of the input.
        val request = generateContentRequest(TextPart(prompt.instructions + "\n\n" + prompt.input)) {
            temperature = TEMPERATURE
            topK = TOP_K
            maxOutputTokens = MAX_OUTPUT_TOKENS
        }
        val response = try {
            client.generateContent(request)
        } catch (e: GenAiException) {
            throw e.toAppError()
        }
        return response.candidates.firstOrNull()?.text?.takeIf { it.isNotBlank() }
            ?: throw AppError.Ai(AiFailure.EmptyResponse)
    }

    private fun GenAiException.toAppError(): AppError.Ai {
        val reason = when (errorCode) {
            GenAiException.ErrorCode.NOT_AVAILABLE,
            GenAiException.ErrorCode.NOT_SUPPORTED,
            GenAiException.ErrorCode.AICORE_INCOMPATIBLE,
            GenAiException.ErrorCode.NEEDS_SYSTEM_UPDATE,
            -> AiFailure.Unsupported

            GenAiException.ErrorCode.PER_APP_BATTERY_USE_QUOTA_EXCEEDED -> AiFailure.QuotaExceeded

            GenAiException.ErrorCode.BUSY,
            GenAiException.ErrorCode.BACKGROUND_USE_BLOCKED,
            GenAiException.ErrorCode.NOT_ENOUGH_DISK_SPACE,
            -> AiFailure.ModelUnavailable

            else -> AiFailure.Service
        }
        return AppError.Ai(reason, detail = message?.takeIf { it.isNotBlank() }, cause = this)
    }

    private companion object {
        const val TAG = "GeminiNano"

        /** Low: the task has one right answer, and Nano drifts from the JSON format when warm. */
        const val TEMPERATURE = 0.2f
        const val TOP_K = 10

        /** The reply is a small JSON object; this leaves room for a long regex and the reason. */
        const val MAX_OUTPUT_TOKENS = 400
    }
}
