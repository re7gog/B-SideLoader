package dev.re7gog.b_sideloader.ui.feature.telegramlogin

import dev.re7gog.b_sideloader.domain.model.TelegramAuthState
import dev.re7gog.b_sideloader.testing.FakeTelegramRepository
import dev.re7gog.b_sideloader.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TelegramLoginViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `resend waits for the timeout and follows a replacement timeout`() = runTest {
        val repository = FakeTelegramRepository().apply {
            setAuthState(TelegramAuthState.WaitingForCode(canResend = true, resendTimeoutSeconds = 2))
        }
        val vm = TelegramLoginViewModel(repository)
        runCurrent()
        vm.resendCode()
        runCurrent()
        assertEquals(0, repository.resendRequests)
        advanceTimeBy(2_000)
        runCurrent()
        vm.resendCode()
        runCurrent()
        assertEquals(1, repository.resendRequests)

        repository.setAuthState(TelegramAuthState.WaitingForCode(canResend = true, resendTimeoutSeconds = 3))
        runCurrent()
        vm.resendCode()
        runCurrent()
        assertEquals(1, repository.resendRequests)
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(0, (vm.uiState.value.step as AuthStep.Code).resendSeconds)
    }

    @Test
    fun `leaving code entry cancels its countdown`() = runTest {
        val repository = FakeTelegramRepository().apply {
            setAuthState(TelegramAuthState.WaitingForCode(canResend = true, resendTimeoutSeconds = 2))
        }
        val vm = TelegramLoginViewModel(repository)
        runCurrent()
        repository.setAuthState(TelegramAuthState.WaitingForPassword)
        runCurrent()
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(AuthStep.Password, vm.uiState.value.step)
        vm.resendCode()
        assertEquals(0, repository.resendRequests)
    }
}
