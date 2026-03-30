package dev.re7gog.b_sideloader.ui.features.telegram

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputFieldValue by remember { mutableStateOf("") }

    LaunchedEffect(uiState.step) {
        if (uiState.step is AuthStep.Ready) {
            onAuthSuccess()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
        }

        when (val step = uiState.step) {
            is AuthStep.Loading -> Text("Инициализация Telegram...")

            is AuthStep.PhoneInput -> {
                AuthInputSection(
                    title = "Ваш номер телефона",
                    label = "+7 999 000 00 00",
                    value = inputFieldValue,
                    onValueChange = { inputFieldValue = it },
                    onConfirm = { viewModel.sendPhoneNumber(inputFieldValue); inputFieldValue = "" }
                )
            }

            is AuthStep.CodeInput -> {
                AuthInputSection(
                    title = "Код подтверждения",
                    label = "12345",
                    value = inputFieldValue,
                    onValueChange = { inputFieldValue = it },
                    onConfirm = { viewModel.sendCode(inputFieldValue); inputFieldValue = "" }
                )
            }

            is AuthStep.Error -> {
                Text("Ошибка: ${step.message}", color = MaterialTheme.colorScheme.error)
                Button(onClick = { /* Можно добавить сброс состояния */ }) {
                    Text("Попробовать снова")
                }
            }
            else -> {}
        }
    }
}

@Composable
fun AuthInputSection(
    title: String,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit
) {
    Text(text = title, style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(16.dp))
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Spacer(modifier = Modifier.height(16.dp))
    Button(
        onClick = onConfirm,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Text("Продолжить")
    }
}