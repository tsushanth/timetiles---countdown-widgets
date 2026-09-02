package com.factory.timetilescountdownwidgets.billing

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import io.mockk.every
import io.mockk.mockk

/**
 * [Purchase.getPurchaseState] doesn't read the raw JSON `purchaseState` field 1:1: internally it
 * treats the wire value `4` as PENDING and collapses every other value (including `2`, the public
 * [Purchase.PurchaseState.PENDING] constant) to PURCHASED. This maps the public constant callers
 * pass into [fakePurchase] to the raw wire encoding the library actually expects.
 */
private fun rawJsonPurchaseState(purchaseState: Int): Int =
    if (purchaseState == Purchase.PurchaseState.PENDING) 4 else 0

/** Builds a real [Purchase] from the same JSON shape the Play Billing library parses. */
fun fakePurchase(
    productIds: List<String>,
    purchaseState: Int = Purchase.PurchaseState.PURCHASED,
    purchaseToken: String = "token-${productIds.joinToString()}",
    acknowledged: Boolean = true,
    orderId: String = "order-1"
): Purchase {
    val productIdsJson = productIds.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
    val json = """
        {
          "orderId": "$orderId",
          "packageName": "com.factory.timetilescountdownwidgets",
          "productIds": $productIdsJson,
          "purchaseTime": 1000,
          "purchaseState": ${rawJsonPurchaseState(purchaseState)},
          "purchaseToken": "$purchaseToken",
          "quantity": 1,
          "acknowledged": $acknowledged,
          "autoRenewing": true
        }
    """.trimIndent()
    return Purchase(json, "signature")
}

/**
 * [ProductDetails] and its nested offer/pricing types only expose package-private JSON
 * constructors, and hand-crafting the library's internal JSON shape is fragile. Deep-mocking
 * the small slice of getters BillingManager/PaywallViewModel actually read is more robust.
 */
fun buildTestProductDetails(
    productId: String,
    type: String = BillingClient.ProductType.SUBS,
    formattedPrice: String = "$9.99",
    billingPeriod: String = "P1M",
    offerToken: String? = "offer-token-$productId"
): ProductDetails {
    val details = mockk<ProductDetails>(relaxed = true)
    every { details.productId } returns productId
    every { details.productType } returns type

    if (type == BillingClient.ProductType.SUBS) {
        if (offerToken != null) {
            val pricingPhase = mockk<ProductDetails.PricingPhase>(relaxed = true)
            every { pricingPhase.formattedPrice } returns formattedPrice
            every { pricingPhase.billingPeriod } returns billingPeriod

            val pricingPhases = mockk<ProductDetails.PricingPhases>(relaxed = true)
            every { pricingPhases.pricingPhaseList } returns listOf(pricingPhase)

            val offer = mockk<ProductDetails.SubscriptionOfferDetails>(relaxed = true)
            every { offer.offerToken } returns offerToken
            every { offer.pricingPhases } returns pricingPhases

            every { details.subscriptionOfferDetails } returns listOf(offer)
        } else {
            every { details.subscriptionOfferDetails } returns emptyList()
        }
    } else {
        val oneTime = mockk<ProductDetails.OneTimePurchaseOfferDetails>(relaxed = true)
        every { oneTime.formattedPrice } returns formattedPrice
        every { details.oneTimePurchaseOfferDetails } returns oneTime
    }
    return details
}
