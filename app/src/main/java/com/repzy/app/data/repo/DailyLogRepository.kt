package com.repzy.app.data.repo

import com.repzy.app.data.model.WaterLog
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.datetime.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Günlük kayıtlar: su, (sonra) yemek ve antrenman. Streak sunucuda hesaplanır. */
@Singleton
class DailyLogRepository @Inject constructor(
    private val client: SupabaseClient,
    private val auth: AuthRepository,
) {
    private fun requireUserId(): String =
        auth.currentUserId ?: error("Oturum yok — kayıt yapılamaz.")

    suspend fun waterTotalMl(date: LocalDate): Result<Int> = runCatching {
        val uid = requireUserId()
        client.from("water_logs")
            .select {
                filter {
                    eq("user_id", uid)
                    eq("log_date", date.toString())
                }
            }
            .decodeList<WaterLog>()
            .sumOf { it.amountMl }
    }

    suspend fun addWater(amountMl: Int, date: LocalDate): Result<Unit> = runCatching {
        val uid = requireUserId()
        client.from("water_logs").insert(
            WaterLog(userId = uid, logDate = date, amountMl = amountMl),
        )
    }

    /** Yanlış basmayı geri almak için: o günün en son kaydını siler. */
    suspend fun removeLastWater(date: LocalDate): Result<Boolean> = runCatching {
        val uid = requireUserId()
        val last = client.from("water_logs")
            .select {
                filter {
                    eq("user_id", uid)
                    eq("log_date", date.toString())
                }
                order("logged_at", Order.DESCENDING)
                limit(1)
            }
            .decodeSingleOrNull<WaterLog>()
            ?: return@runCatching false

        client.from("water_logs").delete {
            filter { eq("id", last.id!!) }
        }
        true
    }

    suspend fun currentStreak(): Result<Int> = runCatching {
        client.postgrest.rpc("current_streak").decodeAs<Int>()
    }
}
