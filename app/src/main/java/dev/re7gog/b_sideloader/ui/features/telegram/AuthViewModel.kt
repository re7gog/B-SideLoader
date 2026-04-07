package dev.re7gog.b_sideloader.ui.features.telegram

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.re7gog.b_sideloader.data.telegram.TelegramManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val telegramManager: TelegramManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState = _uiState.asStateFlow()

    init {
        observeAuthState()
    }

    private fun observeAuthState() {
        viewModelScope.launch {
            telegramManager.authState.collect { state ->
                val newStep = when (state) {
                    is TdApi.AuthorizationStateWaitPhoneNumber -> AuthStep.PhoneInput
                    is TdApi.AuthorizationStateWaitCode -> AuthStep.CodeInput
                    is TdApi.AuthorizationStateWaitPassword -> AuthStep.PasswordInput
                    is TdApi.AuthorizationStateReady -> AuthStep.Ready
                    is TdApi.AuthorizationStateLoggingOut,
                    is TdApi.AuthorizationStateClosed -> AuthStep.PhoneInput
                    else -> AuthStep.Loading
                }
                _uiState.value = _uiState.value.copy(step = newStep, isLoading = false)
            }
        }
    }

    fun sendPhoneNumber(phone: String) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        telegramManager.setPhoneNumber(phone)
    }

    fun sendCode(code: String) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        telegramManager.checkCode(code)
    }

    fun sendPassword(password: String) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        telegramManager.checkPassword(password)
    }
}