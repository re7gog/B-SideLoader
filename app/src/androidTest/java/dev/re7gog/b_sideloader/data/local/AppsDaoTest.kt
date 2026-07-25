package dev.re7gog.b_sideloader.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.re7gog.b_sideloader.data.local.dao.AppsDao
import dev.re7gog.b_sideloader.data.local.entity.AppEntity
import dev.re7gog.b_sideloader.data.local.entity.GithubDetailsEntity
import dev.re7gog.b_sideloader.data.local.entity.TelegramDetailsEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DAO behaviour against the real SQLite engine.
 *
 * Runs on a device rather than on the JVM on purpose: the things worth testing here — the
 * `@Relation` join, `ON DELETE CASCADE`, `COLLATE NOCASE` ordering and matching — are all
 * behaviours of SQLite itself, and a JVM fake would prove nothing about them.
 */
@RunWith(AndroidJUnit4::class)
class AppsDaoTest {

    private lateinit var database: AppsDatabase
    private lateinit var dao: AppsDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppsDatabase::class.java)
            // Foreign keys are off by default in SQLite; the cascade below depends on them.
            .setQueryCallback({ _, _ -> }, Runnable::run)
            .build()
        dao = database.appsDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun insertedGithubAppIsReturnedWithItsDetails() = runTest {
        val id = dao.insertApp(appEntity(name = "Alpha"))
        dao.upsertGithubDetails(githubDetails(id))

        val stored = dao.observeAll().first().single()

        assertEquals("Alpha", stored.app.name)
        assertNotNull(stored.githubDetails)
        assertNull(stored.telegramDetails)
        assertEquals("octocat", stored.githubDetails?.owner)
    }

    @Test
    fun insertedTelegramAppIsReturnedWithItsDetails() = runTest {
        val id = dao.insertApp(appEntity(name = "Beta", sourceType = 2))
        dao.upsertTelegramDetails(telegramDetails(id))

        val stored = dao.observeAll().first().single()

        assertNull(stored.githubDetails)
        assertEquals(-100L, stored.telegramDetails?.chatId)
    }

    @Test
    fun appsAreOrderedByNameCaseInsensitively() = runTest {
        listOf("banana", "Apple", "cherry").forEach { dao.insertApp(appEntity(name = it)) }

        val names = dao.observeAll().first().map { it.app.name }

        assertEquals(listOf("Apple", "banana", "cherry"), names)
    }

    @Test
    fun findGithubAppIgnoresCase() = runTest {
        val id = dao.insertApp(appEntity())
        dao.upsertGithubDetails(githubDetails(id))

        assertNotNull(dao.findGithubApp("OCTOCAT", "Example"))
    }

    @Test
    fun findTelegramAppMatchesChatAndTopic() = runTest {
        val id = dao.insertApp(appEntity(sourceType = 2))
        dao.upsertTelegramDetails(telegramDetails(id, topicId = 7))

        assertNotNull(dao.findTelegramApp(-100L, 7))
        assertNull(dao.findTelegramApp(-100L, 8))
    }

    /** The details row must go with its app; a leftover would resurface on the next insert. */
    @Test
    fun deletingAnAppCascadesToItsDetails() = runTest {
        val id = dao.insertApp(appEntity())
        dao.upsertGithubDetails(githubDetails(id))

        dao.deleteByIds(listOf(id))

        assertTrue(dao.observeAll().first().isEmpty())
        // Re-inserting with the same id must not pick up the old details row.
        val reused = dao.insertApp(appEntity(id = id))
        assertNull(dao.observeAll().first().single { it.app.id == reused }.githubDetails)
    }

    @Test
    fun updatingDetailsReplacesTheExistingRow() = runTest {
        val id = dao.insertApp(appEntity())
        dao.upsertGithubDetails(githubDetails(id))

        dao.upsertGithubDetails(githubDetails(id).copy(releasesInclude = "stable"))

        assertEquals("stable", dao.observeAll().first().single().githubDetails?.releasesInclude)
    }

    @Test
    fun observeByIdEmitsNullOnceTheAppIsGone() = runTest {
        val id = dao.insertApp(appEntity())
        dao.upsertGithubDetails(githubDetails(id))
        assertNotNull(dao.observeById(id).first())

        dao.deleteByIds(listOf(id))

        assertNull(dao.observeById(id).first())
    }

    @Test
    fun deleteByIdsRemovesOnlyTheGivenApps() = runTest {
        val keep = dao.insertApp(appEntity(name = "Keep"))
        val drop = dao.insertApp(appEntity(name = "Drop"))

        dao.deleteByIds(listOf(drop))

        assertEquals(listOf(keep), dao.getAll().map { it.app.id })
    }

    private fun appEntity(id: Long = 0, name: String = "Example", sourceType: Int = 1) = AppEntity(
        id = id,
        sourceType = sourceType,
        packageName = "com.example",
        name = name,
        version = "v1.0",
        autoupdate = true,
        filterInclude = "",
        filterExclude = "",
    )

    private fun githubDetails(id: Long) = GithubDetailsEntity(
        id = id,
        owner = "octocat",
        repo = "example",
        usePrereleases = false,
        releasesInclude = "",
        releasesExclude = "",
    )

    private fun telegramDetails(id: Long, topicId: Int = 0) = TelegramDetailsEntity(
        id = id,
        chatId = -100L,
        topicId = topicId,
        messageInclude = "",
        messageExclude = "",
    )
}
