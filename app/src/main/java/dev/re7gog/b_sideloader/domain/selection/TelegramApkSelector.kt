package dev.re7gog.b_sideloader.domain.selection

import dev.re7gog.b_sideloader.domain.model.AppSource
import dev.re7gog.b_sideloader.domain.model.AppVersion
import dev.re7gog.b_sideloader.domain.model.CandidateFile
import dev.re7gog.b_sideloader.domain.model.CandidateGroup
import dev.re7gog.b_sideloader.domain.model.DownloadRef
import dev.re7gog.b_sideloader.domain.model.TelegramApkDocument
import dev.re7gog.b_sideloader.domain.model.TrackedApp
import dev.re7gog.b_sideloader.domain.model.UpdateCandidate

/**
 * Turns the APK documents of a Telegram channel/topic into candidate groups, and from those the
 * one APK to install.
 *
 * The wrinkle Telegram adds is media albums: a channel commonly posts several architecture splits
 * as one album with a single caption, so the files and the text that describes them live in
 * different messages. [groups] stitches an album back together into one group — every file of it,
 * under the one caption — so an album is accepted or rejected as a unit by the message filter, and
 * the device's architecture then picks among *all* its files. (Albums used to be collapsed to their
 * first matching file, which could leave a 64-bit device with the 32-bit split, or with nothing.)
 *
 * Input is expected newest-first (which is how `searchChatMessages` returns it) and the groups
 * keep that order.
 */
object TelegramApkSelector {

    fun groups(
        documents: List<TelegramApkDocument>,
        app: TrackedApp,
        source: AppSource.Telegram,
    ): List<CandidateGroup> {
        val albums = LinkedHashMap<Long, MutableList<TelegramApkDocument>>()
        var standaloneKey = 0L
        for (document in documents) {
            // Synthetic negative keys for messages outside an album cannot collide with real ids.
            val key = if (document.albumId == TelegramApkDocument.NO_ALBUM) --standaloneKey else document.albumId
            albums.getOrPut(key) { mutableListOf() } += document
        }
        return albums.values.mapNotNull { album -> group(album, app, source) }
    }

    /** The APK to install, or `null` when nothing qualifies: see [TargetSelector]. */
    fun select(
        documents: List<TelegramApkDocument>,
        app: TrackedApp,
        source: AppSource.Telegram,
        deviceAbis: List<String>,
    ): UpdateCandidate? = TargetSelector.select(groups(documents, app, source), deviceAbis)

    private fun group(
        album: List<TelegramApkDocument>,
        app: TrackedApp,
        source: AppSource.Telegram,
    ): CandidateGroup? {
        // One caption per album, carried by whichever message has it — the newest, if several do.
        val caption = album.firstOrNull { it.caption.isNotEmpty() }?.caption.orEmpty()
        if (!NameMatcher.matches(caption, source.messageFilter, app.filterMode)) return null

        // In the order they were posted, which is how Telegram shows an album.
        val apks = album.filter { it.isApk }.sortedBy { it.messageId }
        val matching = apks.filter { matchesAssetFilter(it, app) }
        if (matching.isEmpty()) return null

        // The whole album is one version — the newest message among the files the filter accepts,
        // which is what the stored marker has always been — so which split the device picks never
        // reads as an update.
        val version = AppVersion(matching.maxOf { it.messageId }.toString())
        val notes = caption.takeIf { it.isNotBlank() }
        return CandidateGroup(
            title = null,
            notes = notes,
            files = apks.map { document ->
                CandidateFile(
                    candidate = UpdateCandidate(
                        version = version,
                        download = DownloadRef.TelegramFile(
                            fileId = document.fileId,
                            sizeBytes = document.sizeBytes,
                        ),
                        fileName = document.fileName,
                        sizeBytes = document.sizeBytes,
                        notes = notes,
                    ),
                    matchesFilter = document in matching,
                )
            },
        )
    }

    /**
     * File names are matched without the `.apk` suffix so that an exclude of "x86" cannot be
     * defeated by, and an include of "apk" cannot be satisfied by, the suffix.
     */
    private fun matchesAssetFilter(document: TelegramApkDocument, app: TrackedApp): Boolean =
        NameMatcher.matches(document.fileName.dropLast(APK_SUFFIX.length), app.assetFilter, app.filterMode)

    private val TelegramApkDocument.isApk: Boolean
        get() = fileName.endsWith(APK_SUFFIX, ignoreCase = true)

    private const val APK_SUFFIX = ".apk"
}
