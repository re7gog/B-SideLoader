package dev.re7gog.b_sideloader.data.ai

import dev.re7gog.b_sideloader.domain.ai.LanguageModelGateway
import dev.re7gog.b_sideloader.domain.error.AiFailure
import dev.re7gog.b_sideloader.domain.error.AppError
import dev.re7gog.b_sideloader.domain.model.AiAvailability
import dev.re7gog.b_sideloader.domain.model.AiBackend
import dev.re7gog.b_sideloader.domain.model.AiMode
import dev.re7gog.b_sideloader.domain.model.AiPrompt
import dev.re7gog.b_sideloader.domain.model.AiUnavailableReason
import dev.re7gog.b_sideloader.domain.repository.SecretsRepository
import dev.re7gog.b_sideloader.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Picks the backend for every call from the settings as they are at that moment, so turning AI
 * off, switching provider or pasting a key applies to the very next prompt.
 */
@Singleton
class LanguageModelGatewayImpl @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val secretsRepository: SecretsRepository,
    private val onDevice: OnDeviceModel,
    private val cloud: CloudModel,
) : LanguageModelGateway {

    override suspend fun availability(): AiAvailability {
        val ai = settingsRepository.current().ai
        return when (ai.mode) {
            AiMode.Off -> AiAvailability.Unavailable(AiUnavailableReason.Disabled)

            AiMode.OnDevice -> when (onDevice.status()) {
                OnDeviceStatus.Available -> AiAvailability.Available(AiBackend.OnDevice)
                OnDeviceStatus.Downloadable,
                OnDeviceStatus.Downloading,
                -> AiAvailability.Available(AiBackend.OnDevice, needsDownload = true)

                OnDeviceStatus.Unavailable -> AiAvailability.Unavailable(AiUnavailableReason.Unsupported)
            }

            AiMode.ApiKey -> if (secretsRepository.getAiApiKey(ai.provider).isNullOrBlank()) {
                AiAvailability.Unavailable(AiUnavailableReason.MissingApiKey)
            } else {
                AiAvailability.Available(AiBackend.Cloud(ai.provider))
            }
        }
    }

    override fun prepare(): Flow<Long> = flow {
        if (settingsRepository.current().ai.mode != AiMode.OnDevice) return@flow
        when (onDevice.status()) {
            OnDeviceStatus.Available -> Unit
            // Downloading too: collecting the download of a running one follows its progress.
            OnDeviceStatus.Downloadable, OnDeviceStatus.Downloading -> emitAll(onDevice.download())
            OnDeviceStatus.Unavailable -> throw AppError.Ai(AiFailure.Unsupported)
        }
    }

    override suspend fun generate(prompt: AiPrompt): String {
        val ai = settingsRepository.current().ai
        return when (ai.mode) {
            AiMode.Off -> throw AppError.Ai(AiFailure.Disabled)
            AiMode.OnDevice -> onDevice.generate(prompt)
            AiMode.ApiKey -> {
                val key = secretsRepository.getAiApiKey(ai.provider)
                    ?.takeIf { it.isNotBlank() }
                    ?: throw AppError.Ai(AiFailure.MissingApiKey)
                cloud.generate(ai.provider, ai, key, prompt)
            }
        }
    }
}
