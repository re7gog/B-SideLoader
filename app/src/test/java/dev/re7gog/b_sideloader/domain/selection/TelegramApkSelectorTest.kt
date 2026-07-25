package dev.re7gog.b_sideloader.domain.selection

import dev.re7gog.b_sideloader.domain.model.AppSource
import dev.re7gog.b_sideloader.domain.model.DownloadRef
import dev.re7gog.b_sideloader.domain.model.TelegramApkDocument
import dev.re7gog.b_sideloader.domain.model.TrackedApp
import dev.re7gog.b_sideloader.testing.ARM64_ABIS
import dev.re7gog.b_sideloader.testing.telegramApp
import dev.re7gog.b_sideloader.testing.tgDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramApkSelectorTest {

    @Test
    fun `keeps standalone messages whose file and caption both match`() {
        val app = telegramApp(assetInclude = "app", messageInclude = "release")
        val candidates = filter(
            app,
            tgDocument(1, "app-universal.apk", caption = "release build"),
            tgDocument(2, "other.apk", caption = "release build"),
            tgDocument(3, "app-universal.apk", caption = "debug build"),
        )

        assertEquals(listOf(1L), candidates.map { it.version.raw.toLong() })
    }

    @Test
    fun `non apk documents are ignored`() {
        val candidates = filter(telegramApp(), tgDocument(1, "notes.txt"))
        assertTrue(candidates.isEmpty())
    }

    /**
     * The `.apk` suffix is stripped before matching, so an exclude of "apk" filters on the *name*
     * rather than trivially rejecting every file.
     */
    @Test
    fun `file name is matched without the apk suffix`() {
        val candidates = filter(telegramApp(assetExclude = "apk"), tgDocument(1, "app-universal.apk"))
        assertEquals(1, candidates.size)
    }

    /**
     * The Telegram-specific wrinkle: a channel posts the splits as an album with one caption, so
     * the file and the text that describes it arrive in different messages.
     */
    @Test
    fun `album caption from a sibling message applies to the file`() {
        val app = telegramApp(assetInclude = "arm64", messageInclude = "stable")
        val candidates = filter(
            app,
            tgDocument(10, "app-arm64-v8a.apk", caption = "", albumId = 99L),
            tgDocument(11, "app-x86_64.apk", caption = "stable 1.2.3", albumId = 99L),
        )

        assertEquals(1, candidates.size)
        assertEquals("app-arm64-v8a.apk", candidates.single().fileName)
        assertEquals("stable 1.2.3", candidates.single().notes)
    }

    @Test
    fun `album is rejected when its caption fails the filter`() {
        val app = telegramApp(assetInclude = "arm64", messageExclude = "beta")
        val candidates = filter(
            app,
            tgDocument(10, "app-arm64-v8a.apk", caption = "", albumId = 99L),
            tgDocument(11, "app-x86_64.apk", caption = "beta channel", albumId = 99L),
        )

        assertTrue(candidates.isEmpty())
    }

    @Test
    fun `standalone messages keep their own order and do not merge`() {
        val candidates = filter(
            telegramApp(),
            tgDocument(3, "c.apk"),
            tgDocument(2, "b.apk"),
            tgDocument(1, "a.apk"),
        )

        assertEquals(listOf("c.apk", "b.apk", "a.apk"), candidates.map { it.fileName })
    }

    @Test
    fun `select picks the newest runnable candidate`() {
        val app = telegramApp()
        val target = TelegramApkSelector.select(
            documents = listOf(
                tgDocument(3, "app-x86_64.apk"),
                tgDocument(2, "app-arm64-v8a.apk"),
                tgDocument(1, "app-universal.apk"),
            ),
            app = app,
            source = app.source as AppSource.Telegram,
            deviceAbis = ARM64_ABIS,
        )

        assertEquals("app-arm64-v8a.apk", target?.fileName)
    }

    @Test
    fun `candidate version is the message id and download points at the file`() {
        val candidate = filter(telegramApp(), tgDocument(42, "app.apk", fileId = 7, sizeBytes = 99L)).single()

        assertEquals("42", candidate.version.raw)
        assertEquals(DownloadRef.TelegramFile(fileId = 7, sizeBytes = 99L), candidate.download)
    }

    @Test
    fun `returns null when there is nothing to install`() {
        val app = telegramApp()
        assertNull(
            TelegramApkSelector.select(
                documents = emptyList(),
                app = app,
                source = app.source as AppSource.Telegram,
                deviceAbis = ARM64_ABIS,
            )
        )
    }

    private fun filter(app: TrackedApp, vararg documents: TelegramApkDocument) =
        TelegramApkSelector.filter(
            documents = documents.toList(),
            app = app,
            source = app.source as AppSource.Telegram,
        )
}
