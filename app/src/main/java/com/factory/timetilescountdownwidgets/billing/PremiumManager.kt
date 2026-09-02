package com.factory.timetilescountdownwidgets.billing

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.android.billingclient.api.Purchase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

private val Context.premiumDataStore by preferencesDataStore(name = "premium_prefs")

/**
 * Single source of truth for the user's premium entitlement. Purchases from [BillingManager]
 * are validated and mirrored into DataStore so premium state survives process death and is
 * available synchronously (via StateFlow) without waiting on a Play Billing round trip.
 */
class PremiumManager(
    private val context: Context,
    private val billingManager: BillingManager,
    externalScope: CoroutineScope
) {
    private object Keys {
        val IS_PREMIUM = booleanPreferencesKey("is_premium")
        val ADS_REMOVED = booleanPreferencesKey("ads_removed")
        val ACTIVE_TIER = stringPreferencesKey("active_tier")
    }

    val isPremium: StateFlow<Boolean> = context.premiumDataStore.data
        .map { it[Keys.IS_PREMIUM] ?: false }
        .stateIn(externalScope, SharingStarted.Eagerly, false)

    val adsRemoved: StateFlow<Boolean> = context.premiumDataStore.data
        .map { it[Keys.ADS_REMOVED] ?: false }
        .stateIn(externalScope, SharingStarted.Eagerly, false)

    val activeTier: StateFlow<PremiumTier?> = context.premiumDataStore.data
        .map { prefs -> prefs[Keys.ACTIVE_TIER]?.let { name -> runCatching { PremiumTier.valueOf(name) }.getOrNull() } }
        .stateIn(externalScope, SharingStarted.Eagerly, null)

    init {
        billingManager.purchases
            .onEach { syncEntitlements(it) }
            .launchIn(externalScope)
    }

    private suspend fun syncEntitlements(purchases: List<Purchase>) {
        val ownedProductIds = purchases
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && PurchaseVerifier.isValid(it) }
            .flatMap { it.products }
            .toSet()

        val tier = PremiumTier.entries.firstOrNull { it.productId in ownedProductIds }
        val isPremiumNow = tier != null
        val adsRemovedNow = isPremiumNow || BillingProducts.IAP_REMOVE_ADS in ownedProductIds

        context.premiumDataStore.edit { prefs ->
            prefs[Keys.IS_PREMIUM] = isPremiumNow
            prefs[Keys.ADS_REMOVED] = adsRemovedNow
            if (tier != null) prefs[Keys.ACTIVE_TIER] = tier.name else prefs.remove(Keys.ACTIVE_TIER)
        }
    }
}
