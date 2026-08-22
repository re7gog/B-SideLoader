package dev.re7gog.b_sideloader.data.settings

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.re7gog.b_sideloader.core.coroutines.suspendRunCatching
import dev.re7gog.b_sideloader.core.log.Logger
import dev.re7gog.b_sideloader.domain.model.AppVersion
import dev.re7gog.b_sideloader.domain.model.PendingSelfUpdate
import dev.re7gog.b_sideloader.domain.repository.PendingSelfUpdateRepository
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-backed [PendingSelfUpdateRepository], sharing [appPreferences] with the settings.
 *
 * All four keys are written in one `edit`, so the record a restarted process reads is either whole
 * or absent — a half-written one would be worse than none, because [get] would have to guess which
 * version the missing half referred to.
 *
 * Failures are logged and swallowed: this record only exists to make a self-update land in the
 * database, and it must never be the reason an install fails or the app cannot start.
 */
@Singleton
class DataStorePendingSelfUpdateRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val logger: Logger,
) : PendingSelfUpdateRepository {

    override suspend fun get(): PendingSelfUpdate? {
        val preferences = read() ?: return null
        return PendingSelfUpdate(
            appId = preferences[Keys.APP_ID] ?: return null,
            packageName = preferences[Keys.PACKAGE_NAME] ?: return null,
            version = AppVersion(preferences[Keys.VERSION] ?: return null),
            previousLastUpdateTime = preferences[Keys.PREVIOUS_UPDATE_TIME] ?: return null,
            previousVersionCode = preferences[Keys.PREVIOUS_VERSION_CODE] ?: return null,
        )
    }

    override suspend fun put(pending: PendingSelfUpdate) = edit { preferences ->
        preferences[Keys.APP_ID] = pending.appId
        preferences[Keys.PACKAGE_NAME] = pending.packageName
        preferences[Keys.VERSION] = pending.version.raw
        preferences[Keys.PREVIOUS_UPDATE_TIME] = pending.previousLastUpdateTime
        preferences[Keys.PREVIOUS_VERSION_CODE] = pending.previousVersionCode
    }

    override suspend fun clear() = edit { preferences ->
        Keys.ALL.forEach { preferences.remove(it) }
    }

    private suspend fun read(): Preferences? = suspendRunCatching {
        context.appPreferences.data
            .catch { throwable ->
                if (throwable !is IOException) throw throwable
                logger.w(TAG, throwable) { "Could not read the pending self-update" }
                emit(emptyPreferences())
            }
            .first()
    }.getOrNull()

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        suspendRunCatching { context.appPreferences.edit(block) }
            .onFailure { logger.w(TAG, it) { "Could not write the pending self-update" } }
    }

    private object Keys {
        val APP_ID = longPreferencesKey("pending_self_update_app_id")
        val PACKAGE_NAME = stringPreferencesKey("pending_self_update_package")
        val VERSION = stringPreferencesKey("pending_self_update_version")
        val PREVIOUS_UPDATE_TIME = longPreferencesKey("pending_self_update_previous_time")
        val PREVIOUS_VERSION_CODE = longPreferencesKey("pending_self_update_previous_code")

        val ALL = listOf(APP_ID, PACKAGE_NAME, VERSION, PREVIOUS_UPDATE_TIME, PREVIOUS_VERSION_CODE)
    }

    private companion object {
        const val TAG = "SelfUpdate"
    }
}
