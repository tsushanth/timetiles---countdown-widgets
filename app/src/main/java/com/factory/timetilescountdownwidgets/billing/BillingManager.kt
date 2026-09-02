package com.factory.timetilescountdownwidgets.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the [BillingClient] connection lifecycle, product catalog, and purchase flow.
 * A single instance lives for the process lifetime (see TimeTilesApplication).
 */
class BillingManager(context: Context) : PurchasesUpdatedListener {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    private val _connectionState = MutableStateFlow<BillingConnectionState>(BillingConnectionState.Disconnected)
    val connectionState: StateFlow<BillingConnectionState> = _connectionState.asStateFlow()

    private val _productDetails = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val productDetails: StateFlow<Map<String, ProductDetails>> = _productDetails.asStateFlow()

    private val _purchases = MutableStateFlow<List<Purchase>>(emptyList())
    val purchases: StateFlow<List<Purchase>> = _purchases.asStateFlow()

    private val _purchaseEvents = MutableSharedFlow<PurchaseEvent>(extraBufferCapacity = 4)
    val purchaseEvents: SharedFlow<PurchaseEvent> = _purchaseEvents.asSharedFlow()

    private var reconnectAttempts = 0

    fun startConnection() {
        if (billingClient.isReady) return
        _connectionState.value = BillingConnectionState.Connecting
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    reconnectAttempts = 0
                    _connectionState.value = BillingConnectionState.Connected
                    scope.launch {
                        queryProductDetails()
                        refreshPurchases()
                    }
                } else {
                    _connectionState.value = BillingConnectionState.Error(result.debugMessage)
                }
            }

            override fun onBillingServiceDisconnected() {
                _connectionState.value = BillingConnectionState.Disconnected
                retryConnectionWithBackoff()
            }
        })
    }

    fun refreshPurchasesIfReady() {
        if (billingClient.isReady) scope.launch { refreshPurchases() }
    }

    private fun retryConnectionWithBackoff() {
        reconnectAttempts++
        val delayMs = minOf(1_000L * (1L shl reconnectAttempts.coerceAtMost(6)), 30_000L)
        scope.launch {
            delay(delayMs)
            startConnection()
        }
    }

    suspend fun queryProductDetails() {
        val subsProducts = BillingProducts.subscriptionProductIds.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val inAppProducts = BillingProducts.inAppProductIds.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }

        val results = listOf(subsProducts, inAppProducts).filter { it.isNotEmpty() }.map { productList ->
            billingClient.queryProductDetails(
                QueryProductDetailsParams.newBuilder().setProductList(productList).build()
            )
        }

        val merged = _productDetails.value.toMutableMap()
        results.forEach { result ->
            if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                result.productDetailsList?.forEach { details -> merged[details.productId] = details }
            }
        }
        _productDetails.value = merged
    }

    suspend fun refreshPurchases() {
        val subsResult = billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        )
        val inAppResult = billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        )
        val all = subsResult.purchasesList + inAppResult.purchasesList
        _purchases.value = all
        all.forEach { processPurchase(it, emitEvents = false) }
    }

    fun launchPurchaseFlow(activity: Activity, productId: String) {
        val details = _productDetails.value[productId]
        if (details == null) {
            _purchaseEvents.tryEmit(PurchaseEvent.Error("Product not available. Check your connection and try again."))
            return
        }

        val paramsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details)
        if (details.productType == BillingClient.ProductType.SUBS) {
            val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
            if (offerToken == null) {
                _purchaseEvents.tryEmit(PurchaseEvent.Error("No subscription offer available for this product."))
                return
            }
            paramsBuilder.setOfferToken(offerToken)
        }

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(paramsBuilder.build()))
            .build()

        val result = billingClient.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _purchaseEvents.tryEmit(PurchaseEvent.Error(result.debugMessage))
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val updated = purchases.orEmpty()
                scope.launch {
                    updated.forEach { processPurchase(it, emitEvents = true) }
                    _purchases.value = (_purchases.value.filterNot { existing ->
                        updated.any { it.purchaseToken == existing.purchaseToken }
                    } + updated)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED ->
                _purchaseEvents.tryEmit(PurchaseEvent.Cancelled)
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingClient.BillingResponseCode.NETWORK_ERROR ->
                _purchaseEvents.tryEmit(PurchaseEvent.NetworkError)
            else ->
                _purchaseEvents.tryEmit(PurchaseEvent.Error(result.debugMessage))
        }
    }

    private suspend fun processPurchase(purchase: Purchase, emitEvents: Boolean) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                if (!PurchaseVerifier.isValid(purchase)) {
                    if (emitEvents) {
                        _purchaseEvents.tryEmit(PurchaseEvent.Error("Purchase could not be verified."))
                    }
                    return
                }
                if (!purchase.isAcknowledged) {
                    val ackResult = billingClient.acknowledgePurchase(
                        AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                    )
                    if (ackResult.responseCode != BillingClient.BillingResponseCode.OK) {
                        if (emitEvents) {
                            _purchaseEvents.tryEmit(PurchaseEvent.Error(ackResult.debugMessage))
                        }
                        return
                    }
                }
                if (emitEvents) {
                    _purchaseEvents.tryEmit(PurchaseEvent.Success(purchase.products))
                }
            }
            Purchase.PurchaseState.PENDING -> {
                if (emitEvents) _purchaseEvents.tryEmit(PurchaseEvent.Pending)
            }
            else -> Unit
        }
    }

    fun endConnection() {
        billingClient.endConnection()
    }
}
