package dev.re7gog.b_sideloader.data.telegram.mapper

import dev.re7gog.b_sideloader.domain.model.TelegramApkDocument
import dev.re7gog.b_sideloader.domain.model.TelegramAuthState
import dev.re7gog.b_sideloader.domain.model.TelegramChatSummary
import dev.re7gog.b_sideloader.domain.model.TelegramTopicSummary
import org.drinkless.tdlib.TdApi

/** TDLib types -> domain models. The only place outside `data/telegram` that knows `TdApi`. */

fun TdApi.AuthorizationState?.toAuthState(): TelegramAuthState = when (this) {
    null,
    is TdApi.AuthorizationStateWaitTdlibParameters,
    -> TelegramAuthState.Initialising

    is TdApi.AuthorizationStateWaitPhoneNumber -> TelegramAuthState.WaitingForPhoneNumber
    is TdApi.AuthorizationStateWaitCode -> TelegramAuthState.WaitingForCode
    is TdApi.AuthorizationStateWaitPassword -> TelegramAuthState.WaitingForPassword
    is TdApi.AuthorizationStateReady -> TelegramAuthState.Ready

    is TdApi.AuthorizationStateLoggingOut,
    is TdApi.AuthorizationStateClosing,
    is TdApi.AuthorizationStateClosed,
    -> TelegramAuthState.LoggedOut

    // Covers the states the login flow has nothing to show for (e.g. e-mail confirmation),
    // which then render as the neutral loading step rather than crashing an exhaustive `when`.
    else -> TelegramAuthState.Initialising
}

fun TdApi.Chat.toDomain(isForum: Boolean, username: String? = null): TelegramChatSummary =
    TelegramChatSummary(
        id = id,
        title = title.orEmpty(),
        username = username,
        photoFileId = photo?.small?.id,
        isForum = isForum,
    )

fun TdApi.ForumTopic.toDomain(): TelegramTopicSummary = TelegramTopicSummary(
    id = info.forumTopicId,
    name = info.name.orEmpty(),
    iconColor = info.icon?.color ?: 0,
)

/**
 * Keeps only the messages that carry a document, preserving TDLib's newest-first order and the
 * `mediaAlbumId` grouping that lets the selector pair a file with the caption posted beside it.
 */
fun Array<TdApi.Message>.toApkDocuments(): List<TelegramApkDocument> = mapNotNull { message ->
    val content = message.content as? TdApi.MessageDocument ?: return@mapNotNull null
    val document = content.document
    TelegramApkDocument(
        messageId = message.id,
        fileId = document.document.id,
        fileName = document.fileName.orEmpty(),
        sizeBytes = document.document.size,
        caption = content.caption?.text.orEmpty(),
        albumId = message.mediaAlbumId,
    )
}
