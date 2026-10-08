package dev.re7gog.b_sideloader.domain.ai

import dev.re7gog.b_sideloader.domain.model.AiAvailability
import dev.re7gog.b_sideloader.domain.model.AiPrompt
import kotlinx.coroutines.flow.Flow

/**
 * The one way the domain talks to a language model, whichever answers: Gemini Nano on the phone or
 * a cloud provider with the user's key. Which one is decided per call from the current settings,
 * so a change in settings applies to the next prompt with nothing to rebuild.
 *
 * Callers own the prompt and the parsing of the reply; this only moves text. Every failure is an
 * [dev.re7gog.b_sideloader.domain.error.AppError] — [dev.re7gog.b_sideloader.domain.error.AppError.Ai]
 * for the model's own, `Network` for the connection.
 */
interface LanguageModelGateway {

    /** Whether [generate] would get an answer right now, and from what. Cheap; never throws. */
    suspend fun availability(): AiAvailability

    /**
     * Gets the model ready to answer: downloads the on-device model when it is not on the phone
     * yet, emitting the bytes downloaded so far, and completes once it can answer. Completes at
     * once for a cloud model. The on-device model is only usable while the app is in the
     * foreground, so call this from a screen, never from background work.
     */
    fun prepare(): Flow<Long>

    /** The model's reply to [prompt], as text. */
    suspend fun generate(prompt: AiPrompt): String
}
