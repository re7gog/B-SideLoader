package dev.re7gog.b_sideloader.domain.selection

import dev.re7gog.b_sideloader.domain.model.AppSource
import dev.re7gog.b_sideloader.domain.model.AppVersion
import dev.re7gog.b_sideloader.domain.model.DownloadRef
import dev.re7gog.b_sideloader.domain.model.TelegramApkDocument
import dev.re7gog.b_sideloader.domain.model.TrackedApp
import dev.re7gog.b_sideloader.domain.model.UpdateCandidate

/**
 * Turns the APK documents of a Telegram channel/topic into installable candidates.
 *
 * The wrinkle Telegram adds is media albums: a channel commonly posts several architecture splits
 * as one album with a single caption, so the file and the text that describes it live in different
 * messages. [filter] stitches an album's messages back together before applying the two filters —
 * file name against the app's asset filter, caption against the source's message filter — so an
 * album is accepted or rejected as a unit.
 *
 * Input is expected newest-first (which is how `searchChatMessages` returns it) and the output
 * preserves that order.
 */
object TelegramApkSelector {

    fun filter(
        documents: List<TelegramApkDocument>,
        app: TrackedApp,
        source: AppSource.Telegram,
    ): List<UpdateCandidate> {
        val groups = LinkedHashMap<Long, AlbumGroup>()
        var standaloneKey = 0L

        for (document in documents) {
            // File names are matched without the `.apk` suffix so that an exclude of "x86"
            // cannot be defeated by, and an include of "apk" cannot be satisfied by, the suffix.
            val fileMatches = document.fileName.endsWith(APK_SUFFIX, ignoreCase = true) &&
                NameMatcher.matches(
                    document.fileName.dropLast(APK_SUFFIX.length),
                    app.assetFilter,
                    app.filterMode,
                )
            val captionMatches =
                NameMatcher.matches(document.caption, source.messageFilter, app.filterMode)

            if (document.albumId == TelegramApkDocument.NO_ALBUM) {
                if (fileMatches && captionMatches) {
                    // Synthetic negative keys cannot collide with real album ids.
                    groups[--standaloneKey] = AlbumGroup(
                        document = document,
                        fileMatches = true,
                        captionMatches = true,
                    )
                }
                continue
            }

            val existing = groups[document.albumId]
            if (existing == null) {
                groups[document.albumId] = AlbumGroup(
                    document = document,
                    fileMatches = fileMatches,
                    captionMatches = captionMatches,
                )
                continue
            }
            // Later siblings can supply the piece the first one lacked: the matching file, or the
            // caption. Whichever arrives first wins, matching how albums are actually posted.
            var merged = existing
            if (!merged.fileMatches && fileMatches) {
                merged = merged.copy(document = document, fileMatches = true)
            }
            if (merged.caption.isEmpty() && document.caption.isNotEmpty()) {
                merged = merged.copy(
                    captionOverride = document.caption,
                    captionMatches = captionMatches,
                )
            }
            groups[document.albumId] = merged
        }

        return groups.values
            .filter { it.fileMatches && it.captionMatches }
            .map { group ->
                UpdateCandidate(
                    version = AppVersion(group.document.messageId.toString()),
                    download = DownloadRef.TelegramFile(
                        fileId = group.document.fileId,
                        sizeBytes = group.document.sizeBytes,
                    ),
                    fileName = group.document.fileName,
                    sizeBytes = group.document.sizeBytes,
                    notes = group.caption.takeIf { it.isNotBlank() },
                )
            }
    }

    /** The one candidate to install: newest that runs on this device, else newest overall. */
    fun select(
        documents: List<TelegramApkDocument>,
        app: TrackedApp,
        source: AppSource.Telegram,
        deviceAbis: List<String>,
    ): UpdateCandidate? =
        AbiMatcher.pickInstallable(filter(documents, app, source), deviceAbis) { it.fileName }

    private const val APK_SUFFIX = ".apk"

    /**
     * An album (or a standalone message) while it is being assembled. [captionOverride] carries a
     * caption picked up from a sibling message.
     */
    private data class AlbumGroup(
        val document: TelegramApkDocument,
        val fileMatches: Boolean,
        val captionMatches: Boolean,
        val captionOverride: String? = null,
    ) {
        val caption: String get() = captionOverride ?: document.caption
    }
}
