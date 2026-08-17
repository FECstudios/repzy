package com.repzy.app.data.repo

import com.repzy.app.data.model.Workout
import com.repzy.app.data.model.WorkoutSet
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutRepository @Inject constructor(
    private val client: SupabaseClient,
    private val auth: AuthRepository,
) {
    private fun requireUserId(): String =
        auth.currentUserId ?: error("Oturum yok — antrenman kaydı yapılamaz.")

    /** Bitmemiş antrenman varsa onu döner; uygulama yeniden açıldığında seans kaybolmaz. */
    suspend fun activeWorkout(): Result<Workout?> = runCatching {
        val uid = requireUserId()
        client.from("workouts")
            .select {
                filter {
                    eq("user_id", uid)
                    exact("finished_at", null)
                }
                order("started_at", Order.DESCENDING)
                limit(1)
            }
            .decodeSingleOrNull<Workout>()
    }

    suspend fun startWorkout(title: String?): Result<Workout> = runCatching {
        val uid = requireUserId()
        client.from("workouts")
            .insert(Workout(userId = uid, title = title)) { select() }
            .decodeSingle<Workout>()
    }

    suspend fun finishWorkout(workoutId: String, perceivedEffort: Int?): Result<Unit> = runCatching {
        client.from("workouts").update(
            {
                set("finished_at", nowIso())
                if (perceivedEffort != null) set("perceived_effort", perceivedEffort)
            },
        ) {
            filter { eq("id", workoutId) }
        }
    }

    /** Hiç set girilmediyse boş antrenman satırı bırakmamak için silinir. */
    suspend fun discardWorkout(workoutId: String): Result<Unit> = runCatching {
        client.from("workouts").delete { filter { eq("id", workoutId) } }
    }

    suspend fun setsOf(workoutId: String): Result<List<WorkoutSet>> = runCatching {
        client.from("workout_sets")
            .select {
                filter { eq("workout_id", workoutId) }
                order("completed_at", Order.ASCENDING)
            }
            .decodeList<WorkoutSet>()
    }

    suspend fun addSet(set: WorkoutSet): Result<WorkoutSet> = runCatching {
        val uid = requireUserId()
        client.from("workout_sets")
            .insert(set.copy(userId = uid)) { select() }
            .decodeSingle<WorkoutSet>()
    }

    suspend fun deleteSet(setId: String): Result<Unit> = runCatching {
        client.from("workout_sets").delete { filter { eq("id", setId) } }
    }

    suspend fun recentWorkouts(limit: Long = 20): Result<List<Workout>> = runCatching {
        val uid = requireUserId()
        client.from("workouts")
            .select {
                filter { eq("user_id", uid) }
                order("started_at", Order.DESCENDING)
                limit(limit)
            }
            .decodeList<Workout>()
            // Devam eden seans geçmiş listesinde görünmesin.
            .filter { !it.isActive }
    }

    /**
     * Bir egzersizin en son yapıldığı setler — progressive overload için referans.
     * "Geçen sefer 3×8 × 40 kg" bilgisi olmadan kullanıcı ne kadar artıracağını bilemez.
     */
    suspend fun lastPerformance(exerciseId: String, excludeWorkoutId: String?): Result<List<WorkoutSet>> =
        runCatching {
            val uid = requireUserId()
            val sets = client.from("workout_sets")
                .select {
                    filter {
                        eq("user_id", uid)
                        eq("exercise_id", exerciseId)
                        if (excludeWorkoutId != null) neq("workout_id", excludeWorkoutId)
                    }
                    order("completed_at", Order.DESCENDING)
                    limit(20)
                }
                .decodeList<WorkoutSet>()

            // En son antrenmandaki setleri al, set sırasına göre diz.
            val lastWorkoutId = sets.firstOrNull()?.workoutId ?: return@runCatching emptyList()
            sets.filter { it.workoutId == lastWorkoutId }.sortedBy { it.setIndex }
        }
}

private fun nowIso(): String = java.time.Instant.now().toString()
