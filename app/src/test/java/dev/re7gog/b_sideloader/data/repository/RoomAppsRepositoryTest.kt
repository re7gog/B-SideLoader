package dev.re7gog.b_sideloader.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import dev.re7gog.b_sideloader.core.coroutines.DefaultDispatcherProvider
import dev.re7gog.b_sideloader.data.local.AppsDatabase
import dev.re7gog.b_sideloader.domain.model.AppSource
import dev.re7gog.b_sideloader.domain.model.AppVersion
import dev.re7gog.b_sideloader.domain.model.FilterRule
import dev.re7gog.b_sideloader.domain.model.TrackedApp
import dev.re7gog.b_sideloader.testing.githubApp
import dev.re7gog.b_sideloader.testing.telegramApp
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
 * The domain-facing side of the database: [TrackedApp] in, [TrackedApp] out, across two tables.
 *
 * What the DAO test cannot show is that the repository keeps an app row and its details row
 * together — both on insert and on update — and that it round-trips every field the mappers
 * touch, against real SQLite.
 */
@RunWith(AndroidJUnit4::class)
class RoomAppsRepositoryTest {

    private lateinit var database: AppsDatabase
    private lateinit var repository: RoomAppsRepository

    @Before
    fun createRepository() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppsDatabase::class.java).build()
        repository = RoomAppsRepository(database, database.appsDao(), DefaultDispatcherProvider())
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    /**
     * Adds [app] as the app does: unsaved. The fixtures default to id 1, and the DAO inserts with
     * `REPLACE`, so adding two of them as they are would overwrite the first.
     */
    private suspend fun add(app: TrackedApp): Long =
        repository.add(app.copy(id = TrackedApp.NEW_APP_ID))

    @Test
    fun aGithubAppRoundTripsEveryField() = runTest {
        val app = githubApp(
            version = AppVersion("v1.2"),
            assetInclude = "arm64",
            assetExclude = "debug",
            releaseInclude = "stable",
            releaseExclude = "nightly",
            usePrereleases = true,
            autoUpdate = false,
        )

        val id = add(app)

        assertEquals(app.copy(id = id), repository.getApp(id))
    }

    @Test
    fun aTelegramAppRoundTripsEveryField() = runTest {
        val app = telegramApp(
            chatId = -1001L,
            topicId = 42,
            messageInclude = "release",
            messageExclude = "beta",
            version = AppVersion("123"),
        )

        val id = add(app)

        assertEquals(app.copy(id = id), repository.getApp(id))
    }

    @Test
    fun updateRewritesTheSourceFiltersNotJustTheAppRow() = runTest {
        val id = add(githubApp())
        val saved = checkNotNull(repository.getApp(id))

        val source = saved.source as AppSource.GitHub
        repository.update(
            saved.copy(
                name = "Renamed",
                source = source.copy(releaseFilter = FilterRule(include = "stable")),
            )
        )

        val updated = checkNotNull(repository.getApp(id))
        assertEquals("Renamed", updated.name)
        assertEquals("stable", (updated.source as AppSource.GitHub).releaseFilter.include)
    }

    /** An unsaved app has no row to update; writing one would invent an app the user never added. */
    @Test
    fun updatingAnUnsavedAppWritesNothing() = runTest {
        repository.update(githubApp(id = TrackedApp.NEW_APP_ID))

        assertTrue(repository.getApps().isEmpty())
    }

    @Test
    fun findBySourceIgnoresGithubCasingAndMatchesTelegramByChatAndTopic() = runTest {
        add(githubApp(name = "GitHub"))
        add(telegramApp(name = "Telegram", chatId = -5L, topicId = 3))

        assertEquals(
            "GitHub",
            repository.findBySource(AppSource.GitHub(owner = "OCTOCAT", repo = "Example"))?.name,
        )
        assertEquals("Telegram", repository.findBySource(AppSource.Telegram(chatId = -5L, topicId = 3))?.name)
        assertNull(repository.findBySource(AppSource.Telegram(chatId = -5L, topicId = 4)))
    }

    @Test
    fun deleteAllRemovesSavedAppsAndIgnoresUnsavedOnes() = runTest {
        val keep = add(githubApp(name = "Keep"))
        val drop = add(telegramApp(name = "Drop"))

        repository.deleteAll(
            listOf(
                checkNotNull(repository.getApp(drop)),
                githubApp(id = TrackedApp.NEW_APP_ID, name = "Never saved"),
            )
        )

        assertEquals(listOf(keep), repository.getApps().map { it.id })
    }

    @Test
    fun observeAppsFollowsWritesInNameOrder() = runTest {
        repository.observeApps().test {
            assertEquals(emptyList<String>(), awaitItem().map { it.name })

            add(githubApp(name = "beta"))
            assertEquals(listOf("beta"), awaitItem().map { it.name })

            add(telegramApp(name = "Alpha"))
            assertEquals(listOf("Alpha", "beta"), awaitItem().map { it.name })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeAppEmitsNullOnceTheAppIsDeleted() = runTest {
        val id = add(githubApp())

        repository.observeApp(id).test {
            assertNotNull(awaitItem())

            repository.delete(checkNotNull(repository.getApp(id)))

            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
