package com.repzy.app.data.repo

import com.repzy.app.data.model.EdgeFunctionError
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json

/**
 * Edge Function çağrısı + hata çevirisi, tek yerde.
 *
 * Neden gerekti: supabase-kt 3.7'de `functions.invoke` non-2xx durumda kendi
 * istisnasını fırlatıyor — çağıranın `response.status != OK` dalına hiç
 * girilmiyordu. Sonuç: Ktor'un istisna metni (gövde + `URL:` + `Headers:
 * {Authorization=[Bearer ...], apikey=[...]}`) doğrudan arayüze basılıyordu.
 * Cihazda 18 Ağu 2026'da görüldü.
 *
 * Artık hangi yoldan gelirse gelsin gövde ayrıştırılıp [AiFailure]'a çevriliyor;
 * ham istek dökümü hiçbir zaman kullanıcıya ulaşmıyor.
 */
suspend fun SupabaseClient.invokeEdgeFunction(
    function: String,
    body: Any,
    json: Json,
): String {
    val response: HttpResponse = try {
        functions.invoke(function = function, body = body)
    } catch (e: Exception) {
        // İstisnanın metninde gövde olabilir; sadece bilinen hata kodlarını
        // arıyoruz. Bulamazsak genel bir hata veriyoruz — mesajı ASLA
        // olduğu gibi yukarı taşımıyoruz.
        throw e.asAiFailure(json)
    }

    val text = response.bodyAsText()
    if (response.status != HttpStatusCode.OK) {
        throw text.asAiFailure(json, response.status.value.toString())
    }
    return text
}

/** Gövde metnini bilinen hata koduna çevirir. */
private fun String.asAiFailure(json: Json, fallbackCode: String): AiFailure {
    val error = runCatching { json.decodeFromString<EdgeFunctionError>(this) }.getOrNull()
    return when (error?.error) {
        "daily_limit_reached" -> AiFailure.DailyLimitReached(
            limit = error.limit ?: 0,
            used = error.used ?: 0,
        )
        "ai_quota_exhausted" -> AiFailure.ProviderQuotaExhausted
        else -> AiFailure.Other(error?.error ?: fallbackCode)
    }
}

/**
 * İstisnadan hata kodu çıkarır.
 *
 * JSON ayrıştırmak yerine düz regex kullanıyoruz, çünkü Ktor gövdeyi mesajın
 * içine gömerken biçimi garanti değil: bazen ham `{"error":"ai_failed"}`,
 * bazen tırnakları kaçırılmış `Text: "{\"error\":\"ai_failed\"}"` şeklinde
 * geliyor. İkincisini `decodeFromString` ayrıştıramıyor ve hata sessizce
 * "ağa ulaşılamadı"ya düşüyordu — sunucu aslında cevap vermiş olsa bile.
 * Regex her iki biçimi de yakalıyor.
 */
private val ERROR_CODE = Regex("""\\?"error\\?"\s*:\s*\\?"([a-z_]+)\\?"""")

private fun Exception.asAiFailure(json: Json): AiFailure {
    val message = message.orEmpty()
    val code = ERROR_CODE.find(message)?.groupValues?.get(1)
        ?: return AiFailure.Other("network")

    return when (code) {
        "daily_limit_reached" -> {
            // Limit/kullanım sayıları mesajdan güvenilir çıkmıyor; sıfır geçip
            // kullanıcıya genel limit metnini gösteriyoruz.
            AiFailure.DailyLimitReached(limit = 0, used = 0)
        }
        "ai_quota_exhausted" -> AiFailure.ProviderQuotaExhausted
        else -> AiFailure.Other(code)
    }
}
