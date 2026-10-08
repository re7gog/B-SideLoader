package dev.re7gog.b_sideloader.data.ai

import dev.re7gog.b_sideloader.domain.error.AiFailure
import dev.re7gog.b_sideloader.domain.error.AppError
import dev.re7gog.b_sideloader.domain.model.AiAvailability
import dev.re7gog.b_sideloader.domain.model.AiBackend
import dev.re7gog.b_sideloader.domain.model.AiMode
import dev.re7gog.b_sideloader.domain.model.AiPrompt
import dev.re7gog.b_sideloader.domain.model.AiProvider
import dev.re7gog.b_sideloader.domain.model.AiSettings
import dev.re7gog.b_sideloader.domain.model.AiUnavailableReason
import dev.re7gog.b_sideloader.testing.FakeSecretsRepository
import dev.re7gog.b_sideloader.testing.FakeSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/** Which backend answers is decided per call, from the settings at that moment. */
class LanguageModelGatewayImplTest {

    private val settings = FakeSettingsRepository()
    private val secrets = FakeSecretsRepository()
    private val onDevice = FakeOnDeviceModel()
    private val cloud = FakeCloudModel()
    private val gateway = LanguageModelGatewayImpl(settings, secrets, onDevice, cloud)
    private val prompt = AiPrompt(instructions = "i", input = "x")

    @Test
    fun `off is unavailable and refuses to generate`() = runTest {
        settings.setAiMode(AiMode.Off)

        assertEquals(AiAvailability.Unavailable(AiUnavailableReason.Disabled), gateway.availability())
        assertFailure(AiFailure.Disabled) { gateway.generate(prompt) }
    }

    @Test
    fun `on-device follows the model's status`() = runTest {
        settings.setAiMode(AiMode.OnDevice)

        onDevice.status = OnDeviceStatus.Unavailable
        assertEquals(AiAvailability.Unavailable(AiUnavailableReason.Unsupported), gateway.availability())

        onDevice.status = OnDeviceStatus.Downloadable
        assertEquals(AiAvailability.Available(AiBackend.OnDevice, needsDownload = true), gateway.availability())
        assertEquals(listOf(10L, 20L), gateway.prepare().toList())

        onDevice.status = OnDeviceStatus.Available
        assertEquals(AiAvailability.Available(AiBackend.OnDevice), gateway.availability())
        assertEquals("nano", gateway.generate(prompt))
    }

    @Test
    fun `a cloud provider needs its own key`() = runTest {
        settings.setAiMode(AiMode.ApiKey)
        settings.setAiProvider(AiProvider.Anthropic)
        secrets.setAiApiKey(AiProvider.OpenAi, "someone else's")

        assertEquals(AiAvailability.Unavailable(AiUnavailableReason.MissingApiKey), gateway.availability())
        assertFailure(AiFailure.MissingApiKey) { gateway.generate(prompt) }

        secrets.setAiApiKey(AiProvider.Anthropic, "sk-ant")
        assertEquals(AiAvailability.Available(AiBackend.Cloud(AiProvider.Anthropic)), gateway.availability())
        assertEquals("cloud", gateway.generate(prompt))
        assertEquals(AiProvider.Anthropic to "sk-ant", cloud.calls.single())
    }

    @Test
    fun `preparing a cloud model is a no-op`() = runTest {
        settings.setAiMode(AiMode.ApiKey)

        assertEquals(emptyList<Long>(), gateway.prepare().toList())
    }

    private suspend fun assertFailure(expected: AiFailure, block: suspend () -> Unit) {
        try {
            block()
            fail("Expected $expected")
        } catch (e: AppError.Ai) {
            assertEquals(expected, e.reason)
        }
    }

    private class FakeOnDeviceModel : OnDeviceModel {
        var status = OnDeviceStatus.Available
        override suspend fun status(): OnDeviceStatus = status
        override fun download(): Flow<Long> = flowOf(10L, 20L)
        override suspend fun generate(prompt: AiPrompt): String = "nano"
    }

    private class FakeCloudModel : CloudModel {
        val calls = mutableListOf<Pair<AiProvider, String>>()
        override suspend fun generate(provider: AiProvider, settings: AiSettings, apiKey: String, prompt: AiPrompt): String {
            calls += provider to apiKey
            return "cloud"
        }
    }
}
