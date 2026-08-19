package com.repzy.app.ui.paywall

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repzy.app.billing.BillingRepository
import com.repzy.app.billing.PlanOffer
import com.repzy.app.billing.Products
import com.repzy.app.data.repo.AuthRepository
import com.repzy.app.data.repo.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.repzy.app.data.repo.toUserMessage

data class PaywallUiState(
    val isLoading: Boolean = true,
    val offers: List<PlanOffer> = emptyList(),
    val selectedPlanId: String? = null,
    val isPremium: Boolean = false,
    val isPurchasing: Boolean = false,
    val error: String? = null,
) {
    /** Play Console'da ürün tanımlı değilse satın alma yapılamaz. */
    val storeUnavailable: Boolean get() = !isLoading && offers.isEmpty()

    val selected: PlanOffer? get() = offers.firstOrNull { it.planId == selectedPlanId }

    /**
     * Yıllık planın aylığa göre tasarrufu. Aylık plan yoksa (ya da fiyat gelmediyse)
     * uydurma bir yüzde göstermemek için null döner.
     */
    fun savingPercentFor(offer: PlanOffer): Int? {
        if (offer.planId != Products.PLAN_YEARLY) return null
        val monthly = offers.firstOrNull { it.planId == Products.PLAN_MONTHLY } ?: return null
        if (monthly.priceMicros <= 0 || offer.priceMicros <= 0) return null

        val yearlyIfMonthly = monthly.priceMicros * 12
        val saving = 100 - (offer.priceMicros * 100 / yearlyIfMonthly)
        return saving.toInt().takeIf { it in 1..90 }
    }

    /** Yıllık planı öne çıkarıyoruz — dönüşüm ve yıllık gelir için en iyisi. */
    val ordered: List<PlanOffer>
        get() = offers.sortedBy { offer ->
            when (offer.planId) {
                Products.PLAN_YEARLY -> 0
                Products.PLAN_MONTHLY -> 1
                Products.PLAN_WEEKLY -> 2
                else -> 3
            }
        }
}

@HiltViewModel
class PaywallViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val billingRepository: BillingRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PaywallUiState())
    val state: StateFlow<PaywallUiState> = _state.asStateFlow()

    init {
        load()
        observePurchases()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            val premium = subscriptionRepository.isPremium().getOrDefault(false)
            billingRepository.loadOffers()
                .onSuccess { offers ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            offers = offers,
                            // Varsayılan seçim yıllık; yoksa ilk plan.
                            selectedPlanId = offers.firstOrNull {
                                o -> o.planId == Products.PLAN_YEARLY
                            }?.planId ?: offers.firstOrNull()?.planId,
                            isPremium = premium,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.toUserMessage(appContext), isPremium = premium) }
                }
        }
    }

    /**
     * Satın alma tamamlandığında token sunucuya gönderilir. Premium yetkisini
     * sunucu veriyor; burada sadece bildiriyoruz.
     */
    private fun observePurchases() {
        viewModelScope.launch {
            billingRepository.lastPurchase.collect { purchase ->
                if (purchase == null) return@collect
                billingRepository.acknowledge(purchase)
                subscriptionRepository.reportPurchase(
                    purchaseToken = purchase.purchaseToken,
                    productId = purchase.products.firstOrNull(),
                )
                refreshPremium()
            }
        }
    }

    fun select(planId: String) = _state.update { it.copy(selectedPlanId = planId) }

    fun purchase(activity: Activity) {
        val offer = _state.value.selected ?: return
        _state.update { it.copy(isPurchasing = true, error = null) }

        // Satın almayı hesaba bağlıyoruz; sunucu da aynı bağı kontrol ediyor.
        billingRepository.launchPurchase(activity, offer, authRepository.currentUserId)
            .onFailure { e -> _state.update { it.copy(isPurchasing = false, error = e.toUserMessage(appContext)) } }
            .onSuccess { _state.update { it.copy(isPurchasing = false) } }
    }

    /** "Satın almalarımı geri yükle": Play'deki aktif aboneliği sunucuya tekrar bildirir. */
    fun restore() {
        viewModelScope.launch {
            _state.update { it.copy(isPurchasing = true, error = null) }
            billingRepository.activePurchases()
                .onSuccess { purchases ->
                    purchases.forEach {
                        subscriptionRepository.reportPurchase(
                            purchaseToken = it.purchaseToken,
                            productId = it.products.firstOrNull(),
                        )
                    }
                    refreshPremium()
                    _state.update { it.copy(isPurchasing = false) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isPurchasing = false, error = e.toUserMessage(appContext)) }
                }
        }
    }

    private suspend fun refreshPremium() {
        val premium = subscriptionRepository.isPremium().getOrDefault(false)
        _state.update { it.copy(isPremium = premium) }
    }

    fun dismissError() = _state.update { it.copy(error = null) }
}
