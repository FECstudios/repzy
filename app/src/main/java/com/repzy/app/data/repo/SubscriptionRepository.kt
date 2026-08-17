package com.repzy.app.data.repo

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class PurchaseReport(val purchaseToken: String)

/**
 * Abonelik yetkisi. Tek doğruluk kaynağı sunucu: `is_premium()` RPC'si.
 *
 * İstemci premium olduğunu iddia edemez — `subscriptions` tablosunda kullanıcı için
 * insert/update politikası yok. Satın alma token'ı `verify-purchase` fonksiyonuna
 * bildiriliyor, doğrulama Google Play Developer API'siyle orada yapılacak.
 */
@Singleton
class SubscriptionRepository @Inject constructor(
    private val client: SupabaseClient,
) {
    suspend fun isPremium(): Result<Boolean> = runCatching {
        client.postgrest.rpc("is_premium").decodeAs<Boolean>()
    }

    /**
     * Satın alma token'ını sunucuya bildirir. Fonksiyon henüz yayında değilse
     * (doğrulama kurulmadıysa) hata yutulur: kullanıcı akışı kırılmaz, sadece
     * premium açılmaz. Bu bilinçli — doğrulanmamış satın almaya yetki verilmiyor.
     */
    suspend fun reportPurchase(purchaseToken: String): Result<Unit> = runCatching {
        client.functions.invoke(
            function = "verify-purchase",
            body = PurchaseReport(purchaseToken),
        )
        Unit
    }
}
