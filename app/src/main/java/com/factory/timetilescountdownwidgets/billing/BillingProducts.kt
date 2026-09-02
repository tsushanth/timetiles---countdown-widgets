package com.factory.timetilescountdownwidgets.billing

import com.android.billingclient.api.BillingClient

/** Every premium subscription tier, keyed by its Play Console product ID. */
enum class PremiumTier(val productId: String, val displayName: String) {
    WEEKLY("com.factory.timetilescountdownwidgets.subscription.weekly", "Weekly"),
    MONTHLY("com.factory.timetilescountdownwidgets.subscription.monthly", "Monthly"),
    YEARLY("com.factory.timetilescountdownwidgets.subscription.yearly", "Yearly"),

    // Modeled as a one-time (INAPP) product even though it shares the "subscription." id
    // namespace with the recurring tiers above: Play Billing has no concept of a
    // never-expiring subscription, so lifetime access has to be sold as a single purchase.
    LIFETIME("com.factory.timetilescountdownwidgets.subscription.lifetime", "Lifetime");

    val billingProductType: String
        get() = if (this == LIFETIME) BillingClient.ProductType.INAPP else BillingClient.ProductType.SUBS
}

object BillingProducts {
    const val IAP_REMOVE_ADS = "com.factory.timetilescountdownwidgets.remove_ads"

    /** Hardcoded display fallbacks shown while ProductDetails is still loading from Play. */
    const val WEEKLY_FALLBACK_PRICE = "$8.80"
    const val REMOVE_ADS_FALLBACK_PRICE = "$1.99"

    val subscriptionProductIds: List<String> = PremiumTier.entries
        .filter { it.billingProductType == BillingClient.ProductType.SUBS }
        .map { it.productId }

    val inAppProductIds: List<String> = PremiumTier.entries
        .filter { it.billingProductType == BillingClient.ProductType.INAPP }
        .map { it.productId } + IAP_REMOVE_ADS

    fun tierForProductId(productId: String): PremiumTier? =
        PremiumTier.entries.firstOrNull { it.productId == productId }
}

data class PaywallProduct(
    val productId: String,
    val productType: String,
    val tier: PremiumTier?,
    val title: String,
    val formattedPrice: String?,
    val fallbackPrice: String?,
    val billingPeriod: String?,
    val offerToken: String?
) {
    val displayPrice: String get() = formattedPrice ?: fallbackPrice ?: "—"
}
