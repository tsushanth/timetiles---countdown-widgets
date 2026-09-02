package com.factory.timetilescountdownwidgets.billing

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.factory.timetilescountdownwidgets.testutil.MainDispatcherRule
import com.android.billingclient.api.Purchase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * [PremiumManager] persists entitlements to a process-wide DataStore singleton, so every test
 * below explicitly drives a known [purchases] value through construction: syncEntitlements()
 * always fully overwrites all three keys, which makes each test self-healing regardless of
 * state left behind by DataStore's real (non-virtual-time) IO from a previous test.
 *
 * [isPremium], [adsRemoved] and [activeTier] are three *independent* `.stateIn()` pipelines over
 * the same underlying DataStore flow, so they don't necessarily settle in the same tick. Every
 * assertion below waits on the specific flow being asserted via its own turbine subscription
 * instead of reading a sibling flow's `.value` right after awaiting a different one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class PremiumManagerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private suspend fun <T> ReceiveTurbine<T>.awaitValue(expected: T): T {
        var item = awaitItem()
        while (item != expected) item = awaitItem()
        return item
    }

    private fun buildManager(purchases: MutableStateFlow<List<Purchase>>): PremiumManager {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val billingManager = mockk<BillingManager>(relaxed = true)
        every { billingManager.purchases } returns purchases
        val scope = TestScope(mainDispatcherRule.testDispatcher)
        return PremiumManager(context, billingManager, scope)
    }

    @Test
    fun `no purchases leaves isPremium false, activeTier null and ads present`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val manager = buildManager(MutableStateFlow(emptyList()))

            manager.isPremium.test { awaitValue(false) }
            manager.activeTier.test { awaitValue(null) }
            manager.adsRemoved.test { awaitValue(false) }
        }

    @Test
    fun `owning a subscription product marks isPremium true and records the active tier`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val purchases = MutableStateFlow<List<Purchase>>(emptyList())
            val manager = buildManager(purchases)
            manager.isPremium.test { awaitValue(false) }

            purchases.value = listOf(fakePurchase(listOf(PremiumTier.MONTHLY.productId)))

            manager.isPremium.test { awaitValue(true) }
            manager.activeTier.test { awaitValue(PremiumTier.MONTHLY) }
            manager.adsRemoved.test { awaitValue(true) }
        }

    @Test
    fun `owning remove-ads product removes ads without granting premium`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val purchases = MutableStateFlow<List<Purchase>>(emptyList())
            val manager = buildManager(purchases)
            manager.adsRemoved.test { awaitValue(false) }

            purchases.value = listOf(fakePurchase(listOf(BillingProducts.IAP_REMOVE_ADS)))

            manager.adsRemoved.test { awaitValue(true) }
            manager.isPremium.test { awaitValue(false) }
            manager.activeTier.test { awaitValue(null) }
        }

    @Test
    fun `losing entitlement clears premium state and active tier`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val purchases = MutableStateFlow(
                listOf(fakePurchase(listOf(PremiumTier.YEARLY.productId)))
            )
            val manager = buildManager(purchases)
            manager.isPremium.test { awaitValue(true) }
            manager.activeTier.test { awaitValue(PremiumTier.YEARLY) }

            purchases.value = emptyList()

            manager.isPremium.test { awaitValue(false) }
            manager.activeTier.test { awaitValue(null) }
            manager.adsRemoved.test { awaitValue(false) }
        }

    @Test
    fun `pending purchases do not grant premium`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val purchases = MutableStateFlow<List<Purchase>>(emptyList())
            val manager = buildManager(purchases)
            manager.isPremium.test { awaitValue(false) }

            purchases.value = listOf(
                fakePurchase(
                    listOf(PremiumTier.WEEKLY.productId),
                    purchaseState = Purchase.PurchaseState.PENDING
                )
            )

            manager.isPremium.test { awaitValue(false) }
        }

    @Test
    fun `lifetime product grants premium as a non-expiring tier`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val purchases = MutableStateFlow<List<Purchase>>(emptyList())
            val manager = buildManager(purchases)
            manager.isPremium.test { awaitValue(false) }

            purchases.value = listOf(fakePurchase(listOf(PremiumTier.LIFETIME.productId)))

            manager.isPremium.test { awaitValue(true) }
            manager.activeTier.test { awaitValue(PremiumTier.LIFETIME) }
        }
}
