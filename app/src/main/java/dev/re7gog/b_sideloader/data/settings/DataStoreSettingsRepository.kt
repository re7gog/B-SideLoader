package dev.re7gog.b_sideloader.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.re7gog.b_sideloader.core.log.Logger
import dev.re7gog.b_sideloader.domain.model.AppSettings
import dev.re7gog.b_sideloader.domain.model.BackgroundMode
import dev.re7gog.b_sideloader.domain.model.InstallerMode
import dev.re7gog.b_sideloader.domain.model.ThemeMode
import dev.re7gog.b_sideloader.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-backed [SettingsRepository].
 *
 * Emits one whole [AppSettings] rather than a flow per key. Scheduling decisions depend on several
 * settings at once (auto-update + mode + metered), and reading them from independent flows made it
 * possible to reconcile against a half-updated view.
 */
@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val logger: Logger,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = context.appPreferences.data
        .catch { throwable ->
            // A corrupt preferences file must not take the app down; defaults are a valid state.
            if (throwable !is IOException) throw throwable
            logger.w(TAG, throwable) { "Could not read settings; falling back to defaults" }
            emit(emptyPreferences())
        }
        .map { it.toSettings() }
        .distinctUntilChanged()

    override suspend fun current(): AppSettings = settings.first()

    override suspend fun setInstallerMode(mode: InstallerMode) = edit {
        it[Keys.INSTALLER_MODE] = mode.name
    }

    override suspend fun setAutoUpdate(enabled: Boolean) = edit {
        it[Keys.AUTO_UPDATE] = enabled
    }

    override suspend fun setAllowMeteredNetwork(enabled: Boolean) = edit {
        it[Keys.ALLOW_METERED] = enabled
    }

    override suspend fun setUseDynamicColor(enabled: Boolean) = edit {
        it[Keys.DYNAMIC_COLOR] = enabled
    }

    override suspend fun setThemeMode(mode: ThemeMode) = edit {
        it[Keys.THEME_MODE] = mode.name
    }

    override suspend fun setParallelUpdateChecks(enabled: Boolean) = edit {
        it[Keys.PARALLEL_CHECKS] = enabled
    }

    override suspend fun setBackgroundMode(mode: BackgroundMode) = edit {
        it[Keys.BACKGROUND_MODE] = mode.name
    }

    override suspend fun setLongPressHintSeen(seen: Boolean) = edit {
        it[Keys.LONG_PRESS_HINT_SEEN] = seen
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.appPreferences.edit(block)
    }

    private fun Preferences.toSettings() = AppSettings(
        installerMode = InstallerMode.fromStoredName(this[Keys.INSTALLER_MODE]),
        autoUpdate = this[Keys.AUTO_UPDATE] ?: AppSettings().autoUpdate,
        allowMeteredNetwork = this[Keys.ALLOW_METERED] ?: AppSettings().allowMeteredNetwork,
        useDynamicColor = this[Keys.DYNAMIC_COLOR] ?: AppSettings().useDynamicColor,
        themeMode = ThemeMode.fromStoredName(this[Keys.THEME_MODE]),
        parallelUpdateChecks = this[Keys.PARALLEL_CHECKS] ?: AppSettings().parallelUpdateChecks,
        backgroundMode = resolveBackgroundMode(this),
        longPressHintSeen = this[Keys.LONG_PRESS_HINT_SEEN] ?: AppSettings().longPressHintSeen,
    )

    /**
     * Reads the current key, falling back to the boolean flag an earlier version wrote. Without
     * this, everyone who had opted into the persistent service would silently drop back to the
     * periodic job on upgrade.
     */
    private fun resolveBackgroundMode(preferences: Preferences): BackgroundMode {
        preferences[Keys.BACKGROUND_MODE]?.let { return BackgroundMode.fromStoredName(it) }
        return when (preferences[Keys.LEGACY_FOREGROUND_SERVICE]) {
            true -> BackgroundMode.Persistent
            else -> BackgroundMode.Default
        }
    }

    private object Keys {
        val INSTALLER_MODE = stringPreferencesKey("installer_mode")
        val AUTO_UPDATE = booleanPreferencesKey("use_autoupdates")
        val ALLOW_METERED = booleanPreferencesKey("use_mobile_data")
        val DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PARALLEL_CHECKS = booleanPreferencesKey("parallel_update_checks")
        val BACKGROUND_MODE = stringPreferencesKey("background_mode")
        val LONG_PRESS_HINT_SEEN = booleanPreferencesKey("long_press_hint_seen")

        /** Written by versions before [BACKGROUND_MODE] existed. */
        val LEGACY_FOREGROUND_SERVICE = booleanPreferencesKey("use_foreground_service")
    }

    private companion object {
        const val TAG = "Settings"
    }
}
