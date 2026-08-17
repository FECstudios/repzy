package com.repzy.app.data.repo

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val client: SupabaseClient,
) {
    val sessionStatus: Flow<SessionStatus> = client.auth.sessionStatus

    /**
     * Kayıtlı oturum diskten yüklenene kadar bekler. Bu beklenmezse soğuk açılışta
     * bir an "oturum yok" görülüp onboarding parlayabiliyor.
     */
    suspend fun awaitInitialization() = client.auth.awaitInitialization()

    val currentUser: UserInfo? get() = client.auth.currentUserOrNull()

    val currentUserId: String? get() = currentUser?.id

    /**
     * Supabase'de e-posta doğrulaması açıksa signUp oturum döndürmez —
     * kullanıcının maildeki linke tıklaması gerekir. [needsEmailConfirmation] bunu ayırt eder.
     */
    suspend fun signUp(email: String, password: String): Result<SignUpOutcome> = runCatching {
        client.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        if (client.auth.currentSessionOrNull() == null) {
            SignUpOutcome.NEEDS_EMAIL_CONFIRMATION
        } else {
            SignUpOutcome.SIGNED_IN
        }
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        client.auth.resetPasswordForEmail(email.trim())
    }

    suspend fun signOut(): Result<Unit> = runCatching { client.auth.signOut() }
}

enum class SignUpOutcome { SIGNED_IN, NEEDS_EMAIL_CONFIRMATION }
