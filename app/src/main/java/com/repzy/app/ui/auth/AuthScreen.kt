package com.repzy.app.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repzy.app.R

/**
 * @param pendingPlan Onboarding bitmiş ve plan cihazda bekliyorsa true —
 * ekran "boş kayıt formu" değil, "planını kaydet" olarak çerçevelenir.
 * @param onCancel null değilse ekran onboarding'den "hesabım var" denerek açılmış,
 * geri dönüş mümkün.
 */
@Composable
fun AuthScreen(
    pendingPlan: Boolean = false,
    onCancel: (() -> Unit)? = null,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    // "Hesabım var" diyerek gelindiyse kayıt değil giriş formu açılır.
    LaunchedEffect(onCancel) {
        if (onCancel != null) viewModel.setMode(AuthMode.SIGN_IN)
    }
    if (onCancel != null) {
        BackHandler(onBack = onCancel)
    }

    Scaffold(
        topBar = {
            if (onCancel != null) {
                Row {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                }
            }
        },
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            Text(
                text = if (pendingPlan) {
                    stringResource(R.string.auth_save_plan_title)
                } else {
                    stringResource(R.string.app_name)
                },
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (pendingPlan) {
                    stringResource(R.string.auth_save_plan_body)
                } else {
                    stringResource(R.string.auth_tagline)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(40.dp))

            OutlinedTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = { Text(stringResource(R.string.auth_email)) },
                singleLine = true,
                isError = state.email.isNotEmpty() && !state.emailLooksValid,
                supportingText = {
                    if (state.email.isNotEmpty() && !state.emailLooksValid) {
                        Text(stringResource(R.string.auth_email_invalid))
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text(stringResource(R.string.auth_password)) },
                singleLine = true,
                isError = state.password.isNotEmpty() && !state.passwordLongEnough,
                supportingText = {
                    if (state.password.isNotEmpty() && !state.passwordLongEnough) {
                        Text(stringResource(R.string.auth_password_too_short))
                    }
                },
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                            contentDescription = null,
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            state.infoMessage?.let { key ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (key == AuthViewModel.NEEDS_CONFIRMATION) {
                        stringResource(R.string.auth_confirm_email)
                    } else {
                        key
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
            }

            state.errorMessage?.let { message ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = viewModel::submit,
                enabled = state.canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        stringResource(
                            when (state.mode) {
                                AuthMode.SIGN_IN -> R.string.auth_sign_in
                                AuthMode.SIGN_UP -> R.string.auth_sign_up
                            },
                        ),
                    )
                }
            }

            TextButton(onClick = viewModel::toggleMode) {
                Text(
                    stringResource(
                        when (state.mode) {
                            AuthMode.SIGN_IN -> R.string.auth_to_sign_up
                            AuthMode.SIGN_UP -> R.string.auth_to_sign_in
                        },
                    ),
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.disclaimer_short),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
