package com.repzy.app.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Play Console'da tanımlanacak abonelik ürünü ve teklif etiketleri. */
object Products {
    const val SUBSCRIPTION_ID = "repzy_premium"

    /** Play'de aynı abonelik altında üç temel plan: yıllık öne çıkar. */
    const val PLAN_YEARLY = "yearly"
    const val PLAN_MONTHLY = "monthly"
    const val PLAN_WEEKLY = "weekly"
}

/** Paywall'da gösterilecek tek plan. Fiyat metni Play'den geliyor, biz biçimlendirmiyoruz. */
data class PlanOffer(
    val planId: String,
    val offerToken: String,
    val formattedPrice: String,
    /** Tasarruf yüzdesini hesaplamak için ham fiyat; gösterimde kullanılmıyor. */
    val priceMicros: Long,
    val billingPeriod: String,
    val hasFreeTrial: Boolean,
    val trialDays: Int?,
)

/**
 * Play Billing sarmalayıcısı.
 *
 * ÖNEMLİ: Satın alma burada tamamlanıyor ama **yetki (premium) burada belirlenmiyor.**
 * Premium durumunu sunucu söylüyor (`is_premium()`); satın alma token'ı doğrulanmadan
 * kimse premium olmuyor. İstemci tarafı yetki, APK üzerinde oynayan biri için
 * bedava premium demektir — AI çağrıları para tuttuğu için bu risk alınmıyor.
 */
@Singleton
class BillingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _offers = MutableStateFlow<List<PlanOffer>>(emptyList())
    val offers: StateFlow<List<PlanOffer>> = _offers.asStateFlow()

    private val _lastPurchase = MutableStateFlow<Purchase?>(null)
    val lastPurchase: StateFlow<Purchase?> = _lastPurchase.asStateFlow()

    private val connectionMutex = Mutex()
    private var connected = false

    /**
     * Satın alma akışı offerToken'ın yanında ProductDetails nesnesini de istiyor;
     * yeniden sorgulamak yerine son sorgunun sonucunu tutuyoruz.
     */
    private var cachedDetails: ProductDetails? = null

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode != BillingClient.BillingResponseCode.OK) return@PurchasesUpdatedListener
        purchases?.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            ?.let { _lastPurchase.value = it }
    }

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(purchasesListener)
        // Billing 9 bekleyen satın alma türlerinin açıkça belirtilmesini istiyor.
        // Abonelik sattığımız için ön ödemeli planlar; tek seferlik ürün de ileride eklenebilir.
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build(),
        )
        .build()

    private suspend fun ensureConnected(): Boolean = connectionMutex.withLock {
        if (connected && client.isReady) return true

        val ready = CompletableDeferred<Boolean>()
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                ready.complete(result.responseCode == BillingClient.BillingResponseCode.OK)
            }

            override fun onBillingServiceDisconnected() {
                connected = false
            }
        })
        connected = ready.await()
        connected
    }

    /**
     * Planları çeker. Play Console'da ürün tanımlı değilse boş liste döner —
     * paywall bunu "şu an satın alma yapılamıyor" olarak gösteriyor, çökmüyor.
     */
    suspend fun loadOffers(): Result<List<PlanOffer>> = runCatching {
        if (!ensureConnected()) return@runCatching emptyList()

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(Products.SUBSCRIPTION_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                ),
            )
            .build()

        val details: List<ProductDetails> = suspendCancellableCoroutine { cont ->
            client.queryProductDetailsAsync(params) { _, result ->
                cont.resume(result.productDetailsList.orEmpty())
            }
        }

        cachedDetails = details.firstOrNull()

        val offers = details.flatMap { product ->
            product.subscriptionOfferDetails.orEmpty().map { offer ->
                // Fiyatı olmayan ilk faz ücretsiz deneme demektir.
                val phases = offer.pricingPhases.pricingPhaseList
                val trial = phases.firstOrNull { it.priceAmountMicros == 0L }
                val paid = phases.lastOrNull()

                PlanOffer(
                    planId = offer.basePlanId,
                    offerToken = offer.offerToken,
                    formattedPrice = paid?.formattedPrice.orEmpty(),
                    priceMicros = paid?.priceAmountMicros ?: 0L,
                    billingPeriod = paid?.billingPeriod.orEmpty(),
                    hasFreeTrial = trial != null,
                    trialDays = trial?.billingPeriod?.let(::isoPeriodToDays),
                )
            }
        }
        _offers.value = offers
        offers
    }

    /**
     * Play'in satın alma ekranını açar. Sonuç [lastPurchase] üzerinden gelir.
     *
     * [accountId]: satın almayı hesaba bağlıyor. Play bunu `obfuscatedExternalAccountId`
     * olarak saklıyor; olmadan bir satın alma fişi başka bir Repzy hesabında
     * "benim" diye gösterilebilirdi. Sunucu tarafında da token-hesap bağı
     * kontrol ediliyor, bu ilk savunma hattı.
     */
    fun launchPurchase(
        activity: Activity,
        offer: PlanOffer,
        accountId: String?,
    ): Result<Unit> = runCatching {
        val details = cachedDetails ?: error("Plan bilgisi yüklenmedi.")

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offer.offerToken)
                        .build(),
                ),
            )
            .apply {
                // Play ham kullanıcı kimliği istemiyor; uuid zaten opak ama
                // yine de 64 karakter sınırına uyuyoruz.
                accountId?.take(64)?.let { setObfuscatedAccountId(it) }
            }
            .build()

        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            error("Play satın alma ekranı açılamadı (${result.responseCode}).")
        }
    }

    /** Uygulama açılışında ve "satın almalarımı geri yükle" sonrası çağrılır. */
    suspend fun activePurchases(): Result<List<Purchase>> = runCatching {
        if (!ensureConnected()) return@runCatching emptyList()

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        suspendCancellableCoroutine { cont ->
            client.queryPurchasesAsync(params) { _, purchases ->
                cont.resume(purchases)
            }
        }
    }

    /** Onaylanmayan satın alma 3 gün sonra Play tarafından iade edilir. */
    suspend fun acknowledge(purchase: Purchase): Result<Unit> = runCatching {
        if (purchase.isAcknowledged) return@runCatching
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        suspendCancellableCoroutine<Unit> { cont ->
            client.acknowledgePurchase(params) { cont.resume(Unit) }
        }
    }
}

/** "P14D" / "P1W" gibi ISO-8601 süreyi güne çevirir; tanımadığı biçimde null döner. */
private fun isoPeriodToDays(period: String): Int? {
    val match = Regex("""P(\d+)([DWMY])""").find(period) ?: return null
    val amount = match.groupValues[1].toIntOrNull() ?: return null
    return when (match.groupValues[2]) {
        "D" -> amount
        "W" -> amount * 7
        "M" -> amount * 30
        "Y" -> amount * 365
        else -> null
    }
}
