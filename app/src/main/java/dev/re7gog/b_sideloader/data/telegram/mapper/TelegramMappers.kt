package dev.re7gog.b_sideloader.data.telegram.mapper

import dev.re7gog.b_sideloader.domain.model.TelegramApkDocument
import dev.re7gog.b_sideloader.domain.model.TelegramAuthState
import dev.re7gog.b_sideloader.domain.model.TelegramChatSummary
import dev.re7gog.b_sideloader.domain.model.TelegramCodeDelivery
import dev.re7gog.b_sideloader.domain.model.TelegramTopicSummary
import org.drinkless.tdlib.TdApi

/** TDLib types -> domain models. The only place outside `data/telegram` that knows `TdApi`. */

fun TdApi.AuthorizationState?.toAuthState(): TelegramAuthState = when (this) {
    null,
    is TdApi.AuthorizationStateWaitTdlibParameters,
    -> TelegramAuthState.Initialising

    is TdApi.AuthorizationStateWaitPhoneNumber -> TelegramAuthState.WaitingForPhoneNumber
    is TdApi.AuthorizationStateWaitEmailAddress -> TelegramAuthState.WaitingForEmailAddress
    is TdApi.AuthorizationStateWaitEmailCode -> TelegramAuthState.WaitingForEmailCode(codeInfo.emailAddressPattern)
    is TdApi.AuthorizationStateWaitCode -> TelegramAuthState.WaitingForCode(
        delivery = when (codeInfo.type) {
            is TdApi.AuthenticationCodeTypeTelegramMessage -> TelegramCodeDelivery.Telegram
            is TdApi.AuthenticationCodeTypeSms,
            is TdApi.AuthenticationCodeTypeSmsWord,
            is TdApi.AuthenticationCodeTypeSmsPhrase,
            -> TelegramCodeDelivery.Sms
            is TdApi.AuthenticationCodeTypeCall,
            is TdApi.AuthenticationCodeTypeFlashCall,
            is TdApi.AuthenticationCodeTypeMissedCall,
            -> TelegramCodeDelivery.Call
            is TdApi.AuthenticationCodeTypeFragment -> TelegramCodeDelivery.Fragment
            is TdApi.AuthenticationCodeTypeFirebaseAndroid,
            is TdApi.AuthenticationCodeTypeFirebaseIos,
            -> TelegramCodeDelivery.Firebase
            else -> TelegramCodeDelivery.Unknown
        },
        canResend = codeInfo.nextType != null,
        resendTimeoutSeconds = codeInfo.timeout.coerceAtLeast(0),
    )
    is TdApi.AuthorizationStateWaitPassword -> TelegramAuthState.WaitingForPassword
    is TdApi.AuthorizationStateReady -> TelegramAuthState.Ready

    is TdApi.AuthorizationStateLoggingOut,
    is TdApi.AuthorizationStateClosing,
    is TdApi.AuthorizationStateClosed,
    -> TelegramAuthState.LoggedOut

    // An unsupported step is not initialization: preserve its name so it can be diagnosed.
    else -> TelegramAuthState.Failed("Unsupported Telegram sign-in step: ${javaClass.simpleName}")
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
