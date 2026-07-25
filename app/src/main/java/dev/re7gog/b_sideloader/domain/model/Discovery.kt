package dev.re7gog.b_sideloader.domain.model

/**
 * What the sources offer, in domain shape. These deliberately do not expose `GithubRepoDto` or
 * `TdApi.*`: the UI, the selection logic and the background updater all consume these types, so
 * none of them has to learn Retrofit's or TDLib's vocabulary, and none breaks when a wire format
 * changes.
 */

/** A GitHub repository returned by search or a direct lookup. */
data class GithubRepoSummary(
    val owner: String,
    val name: String,
    val description: String? = null,
    val stars: Int = 0,
    val avatarUrl: String? = null,
) {
    val slug: String get() = "$owner/$name"
}

/** One GitHub release, with the assets that could be APKs. */
data class GithubRelease(
    val name: String,
    val notes: String = "",
    val isPrerelease: Boolean = false,
    val assets: List<GithubAsset> = emptyList(),
)

data class GithubAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long = 0L,
) {
    val isApk: Boolean get() = name.endsWith(APK_SUFFIX, ignoreCase = true)

    private companion object {
        const val APK_SUFFIX = ".apk"
    }
}

/** A Telegram chat that could host APKs. */
data class TelegramChatSummary(
    val id: Long,
    val title: String,
    val username: String? = null,
    /** Small photo file id for the avatar, or null when the chat has no photo. */
    val photoFileId: Int? = null,
    val isForum: Boolean = false,
)

/** A forum topic inside a [TelegramChatSummary] with `isForum = true`. */
data class TelegramTopicSummary(
    val id: Int,
    val name: String,
    /**
     * 0xRRGGBB accent Telegram assigns to the topic, or 0 when it has none. Kept as a plain Int
     * rather than a Compose `Color` so the domain stays free of UI types.
     */
    val iconColor: Int = 0,
) {
    val hasIconColor: Boolean get() = iconColor != 0
}

/**
 * An APK document posted in a Telegram chat/topic.
 *
 * [albumId] is TDLib's `mediaAlbumId`: inside an album the file and the caption that describes it
 * routinely live in *different* messages, so the selector has to stitch siblings back together
 * before it can filter on both. `0` means the message stands alone.
 */
data class TelegramApkDocument(
    /** Id of the message carrying the file. Doubles as the stored version marker. */
    val messageId: Long,
    val fileId: Int,
    val fileName: String,
    val sizeBytes: Long,
    val caption: String = "",
    val albumId: Long = NO_ALBUM,
) {
    companion object {
        const val NO_ALBUM: Long = 0L
    }
}

/** The Telegram sign-in state, reduced to the steps the login screen actually renders. */
sealed interface TelegramAuthState {
    data object Initialising : TelegramAuthState
    data object WaitingForPhoneNumber : TelegramAuthState
    data object WaitingForCode : TelegramAuthState
    data object WaitingForPassword : TelegramAuthState
    data object Ready : TelegramAuthState
    data object LoggedOut : TelegramAuthState
}

/** The signed-in Telegram account, as shown on the settings page. */
data class TelegramAccount(
    val displayName: String,
    val username: String? = null,
    /** Local path of the downloaded avatar, or null when there is none. */
    val avatarPath: String? = null,
)
