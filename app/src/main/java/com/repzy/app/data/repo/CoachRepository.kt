package com.repzy.app.data.repo

import com.repzy.app.data.model.DailyBrief
import com.repzy.app.data.model.DailyBriefRequest
import com.repzy.app.data.model.DeviceActivity
import io.github.jan.supabase.SupabaseClient
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
    suspend fun dailyBrief(
        force: Boolean = false,
        locale: String = "tr",
        activity: DeviceActivity? = null,
    ): Result<DailyBrief> =
        runCatching {
            val text = client.invokeEdgeFunction(
                function = "daily-brief",
                body = DailyBriefRequest(force = force, locale = locale, activity = activity),
                json = json,
            )
            json.decodeFromString<DailyBrief>(text)
        }
}
