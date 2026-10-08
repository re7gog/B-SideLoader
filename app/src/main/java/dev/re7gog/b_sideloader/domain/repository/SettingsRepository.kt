package dev.re7gog.b_sideloader.domain.repository

import dev.re7gog.b_sideloader.domain.model.AiMode
import dev.re7gog.b_sideloader.domain.model.AiProvider
import dev.re7gog.b_sideloader.domain.model.AppSettings
import dev.re7gog.b_sideloader.domain.model.BackgroundMode
import dev.re7gog.b_sideloader.domain.model.InstallerMode
import dev.re7gog.b_sideloader.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/** User preferences, as one immutable snapshot per emission. */
interface SettingsRepository {
    val settings: Flow<AppSettings>

    /** Current value without subscribing. Used by workers and one-shot checks. */
    suspend fun current(): AppSettings

    suspend fun setInstallerMode(mode: InstallerMode)
    suspend fun setAutoUpdate(enabled: Boolean)
    suspend fun setAllowMeteredNetwork(enabled: Boolean)
    suspend fun setUseDynamicColor(enabled: Boolean)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setParallelUpdates(enabled: Boolean)
    suspend fun setBackgroundMode(mode: BackgroundMode)

    /** Records that the apps list has shown its long-press hint. Never reset by the UI. */
    suspend fun setLongPressHintSeen(seen: Boolean)

    suspend fun setAiMode(mode: AiMode)
    suspend fun setAiProvider(provider: AiProvider)

    /** The model to ask at [provider]; blank goes back to the provider's default. */
    suspend fun setAiModel(provider: AiProvider, model: String)

    /** Base URL of an OpenAI-compatible server; blank goes back to OpenAI itself. */
    suspend fun setOpenAiBaseUrl(url: String)
}

/**
 * Secrets held in the hardware-backed keystore. Values are never logged and never leave this
 * interface in plaintext except to the caller that asked for them.
 */
interface SecretsRepository {
    /** The user's GitHub personal access token, or `null` when none is stored. */
    suspend fun getGithubToken(): String?

    /** Stores (or, for a blank value, clears) the GitHub token. */
    suspend fun setGithubToken(token: String)

    /** The user's API key for [provider], or `null` when none is stored. */
    suspend fun getAiApiKey(provider: AiProvider): String?

    /** Stores (or, for a blank value, clears) the API key for [provider]. */
    suspend fun setAiApiKey(provider: AiProvider, key: String)
}
