package com.repzy.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnboardingDraftStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) {
    private val draftKey = stringPreferencesKey("onboarding_draft")

    /**
     * Sunucudaki profilin tamamlandığını bir kez gördükten sonra cihazda işaretliyoruz.
     * Böylece dönen kullanıcı, açılışta `profiles` sorgusunun cevabı beklenmeden
     * doğrudan Home'a giriyor — o sorgu kritik yolda tam bir ağ turu tutuyordu.
     */
    private val profileReadyKey = booleanPreferencesKey("profile_ready")

    val draft: Flow<OnboardingDraft?> = dataStore.data.map { prefs ->
        prefs[draftKey]?.let { raw ->
            // Şema değişip eski taslak okunamazsa kullanıcıyı hataya düşürmek yerine sıfırdan başlat.
            runCatching { json.decodeFromString<OnboardingDraft>(raw) }.getOrNull()
        }
    }

    val profileReady: Flow<Boolean> = dataStore.data.map { it[profileReadyKey] == true }

    suspend fun load(): OnboardingDraft? = draft.first()

    suspend fun isProfileReady(): Boolean = profileReady.first()

    /** Onboarding paywall'ı bir kez gösterilir; kullanıcı kapattıysa tekrar açılmaz. */
    private val paywallSeenKey = booleanPreferencesKey("paywall_seen")

    suspend fun isPaywallSeen(): Boolean = dataStore.data.map { it[paywallSeenKey] == true }.first()

    suspend fun setPaywallSeen() {
        dataStore.edit { it[paywallSeenKey] = true }
    }

    suspend fun setProfileReady(ready: Boolean) {
        dataStore.edit { prefs ->
            if (ready) prefs[profileReadyKey] = true else prefs.remove(profileReadyKey)
        }
    }

    suspend fun save(draft: OnboardingDraft) {
        val encoded = json.encodeToString(draft)
        dataStore.edit { it[draftKey] = encoded }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(draftKey) }
    }
}
