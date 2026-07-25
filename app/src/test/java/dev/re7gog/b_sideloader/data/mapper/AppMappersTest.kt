package dev.re7gog.b_sideloader.data.mapper

import dev.re7gog.b_sideloader.data.local.entity.AppEntity
import dev.re7gog.b_sideloader.data.local.entity.AppWithDetails
import dev.re7gog.b_sideloader.data.local.entity.GithubDetailsEntity
import dev.re7gog.b_sideloader.data.local.entity.TelegramDetailsEntity
import dev.re7gog.b_sideloader.domain.model.AppSource
import dev.re7gog.b_sideloader.domain.model.AppSourceKind
import dev.re7gog.b_sideloader.domain.model.FilterMode
import dev.re7gog.b_sideloader.testing.githubApp
import dev.re7gog.b_sideloader.testing.telegramApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppMappersTest {

    @Test
    fun `github row maps to a github source`() {
        val row = AppWithDetails(
            app = appEntity(sourceType = AppSourceKind.GitHub.storedValue),
            githubDetails = GithubDetailsEntity(
                id = 1,
                owner = "octocat",
                repo = "example",
                usePrereleases = true,
                releasesInclude = "stable",
                releasesExclude = "rc",
            ),
            telegramDetails = null,
        )

        val app = row.toDomainOrNull()!!
        val source = app.source as AppSource.GitHub

        assertEquals("octocat", source.owner)
        assertEquals("example", source.repo)
        assertTrue(source.usePrereleases)
        assertEquals("stable", source.releaseFilter.include)
        assertEquals("rc", source.releaseFilter.exclude)
    }

    @Test
    fun `telegram row maps to a telegram source`() {
        val row = AppWithDetails(
            app = appEntity(sourceType = AppSourceKind.Telegram.storedValue),
            githubDetails = null,
            telegramDetails = TelegramDetailsEntity(
                id = 1,
                chatId = -100,
                topicId = 7,
                messageInclude = "release",
                messageExclude = "beta",
            ),
        )

        val source = row.toDomainOrNull()!!.source as AppSource.Telegram

        assertEquals(-100L, source.chatId)
        assertEquals(7, source.topicId)
        assertEquals("release", source.messageFilter.include)
    }

    /** A row without its details table is unusable; dropping it beats crashing the whole list. */
    @Test
    fun `row without details maps to null`() {
        val row = AppWithDetails(appEntity(), githubDetails = null, telegramDetails = null)

        assertNull(row.toDomainOrNull())
    }

    @Test
    fun `list mapping drops unusable rows and keeps the rest`() {
        val good = AppWithDetails(
            app = appEntity(id = 1),
            githubDetails = GithubDetailsEntity(1, "octocat", "example", false, "", ""),
            telegramDetails = null,
        )
        val broken = AppWithDetails(appEntity(id = 2), null, null)

        val mapped = listOf(good, broken).toDomain()

        assertEquals(1, mapped.size)
        assertEquals(1L, mapped.single().id)
    }

    @Test
    fun `advancedMode column maps to the regex filter mode`() {
        val row = AppWithDetails(
            app = appEntity(advancedMode = true),
            githubDetails = GithubDetailsEntity(1, "octocat", "example", false, "", ""),
            telegramDetails = null,
        )

        assertEquals(FilterMode.Regex, row.toDomainOrNull()!!.filterMode)
    }

    @Test
    fun `github app round trips through the entity mapping`() {
        val original = githubApp(
            id = 3,
            assetInclude = "arm64",
            releaseInclude = "stable",
            usePrereleases = true,
            filterMode = FilterMode.Regex,
        )

        val row = AppWithDetails(
            app = original.toEntity(),
            githubDetails = (original.source as AppSource.GitHub).toEntity(original.id),
            telegramDetails = null,
        )

        assertEquals(original, row.toDomainOrNull())
    }

    @Test
    fun `telegram app round trips through the entity mapping`() {
        val original = telegramApp(id = 4, assetExclude = "x86", messageInclude = "stable", topicId = 9)

        val row = AppWithDetails(
            app = original.toEntity(),
            githubDetails = null,
            telegramDetails = (original.source as AppSource.Telegram).toEntity(original.id),
        )

        assertEquals(original, row.toDomainOrNull())
    }

    /** The discriminator is an explicit stored value, so reordering the enum cannot corrupt data. */
    @Test
    fun `source kind stored values are stable`() {
        assertEquals(1, AppSourceKind.GitHub.storedValue)
        assertEquals(2, AppSourceKind.Telegram.storedValue)
        assertEquals(AppSourceKind.GitHub, AppSourceKind.fromStoredValue(1))
        assertEquals(AppSourceKind.Telegram, AppSourceKind.fromStoredValue(2))
        assertNull(AppSourceKind.fromStoredValue(99))
    }

    private fun appEntity(
        id: Long = 1,
        sourceType: Int = AppSourceKind.GitHub.storedValue,
        advancedMode: Boolean = false,
    ) = AppEntity(
        id = id,
        sourceType = sourceType,
        packageName = "com.example",
        name = "Example",
        version = "v1.0",
        autoupdate = true,
        filterInclude = "",
        filterExclude = "",
        advancedMode = advancedMode,
    )
}
