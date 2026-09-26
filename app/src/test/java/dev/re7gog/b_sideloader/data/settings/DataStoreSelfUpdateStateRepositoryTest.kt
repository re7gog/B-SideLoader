package dev.re7gog.b_sideloader.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.re7gog.b_sideloader.core.log.NoopLogger
import dev.re7gog.b_sideloader.domain.model.AppVersion
import dev.re7gog.b_sideloader.domain.model.PendingSelfUpdate
import dev.re7gog.b_sideloader.domain.model.SelfUpdateState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The self-update record has to survive exactly one thing: the process being killed by the
 * install it describes. Every case here therefore reads back through a *new* repository instance,
 * the way the next process does.
 */
@RunWith(AndroidJUnit4::class)
class DataStoreSelfUpdateStateRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun repository() = DataStoreSelfUpdateStateRepository(context, NoopLogger)

    @Before
    fun clearPreferences() = runTest {
        context.appPreferences.edit { it.clear() }
    }

    @Test
    fun nothingRememberedReadsAsAnEmptyState() = runTest {
        assertEquals(SelfUpdateState(rememberedVersionCode = null, pending = null), repository().get())
    }

    @Test
    fun aPendingUpdateIsReadBackByTheNextProcess() = runTest {
        val pending = PendingSelfUpdate(appId = 3L, releaseName = AppVersion("v1.4.0"))

        repository().markPending(pending)

        assertEquals(pending, repository().get().pending)
    }

    @Test
    fun settlingRemembersTheVersionCodeAndDropsThePendingRecord() = runTest {
        repository().markPending(PendingSelfUpdate(appId = 3L, releaseName = AppVersion("v1.4.0")))

        repository().settle(versionCode = 1_04_00_99L)

        assertEquals(SelfUpdateState(rememberedVersionCode = 1_04_00_99L, pending = null), repository().get())
    }

    @Test
    fun clearingThePendingRecordKeepsTheRememberedVersionCode() = runTest {
        repository().settle(versionCode = 7L)
        repository().markPending(PendingSelfUpdate(appId = 1L, releaseName = AppVersion("v2")))

        repository().clearPending()

        assertEquals(SelfUpdateState(rememberedVersionCode = 7L, pending = null), repository().get())
    }

    /** Half a record cannot say which version it meant, so it is no record at all. */
    @Test
    fun aPendingRecordMissingItsReleaseNameIsIgnored() = runTest {
        context.appPreferences.edit { it[longPreferencesKey("pending_self_update_app_id")] = 3L }

        assertNull(repository().get().pending)
    }

    /** Keys the previous format wrote go away with the record, instead of lingering forever. */
    @Test
    fun clearingAlsoRemovesTheLegacyFormatsKeys() = runTest {
        context.appPreferences.edit {
            it[stringPreferencesKey("pending_self_update_package")] = "dev.re7gog.b_sideloader"
            it[longPreferencesKey("pending_self_update_previous_time")] = 1L
            it[longPreferencesKey("pending_self_update_previous_code")] = 2L
        }

        repository().clearPending()

        assertTrue(context.appPreferences.data.first().asMap().isEmpty())
    }
}
