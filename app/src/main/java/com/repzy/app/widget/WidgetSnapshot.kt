package com.repzy.app.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Widget'ın gösterdiği veri. Widget doğrudan Supabase'e gitmiyor:
 * ana ekran güncellemeleri sık ve arka planda oluyor, her birinde ağ isteği
 * yapmak hem pil hem oturum yenileme açısından sorunlu. Uygulama her yüklemede
 * bu anlık görüntüyü yazıyor, widget onu okuyor.
 *
 * Sonuç: widget uygulama açıldığında güncelleniyor — kabul edilen sınır.
 */
data class WidgetData(
    val name: String = "",
    val waterMl: Int = 0,
    val waterTargetMl: Int = 0,
    val calorieTarget: Int = 0,
    val caloriesEaten: Int = 0,
    val streakDays: Int = 0,
) {
    val waterProgress: Float
        get() = if (waterTargetMl <= 0) 0f else (waterMl.toFloat() / waterTargetMl).coerceIn(0f, 1f)

    val caloriesLeft: Int get() = (calorieTarget - caloriesEaten).coerceAtLeast(0)
}

@Singleton
class WidgetSnapshotStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val name = stringPreferencesKey("widget_name")
    private val water = intPreferencesKey("widget_water_ml")
    private val waterTarget = intPreferencesKey("widget_water_target_ml")
    private val calorieTarget = intPreferencesKey("widget_calorie_target")
    private val caloriesEaten = intPreferencesKey("widget_calories_eaten")
    private val streak = intPreferencesKey("widget_streak")

    suspend fun read(): WidgetData {
        val prefs = dataStore.data.first()
        return WidgetData(
            name = prefs[name].orEmpty(),
            waterMl = prefs[water] ?: 0,
            waterTargetMl = prefs[waterTarget] ?: 0,
            calorieTarget = prefs[calorieTarget] ?: 0,
            caloriesEaten = prefs[caloriesEaten] ?: 0,
            streakDays = prefs[streak] ?: 0,
        )
    }

    suspend fun write(data: WidgetData) {
        dataStore.edit { prefs ->
            prefs[name] = data.name
            prefs[water] = data.waterMl
            prefs[waterTarget] = data.waterTargetMl
            prefs[calorieTarget] = data.calorieTarget
            prefs[caloriesEaten] = data.caloriesEaten
            prefs[streak] = data.streakDays
        }
    }

    /** Çıkışta widget'ta başkasının verisi kalmasın. */
    suspend fun clear() = write(WidgetData())
}

/** Hilt'e bağlı olmayan yerlerden (Glance) store'a erişim. */
suspend fun readWidgetData(context: Context): WidgetData =
    WidgetEntryPoint.resolve(context).widgetSnapshotStore().read()
