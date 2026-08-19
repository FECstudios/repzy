package com.repzy.app.data.repo

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.repzy.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google ile giriş.
 *
 * Tarayıcı üzerinden OAuth yerine **Credential Manager** kullanıyoruz: kullanıcı
 * uygulamadan çıkmıyor, cihazdaki Google hesabını tek dokunuşla seçiyor.
 * Aldığımız kimlik token'ı Supabase'e veriliyor, oturumu Supabase açıyor.
 *
 * Kurulum (Google Cloud Console):
 *  1. OAuth istemcisi — **Web** tipi → client id `GOOGLE_WEB_CLIENT_ID` olarak
 *     `local.properties`'e yazılır ve Supabase panelinde Google sağlayıcısına girilir.
 *  2. OAuth istemcisi — **Android** tipi → paket adı + imza SHA-1 (debug ve release ayrı).
 *     Bu istemcinin id'si koda girmiyor; Google onu imzadan tanıyor.
 */
@Singleton
class GoogleAuthRepository @Inject constructor(
    private val client: SupabaseClient,
) {
    /** Web istemci kimliği tanımlı değilse butonu hiç göstermiyoruz. */
    val isConfigured: Boolean get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    suspend fun signIn(context: Context): Result<Unit> = runCatching {
        check(isConfigured) { "GOOGLE_WEB_CLIENT_ID tanımlı değil." }

        // Nonce: token'ın bu isteğe ait olduğunu doğrulamak için. Google ham nonce'un
        // SHA-256'sını bekliyor, biz de token'ı Supabase'e verirken ham hâlini yolluyoruz.
        val rawNonce = UUID.randomUUID().toString()
        val hashedNonce = MessageDigest.getInstance("SHA-256")
            .digest(rawNonce.toByteArray())
            .joinToString("") { "%02x".format(it) }

        val option = GetGoogleIdOption.Builder()
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            // false: cihazda daha önce Repzy'ye bağlanmamış hesaplar da listelensin.
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .setNonce(hashedNonce)
            .build()

        val response = CredentialManager.create(context).getCredential(
            context = context,
            request = GetCredentialRequest.Builder().addCredentialOption(option).build(),
        )

        val credential = response.credential
        check(
            credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
        ) { "Beklenmeyen kimlik türü döndü." }

        val googleToken = GoogleIdTokenCredential.createFrom(credential.data)

        client.auth.signInWith(IDToken) {
            idToken = googleToken.idToken
            provider = Google
            nonce = rawNonce
        }
    }
}
