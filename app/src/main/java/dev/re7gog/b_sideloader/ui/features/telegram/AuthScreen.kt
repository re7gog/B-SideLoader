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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
            is AuthStep.Loading -> Text("Initializing Telegram...")

            is AuthStep.PhoneInput -> {
                AuthInputSection(
                    title = "Your phone number",
                    label = "+79990000000",
                    value = inputFieldValue,
                    onValueChange = { inputFieldValue = it },
                    onConfirm = { viewModel.sendPhoneNumber(inputFieldValue); inputFieldValue = "" }
                )
            }

            is AuthStep.CodeInput -> {
                AuthInputSection(
                    title = "Confirmation code",
                    label = "12345",
                    value = inputFieldValue,
                    onValueChange = { inputFieldValue = it },
                    onConfirm = { viewModel.sendCode(inputFieldValue); inputFieldValue = "" }
                )
            }

            is AuthStep.PasswordInput -> {
                AuthInputSection(
                    title = "Cloud password (2FA)",
                    label = "Enter your password",
                    value = inputFieldValue,
                    onValueChange = { inputFieldValue = it },
                    onConfirm = { viewModel.sendPassword(inputFieldValue); inputFieldValue = "" },
                    isPassword = true // Добавь этот параметр в AuthInputSection для защиты текста
                )
            }

            is AuthStep.Error -> {
                Text("Error: ${step.message}", color = MaterialTheme.colorScheme.error)
                Button(onClick = { /* TODO: State reset */ }) {
                    Text("Try again")
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
    onConfirm: () -> Unit,
    isPassword: Boolean = false
) {
    Text(text = title, style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(16.dp))
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None
    )
    Spacer(modifier = Modifier.height(16.dp))
    Button(
        onClick = onConfirm,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Text("Continue")
    }
}