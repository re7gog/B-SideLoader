package dev.re7gog.b_sideloader.data.ai

import dev.re7gog.b_sideloader.domain.error.AiFailure
import dev.re7gog.b_sideloader.domain.error.AppError
import dev.re7gog.b_sideloader.domain.model.AiPrompt
import dev.re7gog.b_sideloader.domain.model.AiProvider
import dev.re7gog.b_sideloader.domain.model.AiSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class CloudProtocolTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val prompt = AiPrompt(
        instructions = "Be a filter writer.",
        input = "<releases>...</releases>",
        jsonSchema = """{"type":"object","properties":{},"required":[],"additionalProperties":false}""",
    )

    // ---- requests ------------------------------------------------------------------------------

    @Test
    fun `OpenAI gets a bearer token and a system plus user message, nothing else`() {
        val request = request(AiProvider.OpenAi, AiSettings())

        assertEquals("https://api.openai.com/v1/chat/completions", request.url.toString())
        assertEquals("Bearer secret", request.header("Authorization"))
        val body = request.jsonBody()
        assertEquals(AiProvider.OpenAi.defaultModel, body.string("model"))
        assertEquals(setOf("model", "messages"), body.keys)
        val messages = body["messages"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("system", "user"), messages.map { it.string("role") })
        assertEquals(prompt.input, messages[1].string("content"))
    }

    @Test
    fun `an OpenAI-compatible server is reached at its own address and model`() {
        val settings = AiSettings(
            openAiBaseUrl = "http://192.168.1.5:11434/v1/",
            models = mapOf(AiProvider.OpenAi to "qwen3:8b"),
        )

        val request = request(AiProvider.OpenAi, settings)

        assertEquals("http://192.168.1.5:11434/v1/chat/completions", request.url.toString())
        assertEquals("qwen3:8b", request.jsonBody().string("model"))
    }

    @Test
    fun `a malformed server address is an error, not a crash`() {
        try {
            request(AiProvider.OpenAi, AiSettings(openAiBaseUrl = "not a url"))
            fail("Expected an AI error")
        } catch (e: AppError.Ai) {
            assertEquals(AiFailure.Service, e.reason)
        }
    }

    @Test
    fun `Anthropic gets its key header, version and the schema as structured output`() {
        val request = request(AiProvider.Anthropic, AiSettings())

        assertEquals("https://api.anthropic.com/v1/messages", request.url.toString())
        assertEquals("secret", request.header("x-api-key"))
        assertEquals("2023-06-01", request.header("anthropic-version"))
        val body = request.jsonBody()
        assertEquals(prompt.instructions, body.string("system"))
        val format = body["output_config"]!!.jsonObject["format"]!!.jsonObject
        assertEquals("json_schema", format.string("type"))
        assertEquals("object", format["schema"]!!.jsonObject.string("type"))
    }

    @Test
    fun `Gemini names the model in the path and asks for JSON`() {
        val request = request(AiProvider.Gemini, AiSettings(models = mapOf(AiProvider.Gemini to "models/gemini-x")))

        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-x:generateContent",
            request.url.toString(),
        )
        assertEquals("secret", request.header("x-goog-api-key"))
        assertNull(request.header("Authorization"))
        val config = request.jsonBody()["generationConfig"]!!.jsonObject
        assertEquals("application/json", config.string("responseMimeType"))
    }

    // ---- replies -------------------------------------------------------------------------------

    @Test
    fun `each provider's reply text is found where it puts it`() {
        assertEquals(
            "{}",
            CloudProtocol.replyText(AiProvider.OpenAi, """{"choices":[{"message":{"content":"{}"}}]}""", json),
        )
        assertEquals(
            "{}",
            CloudProtocol.replyText(
                AiProvider.Anthropic,
                """{"stop_reason":"end_turn","content":[{"type":"thinking","thinking":""},{"type":"text","text":"{}"}]}""",
                json,
            ),
        )
        assertEquals(
            "{}",
            CloudProtocol.replyText(
                AiProvider.Gemini,
                """{"candidates":[{"content":{"parts":[{"text":"hmm","thought":true},{"text":"{}"}]}}]}""",
                json,
            ),
        )
    }

    @Test
    fun `a refusal is an empty answer`() {
        assertFailure(AiFailure.EmptyResponse) {
            CloudProtocol.replyText(AiProvider.Anthropic, """{"stop_reason":"refusal","content":[]}""", json)
        }
        assertFailure(AiFailure.EmptyResponse) {
            CloudProtocol.replyText(AiProvider.Gemini, """{"promptFeedback":{"blockReason":"SAFETY"}}""", json)
        }
    }

    @Test
    fun `HTTP failures keep the provider's message`() {
        val wrongKey = CloudProtocol.failure(401, """{"error":{"message":"Incorrect API key"}}""", json)
        assertEquals(AiFailure.InvalidApiKey, wrongKey.reason)

        val geminiWrongKey = CloudProtocol.failure(400, """{"error":{"message":"API key not valid."}}""", json)
        assertEquals(AiFailure.InvalidApiKey, geminiWrongKey.reason)

        val quota = CloudProtocol.failure(429, """{"error":{"message":"You exceeded your quota"}}""", json)
        assertEquals(AiFailure.QuotaExceeded, quota.reason)
        assertEquals("You exceeded your quota", quota.detail)

        val other = CloudProtocol.failure(404, """{"error":{"message":"model not found"}}""", json)
        assertEquals(AiFailure.Service, other.reason)
        assertEquals("model not found", other.detail)

        assertEquals("HTTP 502", CloudProtocol.failure(502, "<html>Bad gateway</html>", json).detail)
    }

    private fun request(provider: AiProvider, settings: AiSettings): Request =
        CloudProtocol.request(provider, settings, apiKey = "secret", prompt = prompt)

    private fun Request.jsonBody(): JsonObject {
        val buffer = Buffer().also { body!!.writeTo(it) }
        return Json.parseToJsonElement(buffer.readUtf8()).jsonObject
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.content

    private fun assertFailure(expected: AiFailure, block: () -> Unit) {
        try {
            block()
            fail("Expected $expected")
        } catch (e: AppError.Ai) {
            assertEquals(expected, e.reason)
        }
    }
}
