package com.repzy.app.data.repo

/**
 * Edge Function'ların ortak hata dili. Kullanıcının limiti ile sağlayıcının kotasını
 * ayırmak önemli: birincisi "yarın devam", ikincisi "birazdan tekrar dene".
 */
sealed class AiFailure(message: String) : Exception(message) {

    data class DailyLimitReached(val limit: Int, val used: Int) :
        AiFailure("Günlük limit doldu ($used/$limit).")

    data object ProviderQuotaExhausted :
        AiFailure("Servis şu an yoğun, birazdan tekrar dene.")

    data class Other(val code: String) : AiFailure("İşlem başarısız ($code).")
}
