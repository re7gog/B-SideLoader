package dev.re7gog.b_sideloader.domain.model

/** Which language model, if any, the AI-assisted features use. */
enum class AiMode {
    /** No model at all. Everything that works without one (an example-based filter) still does. */
    Off,

    /** Gemini Nano through AICore: nothing leaves the phone, but only some phones have it. */
    OnDevice,

    /** A cloud model, paid for with the user's own API key. Sends release and file names out. */
    ApiKey,
    ;

    companion object {
        val Default = OnDevice

        fun fromStoredName(name: String?): AiMode = entries.firstOrNull { it.name == name } ?: Default
    }
}

/**
 * Cloud providers for [AiMode.ApiKey], in the order the settings list them. The stored names are
 * the enum names, so reordering is safe and renaming is not.
 */
enum class AiProvider(
    /**
     * Used when the user leaves the model field blank: each provider's newest low-cost tier, which
     * is plenty for a small JSON reply and keeps a user's own key cheap.
     */
    val defaultModel: String,
) {
    /** OpenAI, or any server that speaks its Chat Completions API (OpenRouter, Groq, Ollama, ...). */
    OpenAi("gpt-5.6-luna"),
    Anthropic("claude-haiku-5-5"),
    Gemini("gemini-3.5-flash-lite"),
    ;

    companion object {
        val Default = OpenAi

        fun fromStoredName(name: String?): AiProvider = entries.firstOrNull { it.name == name } ?: Default
    }
}

/** The AI part of [AppSettings]. API keys are secrets and live in the keystore, not here. */
data class AiSettings(
    val mode: AiMode = AiMode.Default,
    val provider: AiProvider = AiProvider.Default,
    /** The model typed for each provider. Missing or blank means [AiProvider.defaultModel]. */
    val models: Map<AiProvider, String> = emptyMap(),
    /** Where [AiProvider.OpenAi] requests go. Blank means OpenAI itself. */
    val openAiBaseUrl: String = "",
) {
    fun modelFor(provider: AiProvider): String =
        models[provider]?.trim()?.takeIf { it.isNotEmpty() } ?: provider.defaultModel

    val resolvedOpenAiBaseUrl: String
        get() = openAiBaseUrl.trim().trimEnd('/').ifEmpty { DEFAULT_OPENAI_BASE_URL }

    companion object {
        const val DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/v1"
    }
}

/** What actually answers a prompt. */
sealed interface AiBackend {
    data object OnDevice : AiBackend
    data class Cloud(val provider: AiProvider) : AiBackend
}

/** Whether a prompt can be sent right now, as the current settings and device decide. */
sealed interface AiAvailability {

    /**
     * A model will answer. [needsDownload] means the on-device model is not on the phone yet;
     * `LanguageModelGateway.prepare` fetches it first.
     */
    data class Available(val backend: AiBackend, val needsDownload: Boolean = false) : AiAvailability

    /** Why no model will answer: [AiUnavailableReason] is what the UI explains. */
    data class Unavailable(val reason: AiUnavailableReason) : AiAvailability

    val isAvailable: Boolean get() = this is Available
}

enum class AiUnavailableReason {
    /** The user turned AI off. */
    Disabled,

    /** On-device was chosen, but this phone has no Gemini Nano (or AICore has not set it up yet). */
    Unsupported,

    /** A cloud provider was chosen without an API key for it. */
    MissingApiKey,
}

/** One request to a language model. */
data class AiPrompt(
    /** What the model is and how it must answer. A system prompt where the backend has one. */
    val instructions: String,
    /** The data and the question. */
    val input: String,
    /**
     * A JSON Schema the reply must follow, as JSON text, for backends that can enforce one. Every
     * backend is also told in [instructions] to reply with JSON, since not all of them can.
     */
    val jsonSchema: String? = null,
)
