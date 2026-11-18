package dev.re7gog.b_sideloader.data.telegram.mapper

import dev.re7gog.b_sideloader.domain.model.TelegramAuthState
import dev.re7gog.b_sideloader.domain.model.TelegramCodeDelivery
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Test

class TelegramAuthMapperTest {
    @Test
    fun `code delivery and resend restrictions survive mapping`() {
        val state = TdApi.AuthorizationStateWaitCode(
            TdApi.AuthenticationCodeInfo(
                "+123456789", TdApi.AuthenticationCodeTypeTelegramMessage(5),
                TdApi.AuthenticationCodeTypeSms(5), 30,
            ),
        )
        assertEquals(
            TelegramAuthState.WaitingForCode(TelegramCodeDelivery.Telegram, true, 30),
            state.toAuthState(),
        )
    }

    @Test
    fun `no next delivery method means resend is unavailable`() {
        val state = TdApi.AuthorizationStateWaitCode(
            TdApi.AuthenticationCodeInfo("+123456789", TdApi.AuthenticationCodeTypeSms(5), null, -1),
        )
        assertEquals(
            TelegramAuthState.WaitingForCode(TelegramCodeDelivery.Sms, false, 0),
            state.toAuthState(),
        )
    }

    @Test
    fun `firebase is not presented as a delivered SMS`() {
        val state = TdApi.AuthorizationStateWaitCode(
            TdApi.AuthenticationCodeInfo("+123456789", TdApi.AuthenticationCodeTypeFirebaseAndroid(), null, 0),
        )
        assertEquals(
            TelegramCodeDelivery.Firebase,
            (state.toAuthState() as TelegramAuthState.WaitingForCode).delivery,
        )
    }
}
