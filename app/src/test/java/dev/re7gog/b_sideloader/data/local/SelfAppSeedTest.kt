package dev.re7gog.b_sideloader.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.re7gog.b_sideloader.BuildConfig
import dev.re7gog.b_sideloader.data.di.DatabaseModule
import dev.re7gog.b_sideloader.data.mapper.toDomain
import dev.re7gog.b_sideloader.domain.model.AppVersion
import dev.re7gog.b_sideloader.domain.model.SelfApp
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The other half of the self-row seeding: a database created at the current version runs no
 * migration, so the production builder's `onCreate` callback has to add the row itself.
 */
@RunWith(AndroidJUnit4::class)
class SelfAppSeedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun aNewDatabaseTracksTheAppItselfFromTheStart() = runTest {
        val database = DatabaseModule.provideAppsDatabase(context)
        try {
            val self = database.appsDao().getAll().toDomain().single()

            assertEquals(BuildConfig.APPLICATION_ID, self.packageName)
            assertEquals(SelfApp.NAME, self.name)
            assertTrue(SelfApp.isPublishedBy(self.source))
            assertEquals(AppVersion(BuildConfig.RELEASE_TAG), self.version)
            assertTrue(self.autoUpdate)
        } finally {
            database.close()
        }
    }

    /** "A user who removes the row means it": `onCreate` runs once per database, not per open. */
    @Test
    fun aDeletedSelfRowStaysDeletedAcrossRestarts() = runTest {
        val first = DatabaseModule.provideAppsDatabase(context)
        val dao = first.appsDao()
        dao.deleteByIds(dao.getAll().map { it.app.id })
        first.close()

        val reopened = DatabaseModule.provideAppsDatabase(context)
        try {
            assertTrue(reopened.appsDao().getAll().isEmpty())
        } finally {
            reopened.close()
        }
    }
}
