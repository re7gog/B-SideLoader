package dev.re7gog.b_sideloader.data.telegram

import android.util.LongSparseArray
import androidx.core.util.size
import dev.re7gog.b_sideloader.data.filter.AbiSelector
import dev.re7gog.b_sideloader.data.filter.NameFilter
import org.drinkless.tdlib.TdApi

/**
 * A single installable APK found in a Telegram channel/topic. In a media album the file and its
 * descriptive caption may live in different messages, so [msgText] can come from a sibling message
 * of [file]. [id] is the id of the message that carries the file (used as the stored "version").
 */
data class TgApkCandidate(
    val file: TdApi.Document,
    val msgText: String,
    val id: Long
)

/**
 * Filters and orders the APK document messages of a Telegram channel/topic. Shared by the details
 * screen (available-APKs list + install target) and the background updater so both pick the exact
 * same APK. All matching is case-insensitive and whitespace-separated: every include word must
 * appear and no exclude word may appear.
 */
object TgApkSelector {

    private data class Grouped(
        val foundFile: Boolean,
        val foundMsgText: Boolean,
        val file: TdApi.Document,
        val msgText: String,
        val id: Long
    )

    /**
     * Returns the matching APKs, newest first. [filterInclude]/[filterExclude] match against the
     * file name (without the `.apk` suffix); [messageInclude]/[messageExclude] match against the
     * message text (caption). [advanced] switches all four fields to regex matching (see NameFilter).
     */
    fun filter(
        messages: List<TdApi.Message>,
        filterInclude: String,
        filterExclude: String,
        messageInclude: String,
        messageExclude: String,
        advanced: Boolean
    ): List<TgApkCandidate> {
        // Grouped by album id; single messages get synthetic incrementing keys.
        val res = LongSparseArray<Grouped>()
        var latestId = 0L  // For inserting single messages in the right (newest-first) order

        for (msg in messages) {
            val content = (msg.content as? TdApi.MessageDocument) ?: continue
            val document = content.document
            val msgText = content.caption.text

            // Match the file name (without the .apk suffix) against the include/exclude filter.
            val matchesFile by lazy {
                val fileName = document.fileName
                if (!fileName.endsWith(".apk", ignoreCase = true)) return@lazy false
                NameFilter.matches(fileName.dropLast(4), filterInclude, filterExclude, advanced)
            }

            // Match the message text against the message include/exclude filter.
            val matchesText by lazy {
                NameFilter.matches(msgText, messageInclude, messageExclude, advanced)
            }

            val albumId = msg.mediaAlbumId
            if (albumId == 0L) {  // Standalone message: keep only if file AND text match.
                if (matchesFile && matchesText) {
                    latestId += 1
                    res.put(latestId, Grouped(
                        foundFile = true, foundMsgText = true,
                        file = document, msgText = msgText, id = msg.id
                    ))
                }
            } else {  // Album: the file and the caption may be spread across different messages.
                var album = res.get(albumId)
                if (album == null) {
                    res.put(albumId, Grouped(
                        foundFile = matchesFile, foundMsgText = matchesText,
                        file = document, msgText = msgText, id = msg.id
                    ))
                    if (albumId > latestId) latestId = albumId  // Remember the latest group id
                } else {
                    if (!album.foundFile && matchesFile) {  // The message carrying the file
                        res.put(albumId, album.copy(foundFile = true, file = document, id = msg.id))
                        album = res.get(albumId)
                    }
                    if (album.msgText == "" && msgText != "") {  // The message carrying the caption
                        res.put(albumId, album.copy(foundMsgText = matchesText, msgText = msgText))
                    }
                }
            }
        }

        // Keep only entries where both the file and the text matched; newest first.
        val result = mutableListOf<TgApkCandidate>()
        for (i in res.size - 1 downTo 0) {
            val value = res.valueAt(i)
            if (value.foundFile && value.foundMsgText) {
                result.add(TgApkCandidate(file = value.file, msgText = value.msgText, id = value.id))
            }
        }
        return result
    }

    /**
     * Picks the APK to install from [candidates] (already newest-first): the newest one that can
     * actually run on this device. A file can run when it is universal (its name carries no
     * architecture marker) or it is built for one of the device's ABIs ([deviceAbis]).
     *
     * This prefers a newer universal build over an older architecture-specific one, yet still falls
     * back to the matching-architecture file (even if it is older in the list) when the newest
     * release is split per ABI and its top file is for a different architecture. If nothing is
     * installable (e.g. no build for this device's ABIs at all) it falls back to the newest match.
     */
    fun pickTarget(candidates: List<TgApkCandidate>, deviceAbis: List<String>): TgApkCandidate? =
        candidates.firstOrNull { AbiSelector.runsOn(it.file.fileName, deviceAbis) }
            ?: candidates.firstOrNull()
}
