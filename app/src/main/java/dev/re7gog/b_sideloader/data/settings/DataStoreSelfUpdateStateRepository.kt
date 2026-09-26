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
import dev.re7gog.b_sideloader.domain.model.SelfUpdateState
import dev.re7gog.b_sideloader.domain.repository.SelfUpdateStateRepository
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-backed [SelfUpdateStateRepository], sharing [appPreferences] with the settings.
 *
 * Every change is one `edit`, so the state a restarted process reads is whole or absent — a
 * pending record without its release name would be worse than none, because nothing could say
 * which version it referred to.
 *
 * Failures are logged and swallowed: this state only exists to make a self-update land in the
 * database, and it must never be the reason an install fails or the app cannot start.
 */
@Singleton
class DataStoreSelfUpdateStateRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val logger: Logger,
) : SelfUpdateStateRepository {

    override suspend fun get(): SelfUpdateState {
        val preferences = read() ?: emptyPreferences()
        return SelfUpdateState(
            rememberedVersionCode = preferences[Keys.VERSION_CODE],
            pending = pendingFrom(preferences),
        )
    }

    override suspend fun markPending(pending: PendingSelfUpdate) = edit { preferences ->
        preferences[Keys.PENDING_APP_ID] = pending.appId
        preferences[Keys.PENDING_RELEASE] = pending.releaseName.raw
    }

    override suspend fun clearPending() = edit { it.removePending() }

    override suspend fun settle(versionCode: Long) = edit { preferences ->
        preferences.removePending()
        preferences[Keys.VERSION_CODE] = versionCode
    }

    private fun pendingFrom(preferences: Preferences): PendingSelfUpdate? {
        val appId = preferences[Keys.PENDING_APP_ID] ?: return null
        val releaseName = preferences[Keys.PENDING_RELEASE] ?: return null
        return PendingSelfUpdate(appId = appId, releaseName = AppVersion(releaseName))
    }

    private fun MutablePreferences.removePending() {
        remove(Keys.PENDING_APP_ID)
        remove(Keys.PENDING_RELEASE)
        Keys.LEGACY.forEach { remove(it) }
    }

    private suspend fun read(): Preferences? = suspendRunCatching {
        context.appPreferences.data
            .catch { throwable ->
                if (throwable !is IOException) throw throwable
                logger.w(TAG, throwable) { "Could not read the self-update state" }
                emit(emptyPreferences())
            }
            .first()
    }.getOrNull()

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        suspendRunCatching { context.appPreferences.edit(block) }
            .onFailure { logger.w(TAG, it) { "Could not write the self-update state" } }
    }

    private object Keys {
        // The two pending keys keep the names the previous format used, so a record written by an
        // older build is still recognised — and then dropped, as nothing remembers its version code.
        val PENDING_APP_ID = longPreferencesKey("pending_self_update_app_id")
        val PENDING_RELEASE = stringPreferencesKey("pending_self_update_version")
        val VERSION_CODE = longPreferencesKey("self_update_remembered_version_code")

        /** Left by the previous format; removed along with the pending record. */
        val LEGACY = listOf(
            stringPreferencesKey("pending_self_update_package"),
            longPreferencesKey("pending_self_update_previous_time"),
            longPreferencesKey("pending_self_update_previous_code"),
        )
    }

    private companion object {
        const val TAG = "SelfUpdate"
    }
}
