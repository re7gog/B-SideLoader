package dev.re7gog.b_sideloader.domain.repository

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
    suspend fun setParallelUpdateChecks(enabled: Boolean)
    suspend fun setBackgroundMode(mode: BackgroundMode)

    /** Records that the apps list has shown its long-press hint. Never reset by the UI. */
    suspend fun setLongPressHintSeen(seen: Boolean)
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
}
