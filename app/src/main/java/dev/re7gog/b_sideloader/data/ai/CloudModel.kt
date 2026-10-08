package dev.re7gog.b_sideloader.data.ai

import dev.re7gog.b_sideloader.data.di.AiHttpClient
import dev.re7gog.b_sideloader.domain.error.AppError
import dev.re7gog.b_sideloader.domain.model.AiPrompt
import dev.re7gog.b_sideloader.domain.model.AiProvider
import dev.re7gog.b_sideloader.domain.model.AiSettings
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A cloud model, behind a seam so the gateway can be tested without a network. */
interface CloudModel {
    suspend fun generate(provider: AiProvider, settings: AiSettings, apiKey: String, prompt: AiPrompt): String
}

/** [CloudProtocol] over OkHttp. */
@Singleton
class OkHttpCloudModel @Inject constructor(
    @param:AiHttpClient private val httpClient: OkHttpClient,
    private val json: Json,
) : CloudModel {

    override suspend fun generate(
        provider: AiProvider,
        settings: AiSettings,
        apiKey: String,
        prompt: AiPrompt,
    ): String {
        val request = CloudProtocol.request(provider, settings, apiKey, prompt)
        val reply = try {
            httpClient.newCall(request).awaitReply()
        } catch (e: IOException) {
            throw AppError.Network(e)
        }
        if (reply.code !in HTTP_SUCCESS) throw CloudProtocol.failure(reply.code, reply.body, json)
        return CloudProtocol.replyText(provider, reply.body, json)
    }

    private class Reply(val code: Int, val body: String)

    /**
     * Enqueues the call and suspends until its body is read — on OkHttp's thread, so the caller's
     * dispatcher never blocks on the socket. Cancelling the coroutine cancels the call.
     */
    private suspend fun Call.awaitReply(): Reply = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val reply = try {
                    response.use { Reply(it.code, it.body.string()) }
                } catch (e: IOException) {
                    continuation.resumeWithException(e)
                    return
                }
                continuation.resume(reply)
            }
        })
    }

    private companion object {
        val HTTP_SUCCESS = 200..299
    }
}
