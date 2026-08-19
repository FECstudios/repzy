package com.repzy.app.data.repo

import com.repzy.app.data.model.DayNutrition
import com.repzy.app.data.model.EdgeFunctionError
import com.repzy.app.data.model.FoodLog
import com.repzy.app.data.model.MealAnalysis
import com.repzy.app.data.model.MealAnalysisRequest
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MealRepository @Inject constructor(
    private val client: SupabaseClient,
    private val auth: AuthRepository,
    private val json: Json,
) {
    private fun requireUserId(): String =
        auth.currentUserId ?: error("Oturum yok — kayıt yapılamaz.")

    /**
     * Fotoğrafı Edge Function'a gönderir. Key uygulamada olmadığı için tahmin
     * her zaman sunucu üzerinden geçer; limit de orada sayılır.
     */
    suspend fun analyzePhoto(
        imageBase64: String,
        mimeType: String = "image/jpeg",
        locale: String = "tr",
    ): Result<MealAnalysis> = runCatching {
        val text = client.invokeEdgeFunction(
            function = "analyze-meal",
            body = MealAnalysisRequest(imageBase64, mimeType, locale),
            json = json,
        )
        json.decodeFromString<MealAnalysis>(text)
    }

    suspend fun logsOf(date: LocalDate): Result<DayNutrition> = runCatching {
        val uid = requireUserId()
        val logs = client.from("food_logs")
            .select {
                filter {
                    eq("user_id", uid)
                    eq("log_date", date.toString())
                }
                order("logged_at", Order.ASCENDING)
            }
            .decodeList<FoodLog>()
        DayNutrition(logs)
    }

    /**
     * Son [days] günün günlük kalori toplamı. Tek sorgu + istemcide gruplama:
     * gün başına ayrı sorgu atmak 7 ağ turu demek olurdu.
     */
    suspend fun caloriesByDay(today: LocalDate, days: Int = 7): Result<Map<LocalDate, Int>> =
        runCatching {
            val uid = requireUserId()
            val from = LocalDate.fromEpochDays(today.toEpochDays() - (days - 1))

            client.from("food_logs")
                .select {
                    filter {
                        eq("user_id", uid)
                        gte("log_date", from.toString())
                    }
                }
                .decodeList<FoodLog>()
                .filter { it.logDate != null }
                .groupBy { it.logDate!! }
                .mapValues { (_, logs) -> logs.sumOf { it.calories }.toInt() }
        }

    /** Tahmin onaylandıktan sonra kalemleri tek tek yazar — kullanıcı sonra birini silebilir. */
    suspend fun addLogs(logs: List<FoodLog>): Result<Unit> = runCatching {
        if (logs.isEmpty()) return@runCatching
        val uid = requireUserId()
        client.from("food_logs").insert(logs.map { it.copy(userId = uid) })
    }

    suspend fun deleteLog(id: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        client.from("food_logs").delete {
            filter {
                eq("id", id)
                eq("user_id", uid)
            }
        }
    }
}
