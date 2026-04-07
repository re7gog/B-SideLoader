package dev.re7gog.b_sideloader.data.telegram

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelegramManager @Inject constructor(
    private val tdlibParameters: TdApi.SetTdlibParameters
) {
    private var client: Client

    private val _authState = MutableStateFlow<TdApi.AuthorizationState?>(null)
    val authState = _authState.asStateFlow()

    init {
        System.loadLibrary("tdjni")

        client = Client.create(
            { update -> // UpdateHandler
                if (update is TdApi.UpdateAuthorizationState) {
                    _authState.value = update.authorizationState
                    handleAuthUpdate(update.authorizationState)
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
}