package dev.re7gog.b_sideloader.domain.repository

import dev.re7gog.b_sideloader.domain.model.GithubRelease
import dev.re7gog.b_sideloader.domain.model.GithubRepoSummary
import dev.re7gog.b_sideloader.domain.model.TelegramAccount
import dev.re7gog.b_sideloader.domain.model.TelegramApkDocument
import dev.re7gog.b_sideloader.domain.model.TelegramAuthState
import dev.re7gog.b_sideloader.domain.model.TelegramChatSummary
import dev.re7gog.b_sideloader.domain.model.TelegramTopicSummary
import kotlinx.coroutines.flow.Flow

/**
 * One interface per app source.
 *
 * These are deliberately thin wire adapters — they fetch and translate, they do not decide. Which
 * release or message actually wins is [dev.re7gog.b_sideloader.domain.selection] logic, which is
 * pure Kotlin and unit-tested without a network or a device.
 *
 * Adding a source (F-Droid, a plain URL, ...) means a new interface here, a mapper in `data/`, and
 * one branch in `ResolveUpdateUseCase` — nothing in the UI changes.
 *
 * All members throw [dev.re7gog.b_sideloader.domain.error.AppError] on failure.
 */
interface GithubRepository {

    /** Repositories matching a free-text query. */
    suspend fun searchRepositories(query: String, page: Int? = null): List<GithubRepoSummary>

    /** Metadata for one repository, for the details header. */
    suspend fun getRepository(owner: String, repo: String): GithubRepoSummary

    /** Releases, newest first. */
    suspend fun getReleases(owner: String, repo: String, page: Int? = null): List<GithubRelease>
}

interface TelegramRepository {

    /** Current sign-in state. Hot; replays the latest value to new collectors. */
    val authState: Flow<TelegramAuthState>

    /** Errors from auth actions (bad phone/code/password), for the login screen. */
    val authErrors: Flow<String>

    suspend fun sendPhoneNumber(phoneNumber: String)
    suspend fun sendCode(code: String)
    suspend fun sendPassword(password: String)
    suspend fun logOut()

    /** The signed-in account, or `null` when signed out. */
    suspend fun getAccount(): TelegramAccount?

    /** Server-side chat search, limited to channels and supergroups. */
    suspend fun searchChats(query: String, limit: Int = DEFAULT_SEARCH_LIMIT): List<TelegramChatSummary>

    suspend fun getChat(chatId: Long): TelegramChatSummary?

    /** Topics of a forum chat. Empty for a non-forum chat. */
    suspend fun getTopics(chatId: Long, limit: Int = DEFAULT_SEARCH_LIMIT): List<TelegramTopicSummary>

    /** Every APK document in the chat/topic, newest first, before filtering. */
    suspend fun getApkDocuments(
        chatId: Long,
        topicId: Int,
        limit: Int = DEFAULT_MESSAGE_LIMIT,
    ): List<TelegramApkDocument>

    /**
     * Downloads a Telegram file, emitting progress as it goes and finally the local path.
     * Cancelling the collector cancels the TDLib download.
     */
    fun downloadFile(fileId: Int): Flow<TelegramDownload>

    /** Downloads a small file (avatar) and returns its path, or null if it cannot be fetched. */
    suspend fun downloadPhoto(fileId: Int): String?

    /** Drops TDLib's local copy of a file after it has been consumed. */
    suspend fun discardLocalCopy(fileId: Int)

    companion object {
        const val DEFAULT_SEARCH_LIMIT = 50
        const val DEFAULT_MESSAGE_LIMIT = 40
    }
}

/** Progress of a TDLib file download. */
sealed interface TelegramDownload {
    data class Progress(val fraction: Float) : TelegramDownload
    data class Completed(val localPath: String) : TelegramDownload
}
