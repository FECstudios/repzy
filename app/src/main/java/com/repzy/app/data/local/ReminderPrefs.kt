package com.repzy.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.repzy.app.notifications.Reminders
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Hatırlatıcı tercihleri. Bildirim planlaması cihazda, sunucuya gitmiyor. */
data class ReminderSettings(
    val water: Boolean = false,
    val workout: Boolean = false,
    val workoutHour: Int = Reminders.DEFAULT_WORKOUT_HOUR,
    val plan: Boolean = false,
    val planHour: Int = Reminders.DEFAULT_PLAN_HOUR,
)

@Singleton
class ReminderPrefs @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val waterKey = booleanPreferencesKey("reminder_water")
    private val workoutKey = booleanPreferencesKey("reminder_workout")
    private val workoutHourKey = intPreferencesKey("reminder_workout_hour")
    private val planKey = booleanPreferencesKey("reminder_plan")
    private val planHourKey = intPreferencesKey("reminder_plan_hour")

    val settings: Flow<ReminderSettings> = dataStore.data.map { prefs ->
        ReminderSettings(
            water = prefs[waterKey] == true,
            workout = prefs[workoutKey] == true,
            workoutHour = prefs[workoutHourKey] ?: Reminders.DEFAULT_WORKOUT_HOUR,
            plan = prefs[planKey] == true,
            planHour = prefs[planHourKey] ?: Reminders.DEFAULT_PLAN_HOUR,
        )
    }

    suspend fun load(): ReminderSettings = settings.first()

    suspend fun setWater(enabled: Boolean) {
        dataStore.edit { it[waterKey] = enabled }
    }

    suspend fun setWorkout(enabled: Boolean) {
        dataStore.edit { it[workoutKey] = enabled }
    }

    suspend fun setPlan(enabled: Boolean) {
        dataStore.edit { it[planKey] = enabled }
    }

    suspend fun setWorkoutHour(hour: Int) {
        dataStore.edit { it[workoutHourKey] = hour.coerceIn(5, 23) }
    }
}
