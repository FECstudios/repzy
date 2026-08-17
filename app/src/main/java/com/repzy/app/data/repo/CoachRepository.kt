package com.repzy.app.data.repo

import com.repzy.app.data.model.DailyBrief
import com.repzy.app.data.model.DailyBriefRequest
import com.repzy.app.data.model.EdgeFunctionError
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Günlük koç brief'i. Üretim ve limit sunucuda; burada sadece çağrı var. */
@Singleton
class CoachRepository @Inject constructor(
    private val client: SupabaseClient,
    private val json: Json,
) {
    /**
     * [force] false ise sunucu o günün kayıtlı brief'ini AI'ya gitmeden döndürür.
     * Yenileme hakkı bittiyse hata değil, kayıtlı brief + `limitReached` döner.
     */
    suspend fun dailyBrief(force: Boolean = false, locale: String = "tr"): Result<DailyBrief> =
        runCatching {
            val response = client.functions.invoke(
                function = "daily-brief",
                body = DailyBriefRequest(force = force, locale = locale),
            )

            val text = response.bodyAsText()
            if (response.status != HttpStatusCode.OK) {
                val error = runCatching {
                    json.decodeFromString<EdgeFunctionError>(text)
                }.getOrNull()

                throw when (error?.error) {
                    "daily_limit_reached" -> AiFailure.DailyLimitReached(
                        limit = error.limit ?: 0,
                        used = error.used ?: 0,
                    )
                    "ai_quota_exhausted" -> AiFailure.ProviderQuotaExhausted
                    else -> AiFailure.Other(error?.error ?: response.status.value.toString())
                }
            }

            json.decodeFromString<DailyBrief>(text)
        }
}
