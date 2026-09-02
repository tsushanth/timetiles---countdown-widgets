package com.factory.timetilescountdownwidgets.ui.paywall

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.BillingClient
import com.factory.timetilescountdownwidgets.billing.BillingConnectionState
import com.factory.timetilescountdownwidgets.billing.BillingManager
import com.factory.timetilescountdownwidgets.billing.BillingProducts
import com.factory.timetilescountdownwidgets.billing.PaywallProduct
import com.factory.timetilescountdownwidgets.billing.PremiumManager
import com.factory.timetilescountdownwidgets.billing.PremiumTier
import com.factory.timetilescountdownwidgets.billing.PurchaseEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PaywallUiState(
    val connectionState: BillingConnectionState = BillingConnectionState.Connecting,
    val subscriptionProducts: List<PaywallProduct> = emptyList(),
    val removeAdsProduct: PaywallProduct? = null,
    val isPremium: Boolean = false,
    val activeTier: PremiumTier? = null,
    val message: String? = null
)

class PaywallViewModel(
    private val billingManager: BillingManager,
    private val premiumManager: PremiumManager
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PaywallUiState> = combine(
        billingManager.connectionState,
        billingManager.productDetails,
        premiumManager.isPremium,
        premiumManager.activeTier,
        _message
    ) { connection, details, isPremium, activeTier, message ->
        val subscriptionProducts = PremiumTier.entries.map { tier ->
            val fallback = if (tier == PremiumTier.WEEKLY) BillingProducts.WEEKLY_FALLBACK_PRICE else null
            val productDetails = details[tier.productId]
            val offer = productDetails?.subscriptionOfferDetails?.firstOrNull()
            PaywallProduct(
                productId = tier.productId,
                productType = tier.billingProductType,
                tier = tier,
                title = tier.displayName,
                formattedPrice = if (tier == PremiumTier.LIFETIME) {
                    productDetails?.oneTimePurchaseOfferDetails?.formattedPrice
                } else {
                    offer?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                },
                fallbackPrice = fallback,
                billingPeriod = offer?.pricingPhases?.pricingPhaseList?.firstOrNull()?.billingPeriod,
                offerToken = offer?.offerToken
            )
        }

        val removeAdsDetails = details[BillingProducts.IAP_REMOVE_ADS]
        val removeAdsProduct = PaywallProduct(
            productId = BillingProducts.IAP_REMOVE_ADS,
            productType = BillingClient.ProductType.INAPP,
            tier = null,
            title = "Remove Ads",
            formattedPrice = removeAdsDetails?.oneTimePurchaseOfferDetails?.formattedPrice,
            fallbackPrice = BillingProducts.REMOVE_ADS_FALLBACK_PRICE,
            billingPeriod = null,
            offerToken = null
        )

        PaywallUiState(
            connectionState = connection,
            subscriptionProducts = subscriptionProducts,
            removeAdsProduct = removeAdsProduct,
            isPremium = isPremium,
            activeTier = activeTier,
            message = message
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PaywallUiState())

    init {
        billingManager.purchaseEvents.onEach { event ->
            _message.value = when (event) {
                is PurchaseEvent.Success -> "Purchase successful — premium unlocked!"
                PurchaseEvent.Cancelled -> null
                PurchaseEvent.Pending -> "Your purchase is pending confirmation. Premium will unlock automatically once it clears."
                is PurchaseEvent.Error -> "Something went wrong: ${event.message}"
                PurchaseEvent.NetworkError -> "No internet connection. Please check your network and try again."
            }
        }.launchIn(viewModelScope)

        if (billingManager.connectionState.value is BillingConnectionState.Connected) {
            viewModelScope.launch { billingManager.queryProductDetails() }
        } else {
            billingManager.startConnection()
        }
    }

    fun purchase(activity: Activity, productId: String) {
        billingManager.launchPurchaseFlow(activity, productId)
    }

    fun restorePurchases() {
        viewModelScope.launch {
            _message.value = "Restoring purchases…"
            billingManager.refreshPurchases()
            _message.value = if (premiumManager.isPremium.value || premiumManager.adsRemoved.value) {
                "Purchases restored."
            } else {
                "No previous purchases found."
            }
        }
    }

    fun retryConnection() {
        billingManager.startConnection()
    }

    fun dismissMessage() {
        _message.value = null
    }

    class Factory(
        private val billingManager: BillingManager,
        private val premiumManager: PremiumManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PaywallViewModel(billingManager, premiumManager) as T
        }
    }
}
