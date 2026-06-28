package dev.re7gog.b_sideloader.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private object Keys {
        val USE_SHIZUKU = booleanPreferencesKey("use_shizuku")
        val USE_AUTOUPDATES = booleanPreferencesKey("use_autoupdates")
        val USE_MOBILE_DATA = booleanPreferencesKey("use_mobile_data")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
    }

    val useShizuku: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[Keys.USE_SHIZUKU] ?: false
    }

    val useAutoupdates: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[Keys.USE_AUTOUPDATES] ?: true
    }

    val useMobileData: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[Keys.USE_MOBILE_DATA] ?: false
    }

    val useDynamicColor: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[Keys.USE_DYNAMIC_COLOR] ?: false
    }

    suspend fun setUseShizuku(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.USE_SHIZUKU] = enabled
        }
    }

    suspend fun setUseAutoupdates(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.USE_AUTOUPDATES] = enabled
        }
    }

    suspend fun setUseMobileData(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.USE_MOBILE_DATA] = enabled
        }
    }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.USE_DYNAMIC_COLOR] = enabled
        }
    }
}