package com.repzy.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Aktif antrenman = `finished_at` null olan satır. Böylece uygulama kapanıp açılsa da
 * seans kaldığı yerden devam eder; setler girildiği anda sunucuya yazılır.
 */
@Serializable
data class Workout(
    val id: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("finished_at") val finishedAt: String? = null,
    val title: String? = null,
    val note: String? = null,
    @SerialName("perceived_effort") val perceivedEffort: Int? = null,
) {
    val isActive: Boolean get() = finishedAt == null
}

@Serializable
data class WorkoutSet(
    val id: String? = null,
    @SerialName("workout_id") val workoutId: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("exercise_id") val exerciseId: String,
    @SerialName("set_index") val setIndex: Int,
    val reps: Int? = null,
    @SerialName("weight_kg") val weightKg: Double? = null,
    @SerialName("duration_sec") val durationSec: Int? = null,
    @SerialName("distance_m") val distanceM: Int? = null,
    @SerialName("is_warmup") val isWarmup: Boolean = false,
    @SerialName("completed_at") val completedAt: String? = null,
) {
    /** Hacim = tekrar × ağırlık. İlerlemenin en pratik tek sayılı göstergesi. */
    val volumeKg: Double
        get() = (reps ?: 0) * (weightKg ?: 0.0)
}
