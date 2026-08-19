package com.repzy.app.data.repo

import android.content.Context
import com.repzy.app.R

/**
 * Hatayı kullanıcının diline çevirir.
 *
 * Buranın tek işi: ham istisna metninin arayüze ULAŞMAMASI. `e.message` doğrudan
 * gösterildiğinde Ktor'un istek dökümü (gövde + `URL:` + `Headers:
 * {Authorization=[Bearer ...], apikey=[...]}`) ekrana basılıyordu.
 * Tanımadığımız her hata genel mesaja düşer — bilinmeyeni olduğu gibi göstermek yok.
 */
fun Throwable.toUserMessage(context: Context): String = when (this) {
    is AiFailure.DailyLimitReached -> context.getString(R.string.error_daily_limit)
    is AiFailure.ProviderQuotaExhausted -> context.getString(R.string.error_service_busy)
    is AiFailure.Other -> when (code) {
        "network" -> context.getString(R.string.error_network)
        else -> context.getString(R.string.error_generic)
    }
    else -> context.getString(R.string.error_generic)
}
