package dev.re7gog.b_sideloader.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * The app's single preference store.
 *
 * One `DataStore` for the whole app, shared by everything that keeps a key-value scrap:
 * [DataStoreSettingsRepository] for what the user configures and
 * [DataStorePendingSelfUpdateRepository] for the write-ahead record of an in-flight self-update.
 * A second store would mean a second file, a second corruption story and a second migration path
 * for what is a handful of keys.
 *
 * The name is the one the settings have always used, so existing preferences keep being read.
 */
internal val Context.appPreferences: DataStore<Preferences> by preferencesDataStore(name = "settings")
