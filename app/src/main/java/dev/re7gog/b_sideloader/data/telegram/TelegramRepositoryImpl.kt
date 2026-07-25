package dev.re7gog.b_sideloader.data.telegram

import dev.re7gog.b_sideloader.core.coroutines.DispatcherProvider
import dev.re7gog.b_sideloader.core.coroutines.suspendRunCatching
import dev.re7gog.b_sideloader.core.log.Logger
import dev.re7gog.b_sideloader.data.telegram.mapper.toApkDocuments
import dev.re7gog.b_sideloader.data.telegram.mapper.toAuthState
import dev.re7gog.b_sideloader.data.telegram.mapper.toDomain
import dev.re7gog.b_sideloader.domain.model.TelegramAccount
import dev.re7gog.b_sideloader.domain.model.TelegramApkDocument
import dev.re7gog.b_sideloader.domain.model.TelegramAuthState
import dev.re7gog.b_sideloader.domain.model.TelegramChatSummary
import dev.re7gog.b_sideloader.domain.model.TelegramTopicSummary
import dev.re7gog.b_sideloader.domain.repository.TelegramDownload
import dev.re7gog.b_sideloader.domain.repository.TelegramRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain-facing Telegram source. Every `TdApi` type stops here.
 */
@Singleton
class TelegramRepositoryImpl @Inject constructor(
    private val client: TdlibClient,
    private val dispatchers: DispatcherProvider,
    private val logger: Logger,
) : TelegramRepository {

    override val authState: Flow<TelegramAuthState> =
        client.authorizationState.map { it.toAuthState() }

    override val authErrors: SharedFlow<String> = client.authErrors

    override suspend fun sendPhoneNumber(phoneNumber: String) =
        client.sendAuthRequest(TdApi.SetAuthenticationPhoneNumber(phoneNumber, null))

    override suspend fun sendCode(code: String) =
        client.sendAuthRequest(TdApi.CheckAuthenticationCode(code))

    override suspend fun sendPassword(password: String) =
        client.sendAuthRequest(TdApi.CheckAuthenticationPassword(password))

    override suspend fun logOut() = client.sendAuthRequest(TdApi.LogOut())

    override suspend fun getAccount(): TelegramAccount? = withContext(dispatchers.io) {
        val user = client.requestOrNull<TdApi.User>(TdApi.GetMe()) ?: return@withContext null
        val username = user.usernames?.activeUsernames?.firstOrNull()
        val name = listOfNotNull(user.firstName, user.lastName)
            .joinToString(" ")
            .trim()
            .ifEmpty { username.orEmpty() }
        TelegramAccount(
            displayName = name.ifEmpty { UNNAMED_ACCOUNT },
            username = username,
            avatarPath = user.profilePhoto?.small?.id?.let { downloadPhoto(it) },
        )
    }

    override suspend fun searchChats(query: String, limit: Int): List<TelegramChatSummary> =
        withContext(dispatchers.io) {
            if (query.isBlank()) return@withContext emptyList()
            val found = client.requestOrNull<TdApi.Chats>(TdApi.SearchChatsOnServer(query, limit))
                ?: return@withContext emptyList()
            // TDLib delivers the chats themselves via UpdateNewChat *before* it answers the
            // search, so the cache already holds them and no extra round trip is needed.
            found.chatIds
                .toList()
                .mapNotNull { client.cachedChat(it) }
                .filter { it.type is TdApi.ChatTypeSupergroup }
                .map { it.toDomain(isForum = false) }
        }

    override suspend fun getChat(chatId: Long): TelegramChatSummary? = withContext(dispatchers.io) {
        val chat = client.requestOrNull<TdApi.Chat>(TdApi.GetChat(chatId)) ?: return@withContext null
        chat.toDomain(isForum = isForum(chat))
    }

    override suspend fun getTopics(chatId: Long, limit: Int): List<TelegramTopicSummary> =
        withContext(dispatchers.io) {
            val topics = client.requestOrNull<TdApi.ForumTopics>(
                TdApi.GetForumTopics(
                    /* chatId = */ chatId,
                    /* query = */ "",
                    /* offsetDate = */ 0,
                    /* offsetMessageId = */ 0L,
                    /* offsetForumTopicId = */ 0,
                    /* limit = */ limit,
                )
            ) ?: return@withContext emptyList()
            topics.topics.map { it.toDomain() }
        }

    override suspend fun getApkDocuments(
        chatId: Long,
        topicId: Int,
        limit: Int,
    ): List<TelegramApkDocument> = withContext(dispatchers.io) {
        val topic: TdApi.MessageTopic? =
            if (topicId != NO_TOPIC) TdApi.MessageTopicForum(topicId) else null
        val found = client.requestOrNull<TdApi.FoundChatMessages>(
            TdApi.SearchChatMessages(
                /* chatId = */ chatId,
                /* topicId = */ topic,
                /* query = */ "",
                /* senderId = */ null,
                /* fromMessageId = */ 0L,
                /* offset = */ 0,
                /* limit = */ limit,
                /* filter = */ TdApi.SearchMessagesFilterDocument(),
            )
        ) ?: return@withContext emptyList()
        found.messages.toApkDocuments()
    }

    /**
     * Drives a TDLib download and reports progress.
     *
     * Two details that the previous implementation got wrong:
     *  - the update subscription is established *before* `DownloadFile` is sent, so a file already
     *    in TDLib's cache (which completes instantly) cannot slip through unnoticed;
     *  - cancelling the collector actually cancels the transfer, instead of leaving TDLib pulling
     *    bytes for a screen the user has already left.
     *
     * It also emits progress at all, which is what lets the Telegram install show a real bar
     * during the download phase rather than freezing until it is done.
     */
    override fun downloadFile(fileId: Int): Flow<TelegramDownload> = flow {
        val existing = client.requestOrNull<TdApi.File>(TdApi.GetFile(fileId))
        if (existing?.local?.isDownloadingCompleted == true) {
            emit(TelegramDownload.Progress(1f))
            emit(TelegramDownload.Completed(existing.local.path))
            return@flow
        }

        client.fileUpdates
            .onSubscription {
                client.fireAndForget(
                    TdApi.DownloadFile(
                        /* fileId = */ fileId,
                        /* priority = */ DOWNLOAD_PRIORITY,
                        /* offset = */ 0L,
                        /* limit = */ 0L,
                        /* synchronous = */ false,
                    )
                )
            }
            .filter { it.id == fileId }
            .transformWhile { file ->
                if (file.local.isDownloadingCompleted) {
                    emit(TelegramDownload.Progress(1f))
                    emit(TelegramDownload.Completed(file.local.path))
                    false // terminal: stop collecting the shared update stream
                } else {
                    val expected = file.expectedSize.takeIf { it > 0L } ?: file.size
                    if (expected > 0L) {
                        emit(
                            TelegramDownload.Progress(
                                file.local.downloadedSize.toFloat() / expected
                            )
                        )
                    }
                    true
                }
            }
            .collect { emit(it) }
    }.onCompletion { cause ->
        // Also runs on cancellation, which is what actually stops TDLib.
        if (cause != null) {
            suspendRunCatching { client.request(TdApi.CancelDownloadFile(fileId, false)) }
                .onFailure { logger.w(TAG) { "Could not cancel download of file $fileId" } }
        }
    }.flowOn(dispatchers.io)

    override suspend fun downloadPhoto(fileId: Int): String? = withContext(dispatchers.io) {
        suspendRunCatching {
            downloadFile(fileId)
                .filterIsInstance<TelegramDownload.Completed>()
                .first()
                .localPath
        }.getOrNull()
    }

    override suspend fun discardLocalCopy(fileId: Int) {
        withContext(dispatchers.io) {
            suspendRunCatching { client.request(TdApi.DeleteFile(fileId)) }
                .onFailure { logger.w(TAG) { "Could not delete local copy of file $fileId" } }
        }
    }

    private suspend fun isForum(chat: TdApi.Chat): Boolean {
        val type = chat.type as? TdApi.ChatTypeSupergroup ?: return false
        return client.requestOrNull<TdApi.Supergroup>(TdApi.GetSupergroup(type.supergroupId))
            ?.isForum == true
    }

    private companion object {
        const val TAG = "TelegramRepo"
        const val UNNAMED_ACCOUNT = "Telegram account"
        const val NO_TOPIC = 0

        /** TDLib priority is 1..32; 32 means "a user is waiting for this". */
        const val DOWNLOAD_PRIORITY = 32
    }
}
