package dev.re7gog.b_sideloader.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import dev.re7gog.b_sideloader.core.log.NoopLogger
import dev.re7gog.b_sideloader.domain.model.AppSettings
import dev.re7gog.b_sideloader.domain.model.BackgroundMode
import dev.re7gog.b_sideloader.domain.model.InstallerMode
import dev.re7gog.b_sideloader.domain.model.ThemeMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Settings as they are actually stored: which preference key each field lives under, what an
 * empty or stale file reads as, and the upgrade path from the old background-service flag.
 *
 * The key names matter more than they look — they are what existing installs already have on
 * disk, so renaming one silently resets that setting for every user.
 */
@RunWith(AndroidJUnit4::class)
class DataStoreSettingsRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository = DataStoreSettingsRepository(context, NoopLogger)

    @Before
    fun clearPreferences() = runTest {
        // One DataStore per process: the delegate outlives each test's Application.
        context.appPreferences.edit { it.clear() }
    }

    @Test
    fun anEmptyStoreReadsAsTheDefaults() = runTest {
        assertEquals(AppSettings(), repository.current())
    }

    @Test
    fun everySetterIsReadBack() = runTest {
        repository.setInstallerMode(InstallerMode.Dhizuku)
        repository.setAutoUpdate(false)
        repository.setAllowMeteredNetwork(true)
        repository.setUseDynamicColor(true)
        repository.setThemeMode(ThemeMode.Dark)
        repository.setParallelUpdateChecks(true)
        repository.setBackgroundMode(BackgroundMode.Persistent)
        repository.setLongPressHintSeen(true)

        // A second instance reads the same file, which is what the next process start does.
        val reread = DataStoreSettingsRepository(context, NoopLogger).current()

        assertEquals(
            AppSettings(
                installerMode = InstallerMode.Dhizuku,
                autoUpdate = false,
                allowMeteredNetwork = true,
                useDynamicColor = true,
                themeMode = ThemeMode.Dark,
                parallelUpdateChecks = true,
                backgroundMode = BackgroundMode.Persistent,
                longPressHintSeen = true,
            ),
            reread,
        )
    }

    /** Keys written by earlier releases. Renaming any of these resets the setting on upgrade. */
    @Test
    fun readsTheKeyNamesExistingInstallsHaveOnDisk() = runTest {
        context.appPreferences.edit {
            it[stringPreferencesKey("installer_mode")] = "Shizuku"
            it[booleanPreferencesKey("use_autoupdates")] = false
            it[booleanPreferencesKey("use_mobile_data")] = true
            it[booleanPreferencesKey("use_dynamic_color")] = true
            it[stringPreferencesKey("theme_mode")] = "Light"
            it[booleanPreferencesKey("parallel_update_checks")] = true
            it[booleanPreferencesKey("long_press_hint_seen")] = true
        }

        val settings = repository.current()

        assertEquals(InstallerMode.Shizuku, settings.installerMode)
        assertEquals(false, settings.autoUpdate)
        assertEquals(true, settings.allowMeteredNetwork)
        assertEquals(true, settings.useDynamicColor)
        assertEquals(ThemeMode.Light, settings.themeMode)
        assertEquals(true, settings.parallelUpdateChecks)
        assertEquals(true, settings.longPressHintSeen)
    }

    /** Whoever had opted into the service before `background_mode` existed must keep it. */
    @Test
    fun theLegacyForegroundServiceFlagStillSelectsPersistentMode() = runTest {
        context.appPreferences.edit { it[booleanPreferencesKey("use_foreground_service")] = true }

        assertEquals(BackgroundMode.Persistent, repository.current().backgroundMode)
    }

    @Test
    fun theNewBackgroundModeKeyWinsOverTheLegacyFlag() = runTest {
        context.appPreferences.edit { it[booleanPreferencesKey("use_foreground_service")] = true }

        repository.setBackgroundMode(BackgroundMode.Periodic)

        assertEquals(BackgroundMode.Periodic, repository.current().backgroundMode)
    }

    /** A value from a newer build, or a typo, must not crash an older one. */
    @Test
    fun unknownEnumNamesFallBackToTheDefaults() = runTest {
        context.appPreferences.edit {
            it[stringPreferencesKey("installer_mode")] = "Magisk"
            it[stringPreferencesKey("theme_mode")] = "Sepia"
            it[stringPreferencesKey("background_mode")] = "Hourly"
        }

        val settings = repository.current()

        assertEquals(InstallerMode.Default, settings.installerMode)
        assertEquals(ThemeMode.Default, settings.themeMode)
        assertEquals(BackgroundMode.Default, settings.backgroundMode)
    }

    /**
     * The store is shared with the self-update state. Its writes change the file but not the
     * settings, and must not wake every settings collector (each of which may reschedule work).
     */
    @Test
    fun writesToOtherKeysInTheSharedStoreDoNotReEmitSettings() = runTest {
        repository.settings.test {
            assertEquals(AppSettings(), awaitItem())

            context.appPreferences.edit {
                it[longPreferencesKey("self_update_remembered_version_code")] = 42L
            }
            repository.setThemeMode(ThemeMode.Dark)

            // The next emission is the theme change itself, not a duplicate of the defaults.
            assertEquals(ThemeMode.Dark, awaitItem().themeMode)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
