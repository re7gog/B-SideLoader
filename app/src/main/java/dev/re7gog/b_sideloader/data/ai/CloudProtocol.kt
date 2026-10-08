package dev.re7gog.b_sideloader.data.ai

import dev.re7gog.b_sideloader.domain.error.AiFailure
import dev.re7gog.b_sideloader.domain.error.AppError
import dev.re7gog.b_sideloader.domain.model.AiPrompt
import dev.re7gog.b_sideloader.domain.model.AiProvider
import dev.re7gog.b_sideloader.domain.model.AiSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.HttpURLConnection

/**
 * The three cloud APIs, as plain requests and replies — no SDKs. The official SDKs would add a
 * JSON stack, an HTTP stack and keep rules each to an app that needs one request shape per
 * provider; the shapes are small and stable enough to own.
 *
 * Pure: builds an OkHttp [Request] and reads a body string, so every provider is tested without a
 * server. Errors become [AppError.Ai] with the provider's own message kept as the detail.
 */
internal object CloudProtocol {

    fun request(provider: AiProvider, settings: AiSettings, apiKey: String, prompt: AiPrompt): Request {
        val model = settings.modelFor(provider)
        return when (provider) {
            AiProvider.OpenAi -> openAi(settings.resolvedOpenAiBaseUrl, model, apiKey, prompt)
            AiProvider.Anthropic -> anthropic(model, apiKey, prompt)
            AiProvider.Gemini -> gemini(model, apiKey, prompt)
        }
    }

    /** The text of a successful reply. Throws [AppError.Ai] when it holds none. */
    fun replyText(provider: AiProvider, body: String, json: Json): String {
        val root = parse(body, json)
        val text = when (provider) {
            AiProvider.OpenAi -> openAiText(root)
            AiProvider.Anthropic -> anthropicText(root)
            AiProvider.Gemini -> geminiText(root)
        }
        return text?.takeIf { it.isNotBlank() } ?: throw AppError.Ai(AiFailure.EmptyResponse)
    }

    /**
     * A non-2xx reply as a domain error. All three providers put a readable message under
     * `error.message`, which is kept: "model not found" or "billing not set up" is what the user
     * needs to read, and no status code says it.
     */
    fun failure(code: Int, body: String, json: Json): AppError.Ai {
        val message = runCatching { parse(body, json) }.getOrNull()
            ?.obj("error")?.string("message")?.trim()?.takeIf { it.isNotEmpty() }
        val reason = when {
            code == HttpURLConnection.HTTP_UNAUTHORIZED || code == HttpURLConnection.HTTP_FORBIDDEN ->
                AiFailure.InvalidApiKey

            // Gemini answers a wrong key with a plain 400.
            code == HttpURLConnection.HTTP_BAD_REQUEST && message?.contains("api key", ignoreCase = true) == true ->
                AiFailure.InvalidApiKey

            code == HTTP_PAYMENT_REQUIRED || code == HTTP_TOO_MANY_REQUESTS -> AiFailure.QuotaExceeded
            else -> AiFailure.Service
        }
        return AppError.Ai(reason, detail = message ?: "HTTP $code")
    }

    // ---- OpenAI and compatible servers: Chat Completions ----------------------------------------

    /**
     * Only `model` and `messages`: compatible servers differ on everything else, and OpenAI's own
     * reasoning models reject a `temperature` or `max_tokens`. JSON is asked for in the prompt.
     */
    private fun openAi(baseUrl: String, model: String, apiKey: String, prompt: AiPrompt): Request {
        val url = "$baseUrl/chat/completions".toHttpUrlOrNull()
            ?: throw AppError.Ai(AiFailure.Service, detail = "Invalid server address: $baseUrl")
        val body = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", prompt.instructions)
                }
                addJsonObject {
                    put("role", "user")
                    put("content", prompt.input)
                }
            }
        }
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    private fun openAiText(root: JsonObject): String? {
        val message = root.array("choices")?.firstOrNull()?.asObject()?.obj("message") ?: return null
        message.string("refusal")?.takeIf { it.isNotBlank() }?.let {
            throw AppError.Ai(AiFailure.EmptyResponse, detail = it)
        }
        return message.string("content")
    }

    // ---- Anthropic: Messages API -----------------------------------------------------------------

    private fun anthropic(model: String, apiKey: String, prompt: AiPrompt): Request {
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", ANTHROPIC_MAX_TOKENS)
            put("system", prompt.instructions)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    put("content", prompt.input)
                }
            }
            prompt.jsonSchema?.let { schema ->
                putJsonObject("output_config") {
                    putJsonObject("format") {
                        put("type", "json_schema")
                        put("schema", Json.parseToJsonElement(schema))
                    }
                }
            }
        }
        return Request.Builder()
            .url(ANTHROPIC_URL)
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    private fun anthropicText(root: JsonObject): String? {
        if (root.string("stop_reason") == "refusal") throw AppError.Ai(AiFailure.EmptyResponse)
        return root.array("content")
            ?.mapNotNull { it.asObject() }
            ?.filter { it.string("type") == "text" }
            ?.joinToString("") { it.string("text").orEmpty() }
    }

    // ---- Google: Gemini API ----------------------------------------------------------------------

    private fun gemini(model: String, apiKey: String, prompt: AiPrompt): Request {
        val url = GEMINI_BASE_URL.toHttpUrlOrNull()!!.newBuilder()
            .addPathSegment("${model.removePrefix("models/")}:generateContent")
            .build()
        val body = buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { addJsonObject { put("text", prompt.instructions) } }
            }
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") { addJsonObject { put("text", prompt.input) } }
                }
            }
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
            }
        }
        return Request.Builder()
            .url(url)
            .header("x-goog-api-key", apiKey)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    /** The answer's text parts, without the model's thoughts. */
    private fun geminiText(root: JsonObject): String? {
        val candidate = root.array("candidates")?.firstOrNull()?.asObject()
        if (candidate == null) {
            val blocked = root.obj("promptFeedback")?.string("blockReason")
            throw AppError.Ai(AiFailure.EmptyResponse, detail = blocked)
        }
        return candidate.obj("content")?.array("parts")
            ?.mapNotNull { it.asObject() }
            ?.filterNot { (it["thought"] as? JsonPrimitive)?.contentOrNull == "true" }
            ?.joinToString("") { it.string("text").orEmpty() }
    }

    // ---- JSON helpers ------------------------------------------------------------------------------

    private fun parse(body: String, json: Json): JsonObject = try {
        json.parseToJsonElement(body).jsonObject
    } catch (e: IllegalArgumentException) {
        throw AppError.Ai(AiFailure.Service, detail = "Unreadable reply", cause = e)
    }

    private fun JsonElement.asObject(): JsonObject? = this as? JsonObject
    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray
    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private val JSON_MEDIA_TYPE = "application/json".toMediaType()

    private const val ANTHROPIC_URL = "https://api.anthropic.com/v1/messages"
    private const val ANTHROPIC_VERSION = "2023-06-01"

    /** Room for the model's thinking as well as the reply; the reply itself is small. */
    private const val ANTHROPIC_MAX_TOKENS = 16_000

    private const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    private const val HTTP_PAYMENT_REQUIRED = 402
    private const val HTTP_TOO_MANY_REQUESTS = 429
}
