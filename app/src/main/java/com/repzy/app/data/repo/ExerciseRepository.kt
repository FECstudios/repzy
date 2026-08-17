package com.repzy.app.data.repo

import com.repzy.app.data.model.Exercise
import com.repzy.app.data.model.ExerciseAlternative
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Egzersiz kütüphanesi kullanıcıya ait değil ve nadiren değişir — tamamı bir kez
 * çekilip bellekte tutulur, filtre ve arama istemcide çalışır (anında tepki, sıfır gecikme).
 *
 * Kütüphane birkaç yüz hareketi geçtiğinde filtreleme sunucuya taşınmalı.
 */
@Singleton
class ExerciseRepository @Inject constructor(
    private val client: SupabaseClient,
) {
    private val mutex = Mutex()
    private var exercises: List<Exercise>? = null
    private var alternatives: List<ExerciseAlternative>? = null

    suspend fun all(): Result<List<Exercise>> = runCatching {
        mutex.withLock {
            exercises ?: client.from("exercises")
                .select { order("primary_muscle", Order.ASCENDING) }
                .decodeList<Exercise>()
                .also { exercises = it }
        }
    }

    suspend fun byId(id: String): Result<Exercise?> = all().map { list -> list.find { it.id == id } }

    /** Salon hareketinin ev karşılığı (ya da tersi). */
    suspend fun alternativesOf(id: String): Result<List<Exercise>> = runCatching {
        val links = mutex.withLock {
            alternatives ?: client.from("exercise_alternatives")
                .select()
                .decodeList<ExerciseAlternative>()
                .also { alternatives = it }
        }
        val targetIds = links.filter { it.exerciseId == id }.map { it.alternativeId }.toSet()
        all().getOrThrow().filter { it.id in targetIds }
    }
}
