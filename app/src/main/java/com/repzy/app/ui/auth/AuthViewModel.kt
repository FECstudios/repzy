package com.repzy.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repzy.app.data.repo.AuthRepository
import com.repzy.app.data.repo.SignUpOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuthMode { SIGN_IN, SIGN_UP }

data class AuthUiState(
    val mode: AuthMode = AuthMode.SIGN_UP,
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
) {
    val emailLooksValid: Boolean =
        email.contains('@') && email.substringAfterLast('@').contains('.') && !email.contains(' ')
    val passwordLongEnough: Boolean = password.length >= 8
    val canSubmit: Boolean = emailLooksValid && passwordLongEnough && !isSubmitting
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) =
        _state.update { it.copy(email = value, errorMessage = null) }

    fun onPasswordChange(value: String) =
        _state.update { it.copy(password = value, errorMessage = null) }

    /** Onboarding'i atlayıp "hesabım var" diyen kullanıcı doğrudan giriş modunda açılır. */
    fun setMode(mode: AuthMode) = _state.update {
        if (it.mode == mode) it else it.copy(mode = mode, errorMessage = null, infoMessage = null)
    }

    fun toggleMode() = _state.update {
        it.copy(
            mode = if (it.mode == AuthMode.SIGN_IN) AuthMode.SIGN_UP else AuthMode.SIGN_IN,
            errorMessage = null,
            infoMessage = null,
        )
    }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(isSubmitting = true, errorMessage = null, infoMessage = null) }

        viewModelScope.launch {
            when (current.mode) {
                AuthMode.SIGN_UP -> authRepository.signUp(current.email, current.password)
                    .onSuccess { outcome ->
                        _state.update {
                            it.copy(
                                isSubmitting = false,
                                // Oturum açıldıysa nav zaten devralır; açılmadıysa kullanıcıyı bilgilendir.
                                infoMessage = if (outcome == SignUpOutcome.NEEDS_EMAIL_CONFIRMATION) {
                                    NEEDS_CONFIRMATION
                                } else {
                                    null
                                },
                                mode = if (outcome == SignUpOutcome.NEEDS_EMAIL_CONFIRMATION) {
                                    AuthMode.SIGN_IN
                                } else {
                                    it.mode
                                },
                            )
                        }
                    }
                    .onFailure { e -> fail(e) }

                AuthMode.SIGN_IN -> authRepository.signIn(current.email, current.password)
                    .onSuccess { _state.update { it.copy(isSubmitting = false) } }
                    .onFailure { e -> fail(e) }
            }
        }
    }

    private fun fail(e: Throwable) = _state.update {
        it.copy(isSubmitting = false, errorMessage = e.message ?: "Bilinmeyen bir hata oluştu.")
    }

    fun consumeInfoMessage() = _state.update { it.copy(infoMessage = null) }

    companion object {
        /** Ekran tarafında string resource'a çevrilir. */
        const val NEEDS_CONFIRMATION = "needs_email_confirmation"
    }
}
