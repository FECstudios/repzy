package com.repzy.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Arayüz tercihleri. Katlama durumunu bellekte tutmak yetmezdi: kullanıcı kartı
 * kapatıp uygulamayı yeniden açtığında yine açık gelirdi.
 */
@Singleton
class UiPrefs @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val coachExpandedKey = booleanPreferencesKey("coach_card_expanded")

    /** Varsayılan açık: kullanıcı brief'i ilk kez görmeden kapatmış olmuyor. */
    val coachExpanded: Flow<Boolean> = dataStore.data.map { it[coachExpandedKey] ?: true }

    suspend fun isCoachExpanded(): Boolean = coachExpanded.first()

    suspend fun setCoachExpanded(expanded: Boolean) {
        dataStore.edit { it[coachExpandedKey] = expanded }
    }
}
