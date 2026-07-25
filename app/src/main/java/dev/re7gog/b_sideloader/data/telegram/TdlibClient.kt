package dev.re7gog.b_sideloader.data.telegram

import dev.re7gog.b_sideloader.core.log.Logger
import dev.re7gog.b_sideloader.data.di.ApplicationScope
import dev.re7gog.b_sideloader.domain.error.AppError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Coroutine-shaped wrapper around TDLib's callback API.
 *
 * TDLib is a single native client that answers every request on its own thread. This class owns
 * that client and turns its two shapes — request/response and a firehose of updates — into
 * `suspend` functions and `Flow`s. It knows nothing about apps or APKs; [TelegramRepositoryImpl]
 * builds domain meaning on top.
 *
 * Errors: [request] throws [AppError.Telegram] instead of returning a `TdApi.Error` object, so a
 * caller cannot forget to check and silently treat an error as "no results".
 */
@Singleton
class TdlibClient @Inject constructor(
    private val parameters: TdApi.SetTdlibParameters,
    @ApplicationScope private val scope: CoroutineScope,
    private val logger: Logger,
) {
    private val _authorizationState = MutableStateFlow<TdApi.AuthorizationState?>(null)
    val authorizationState: StateFlow<TdApi.AuthorizationState?> = _authorizationState.asStateFlow()

    /** Chats seen via `UpdateNewChat`, used to resolve ids returned by server-side search. */
    private val _chats = MutableStateFlow<Map<Long, TdApi.Chat>>(emptyMap())

    private val _fileUpdates = MutableSharedFlow<TdApi.File>(extraBufferCapacity = FILE_BUFFER)
    val fileUpdates: SharedFlow<TdApi.File> = _fileUpdates.asSharedFlow()

    private val _authErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val authErrors: SharedFlow<String> = _authErrors.asSharedFlow()

    private val client: Client by lazy { createClient() }

    /** Forces creation of the native client. Called once at startup. */
    fun start() {
        client
    }

    private fun createClient(): Client {
        System.loadLibrary(NATIVE_LIBRARY)
        return Client.create(
            { update -> onUpdate(update) },
            { error -> logger.e(TAG, error) { "TDLib update handler failed" } },
            { error -> logger.e(TAG, error) { "TDLib default handler failed" } },
        )
    }

    private fun onUpdate(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateAuthorizationState -> {
                _authorizationState.value = update.authorizationState
                if (update.authorizationState is TdApi.AuthorizationStateWaitTdlibParameters) {
                    // The only request TDLib accepts in this state; everything else is queued
                    // by TDLib until it has been answered.
                    fireAndForget(parameters)
                }
            }

            is TdApi.UpdateNewChat -> _chats.update { it + (update.chat.id to update.chat) }

            is TdApi.UpdateChatPosition -> updateChatPosition(update)

            is TdApi.UpdateFile -> _fileUpdates.tryEmit(update.file)

            else -> Unit
        }
    }

    /**
     * Replaces the chat's main-list position.
     *
     * `TdApi.Chat` is mutable and shared with TDLib, so the positions array is copied rather than
     * edited in place — mutating it directly races with the native thread that may be reading it.
     */
    private fun updateChatPosition(update: TdApi.UpdateChatPosition) {
        _chats.update { chats ->
            val chat = chats[update.chatId] ?: return@update chats
            val existing: Array<TdApi.ChatPosition> = chat.positions ?: emptyArray()
            val index = existing.indexOfFirst { it.list is TdApi.ChatListMain }
            chat.positions = if (index >= 0) {
                existing.copyOf().also { it[index] = update.position }
            } else {
                existing + update.position
            }
            chats + (update.chatId to chat)
        }
    }

    /** The chat cache, for resolving ids that a search returned. */
    fun cachedChat(chatId: Long): TdApi.Chat? = _chats.value[chatId]

    /** Sends a request and ignores the answer. For fire-and-forget calls like `SetTdlibParameters`. */
    fun fireAndForget(query: TdApi.Function<out TdApi.Object>) {
        client.send(query) { result ->
            if (result is TdApi.Error) {
                logger.w(TAG) { "${query::class.java.simpleName} failed: ${result.message}" }
            }
        }
    }

    /**
     * Sends a request and suspends until TDLib answers.
     *
     * @throws AppError.Telegram when TDLib returns an error.
     */
    suspend fun request(query: TdApi.Function<out TdApi.Object>): TdApi.Object {
        val result: TdApi.Object = suspendCancellableCoroutine { continuation ->
            client.send(query) { answer ->
                if (continuation.isActive) continuation.resume(answer)
            }
        }
        if (result is TdApi.Error) throw AppError.Telegram(result.code, result.message)
        return result
    }

    /**
     * Like [request] but returns `null` on error and for an unexpected response type, for the
     * "absence is a normal answer" cases (an unknown chat, a chat without topics).
     */
    suspend inline fun <reified T : TdApi.Object> requestOrNull(
        query: TdApi.Function<out TdApi.Object>,
    ): T? = try {
        request(query) as? T
    } catch (_: AppError.Telegram) {
        null
    }

    /**
     * Reports auth failures (wrong code, wrong password) to the login screen. These are not
     * exceptions — the user simply has to try again — so they travel as a separate stream.
     */
    fun sendAuthRequest(query: TdApi.Function<out TdApi.Object>) {
        client.send(query) { result ->
            if (result is TdApi.Error) {
                logger.w(TAG) { "Auth step failed: ${result.message}" }
                _authErrors.tryEmit(result.message)
            }
        }
    }

    private companion object {
        const val TAG = "TDLib"
        const val NATIVE_LIBRARY = "tdjni"
        const val FILE_BUFFER = 100
    }
}
