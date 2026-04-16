package dev.re7gog.b_sideloader.data.telegram

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class TelegramManager @Inject constructor(
    private val tdlibParameters: TdApi.SetTdlibParameters
) {
    private var client: Client

    private val _authState = MutableStateFlow<TdApi.AuthorizationState?>(null)
    val authState = _authState.asStateFlow()

    private val managerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _chats = MutableStateFlow<Map<Long, TdApi.Chat>>(emptyMap())
    val chatsFlow: StateFlow<List<TdApi.Chat>> = _chats
        .map { map ->
            map.values.sortedByDescending { chat ->
                chat.positions.firstOrNull{ it.list is TdApi.ChatListMain }?.order ?: 0L
            }
        }
        .stateIn(
            managerScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    private val _fileUpdates = MutableSharedFlow<TdApi.UpdateFile>(extraBufferCapacity = 100)

    init {
        System.loadLibrary("tdjni")

        client = Client.create(
            { update -> // UpdateHandler
                when (update) {
                    is TdApi.UpdateAuthorizationState -> {
                        _authState.value = update.authorizationState
                        handleAuthUpdate(update.authorizationState)
                    }
                    is TdApi.UpdateNewChat -> {
                        Log.d("TDLib", "New chat received: ${update.chat.title}")
                        _chats.update { it + (update.chat.id to update.chat) }
                    }
                    is TdApi.UpdateChatPosition -> {
                        handleChatPositionUpdate(update)
                    }
                    is TdApi.UpdateFile -> {
                        _fileUpdates.tryEmit(update)
                    }
                }
            },
            { error -> // UpdateExceptionHandler
                Log.e("TDLib", "Error: ${error.message}")
            },
            { error -> // DefaultExceptionHandler
                Log.e("TDLib", "Default error: ${error.message}")
            }
        )
    }

    private fun handleAuthUpdate(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                send(tdlibParameters)
            }
            else -> {
                Log.d("TDLib", "New state: ${state.javaClass.simpleName}")
            }
        }
    }

    private fun handleChatPositionUpdate(update: TdApi.UpdateChatPosition) {
        _chats.update { current ->
            val chat = current[update.chatId]
            if (chat != null) {
                val oldPositions = chat.positions
                val mainIndex = oldPositions.indexOfFirst { it.list is TdApi.ChatListMain }
                if (mainIndex != -1) {
                    val newPositions = oldPositions.copyOf()
                    newPositions[mainIndex] = update.position
                    chat.positions = newPositions
                } else {
                    chat.positions = oldPositions + update.position
                }
                current + (update.chatId to chat)
            } else current
        }
    }

    private fun send(query: TdApi.Function<out TdApi.Object>, callback: Client.ResultHandler? = null) {
        client.send(query, callback ?: Client.ResultHandler { })
    }

    fun setPhoneNumber(phoneNumber: String) {
        // International format
        send(TdApi.SetAuthenticationPhoneNumber(phoneNumber, null)) { result ->
            if (result is TdApi.Error) {
                Log.e("TDLib", "Phone number error: ${result.message}")
            }
        }
    }

    fun checkCode(code: String) {
        // SMS or through Telegram
        send(TdApi.CheckAuthenticationCode(code)) { result ->
            if (result is TdApi.Error) {
                Log.e("TDLib", "Authentication code error: ${result.message}")
            }
        }
    }

    fun checkPassword(password: String) {
        send(TdApi.CheckAuthenticationPassword(password)) { result ->
            if (result is TdApi.Error) {
                Log.e("TDLib", "2FA error: ${result.message}")
                // TODO: show error in UI
            }
        }
    }

    fun loadChats() {
        send(TdApi.LoadChats(null, 50)) { result ->
            if (result is TdApi.Ok) {
                Log.e("TDLib", "Loading chats")
            } else if (result is TdApi.Error) {
                Log.e("TDLib", "Error loading chats: ${result.message}")
            }
        }
    }

    suspend fun isForum(chatId: Long): Boolean = suspendCancellableCoroutine { continuation ->
        send(TdApi.GetChat(chatId)) { chatResult ->
            if (!continuation.isActive) return@send

            if (chatResult is TdApi.Chat) {
                val type = chatResult.type
                if (type is TdApi.ChatTypeSupergroup) {
                    send(TdApi.GetSupergroup(type.supergroupId)) { supergroupResult ->
                        if (!continuation.isActive) return@send

                        if (supergroupResult is TdApi.Supergroup) {
                            continuation.resume(supergroupResult.isForum)
                        } else {
                            continuation.resume(false)
                        }
                    }
                } else {
                    continuation.resume(false)
                }
            } else {
                continuation.resume(false)
            }
        }
    }

    suspend fun getForumTopics(
        chatId: Long,
        query: String = "",
        offsetDate: Int = 0,
        offsetMessageId: Long = 0,
        offsetForumTopicId: Int = 0,
        limit: Int = 50
    ): TdApi.ForumTopics? = suspendCancellableCoroutine { continuation ->
        send(
            TdApi.GetForumTopics(
                chatId, query, offsetDate, offsetMessageId, offsetForumTopicId, limit
            )
        ) { result ->
            if (!continuation.isActive) return@send

            if (result is TdApi.ForumTopics) {
                continuation.resume(result)
            } else {
                continuation.resume(null)
            }
        }
    }

    suspend fun searchApkMessages(
        chatId: Long,
        topicId: Int = 0,
        limit: Int = 40
    ): TdApi.FoundChatMessages? = suspendCancellableCoroutine { continuation ->
        val topic: TdApi.MessageTopic? = if (topicId != 0) {
            TdApi.MessageTopicForum(topicId)
        } else null

        send(
            TdApi.SearchChatMessages(
                chatId,
                topic,
                "",
                null,
                0,
                0,
                limit,
                TdApi.SearchMessagesFilterDocument()
            )
        ) { result ->
            if (!continuation.isActive) return@send
            if (result is TdApi.FoundChatMessages) continuation.resume(result)
            else continuation.resume(null)
        }
    }

    suspend fun downloadFile(fileId: Int): String = suspendCancellableCoroutine { continuation ->
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        send(TdApi.GetFile(fileId)) { result ->
            if (result is TdApi.File) {
                if (result.local.isDownloadingCompleted) {
                    if (continuation.isActive) continuation.resume(result.local.path)
                    return@send
                }

                send(TdApi.DownloadFile(fileId, 32, 0, 0, true)) { dlResult ->
                    if (dlResult is TdApi.Error && continuation.isActive) {
                        continuation.resumeWithException(Exception(dlResult.message))
                    }
                }

                scope.launch {
                    _fileUpdates
                        .filter { it.file.id == fileId && it.file.local.isDownloadingCompleted }
                        .first()
                        .also { update ->
                            if (continuation.isActive) {
                                continuation.resume(update.file.local.path)
                                scope.cancel()
                            }
                        }
                }
            } else if (result is TdApi.Error && continuation.isActive) {
                continuation.resumeWithException(Exception(result.message))
            }
        }

        continuation.invokeOnCancellation {
            scope.cancel()
            TdApi.CancelDownloadFile(fileId, false)
        }
    }

    fun deleteFile(fileId: Int) {
        send(TdApi.DeleteFile(fileId)) { result ->
            if (result is TdApi.Ok) {
                Log.d("Cleanup", "File status has been reset")
            }
        }
    }
}