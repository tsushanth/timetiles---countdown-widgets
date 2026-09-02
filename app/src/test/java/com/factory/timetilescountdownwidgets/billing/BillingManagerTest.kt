package com.factory.timetilescountdownwidgets.billing

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.android.billingclient.api.AcknowledgePurchaseResponseListener
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetailsResponseListener
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResponseListener
import com.android.billingclient.api.QueryPurchasesParams
import com.factory.timetilescountdownwidgets.testutil.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * BillingManager builds its own [BillingClient] internally (no DI seam), so these tests
 * intercept [BillingClient.newBuilder] via [mockkStatic] to hand it a fully mocked client.
 * The manager's coroutine scope is hardcoded to `Dispatchers.Main.immediate`; [mainDispatcherRule]
 * installs a [kotlinx.coroutines.test.TestDispatcher] there and every `runTest` below is pinned
 * to that *same* dispatcher/scheduler instance so the manager's internally-launched work and the
 * test body advance in lockstep instead of racing on two independent virtual clocks.
 *
 * Real [Purchase]/[com.android.billingclient.api.BillingFlowParams] instances built by
 * production code parse JSON and touch `android.util.Base64`/`android.text.TextUtils` under the
 * hood; those only behave correctly under Robolectric's real shadows (the plain "return default
 * values" unit-test stub jar silently returns wrong values for them), so this suite runs on
 * Robolectric even though it never touches Android UI.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class BillingManagerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var context: Context
    private lateinit var billingClient: BillingClient
    private lateinit var builder: BillingClient.Builder
    private lateinit var manager: BillingManager

    private fun billingResult(code: Int, message: String = ""): BillingResult =
        BillingResult.newBuilder().setResponseCode(code).setDebugMessage(message).build()

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        every { context.applicationContext } returns context

        billingClient = mockk(relaxed = true)
        builder = mockk(relaxed = true)

        mockkStatic(BillingClient::class)
        every { BillingClient.newBuilder(any()) } returns builder
        every { builder.setListener(any()) } returns builder
        every { builder.enablePendingPurchases() } returns builder
        every { builder.build() } returns billingClient

        every { billingClient.startConnection(any()) } answers {
            firstArg<BillingClientStateListener>()
                .onBillingSetupFinished(billingResult(BillingClient.BillingResponseCode.OK))
        }
        every { billingClient.queryProductDetailsAsync(any(), any()) } answers {
            secondArg<ProductDetailsResponseListener>()
                .onProductDetailsResponse(billingResult(BillingClient.BillingResponseCode.OK), emptyList())
        }
        every { billingClient.queryPurchasesAsync(any<QueryPurchasesParams>(), any()) } answers {
            secondArg<PurchasesResponseListener>()
                .onQueryPurchasesResponse(billingResult(BillingClient.BillingResponseCode.OK), emptyList())
        }
        every { billingClient.acknowledgePurchase(any(), any()) } answers {
            secondArg<AcknowledgePurchaseResponseListener>()
                .onAcknowledgePurchaseResponse(billingResult(BillingClient.BillingResponseCode.OK))
        }

        manager = BillingManager(context)
    }

    @After
    fun tearDown() {
        unmockkStatic(BillingClient::class)
    }

    @Test
    fun `startConnection success moves state to Connected`() = runTest(mainDispatcherRule.testDispatcher) {
        manager.connectionState.test {
            assertEquals(BillingConnectionState.Disconnected, awaitItem())
            manager.startConnection()
            assertEquals(BillingConnectionState.Connecting, awaitItem())
            assertEquals(BillingConnectionState.Connected, awaitItem())
        }
    }

    @Test
    fun `startConnection failure moves state to Error`() = runTest(mainDispatcherRule.testDispatcher) {
        every { billingClient.startConnection(any()) } answers {
            firstArg<BillingClientStateListener>()
                .onBillingSetupFinished(billingResult(BillingClient.BillingResponseCode.BILLING_UNAVAILABLE, "unavailable"))
        }

        manager.connectionState.test {
            assertEquals(BillingConnectionState.Disconnected, awaitItem())
            manager.startConnection()
            assertEquals(BillingConnectionState.Connecting, awaitItem())
            val error = assertIs<BillingConnectionState.Error>(awaitItem())
            assertEquals("unavailable", error.message)
        }
    }

    @Test
    fun `startConnection is a no-op while the client is already ready`() = runTest(mainDispatcherRule.testDispatcher) {
        every { billingClient.isReady } returns true
        manager.startConnection()
        verify(exactly = 0) { billingClient.startConnection(any()) }
    }

    @Test
    fun `product details are loaded and merged into state after connecting`() = runTest(mainDispatcherRule.testDispatcher) {
        val weekly = buildTestProductDetails(PremiumTier.WEEKLY.productId)
        val lifetime = buildTestProductDetails(PremiumTier.LIFETIME.productId, type = BillingClient.ProductType.INAPP)
        // BillingManager issues one query for SUBS products and one for INAPP products; return
        // the full merged catalog on each call the same way independent Play responses would
        // combine once both complete.
        every { billingClient.queryProductDetailsAsync(any(), any()) } answers {
            secondArg<ProductDetailsResponseListener>()
                .onProductDetailsResponse(billingResult(BillingClient.BillingResponseCode.OK), listOf(weekly, lifetime))
        }

        manager.productDetails.test {
            assertEquals(emptyMap(), awaitItem())
            manager.startConnection()
            val merged = awaitItem()
            assertEquals(setOf(PremiumTier.WEEKLY.productId, PremiumTier.LIFETIME.productId), merged.keys)
        }
    }

    @Test
    fun `launchPurchaseFlow emits error when product details are not loaded`() = runTest(mainDispatcherRule.testDispatcher) {
        manager.purchaseEvents.test {
            manager.launchPurchaseFlow(mockk<Activity>(relaxed = true), PremiumTier.MONTHLY.productId)
            val event = assertIs<PurchaseEvent.Error>(awaitItem())
            assertTrue(event.message.contains("not available"))
        }
        verify(exactly = 0) { billingClient.launchBillingFlow(any(), any()) }
    }

    @Test
    fun `launchPurchaseFlow emits error when subscription has no offer token`() = runTest(mainDispatcherRule.testDispatcher) {
        every { billingClient.queryProductDetailsAsync(any(), any()) } answers {
            secondArg<ProductDetailsResponseListener>().onProductDetailsResponse(
                billingResult(BillingClient.BillingResponseCode.OK),
                listOf(buildTestProductDetails(PremiumTier.MONTHLY.productId, offerToken = null))
            )
        }
        manager.startConnection()

        manager.purchaseEvents.test {
            manager.launchPurchaseFlow(mockk<Activity>(relaxed = true), PremiumTier.MONTHLY.productId)
            val event = assertIs<PurchaseEvent.Error>(awaitItem())
            assertTrue(event.message.contains("No subscription offer"))
        }
        verify(exactly = 0) { billingClient.launchBillingFlow(any(), any()) }
    }

    @Test
    fun `launchPurchaseFlow launches billing flow for a loaded product`() = runTest(mainDispatcherRule.testDispatcher) {
        every { billingClient.queryProductDetailsAsync(any(), any()) } answers {
            secondArg<ProductDetailsResponseListener>().onProductDetailsResponse(
                billingResult(BillingClient.BillingResponseCode.OK),
                listOf(buildTestProductDetails(PremiumTier.MONTHLY.productId))
            )
        }
        manager.startConnection()

        val activity = mockk<Activity>(relaxed = true)
        manager.launchPurchaseFlow(activity, PremiumTier.MONTHLY.productId)

        verify(exactly = 1) { billingClient.launchBillingFlow(activity, any()) }
    }

    @Test
    fun `launchPurchaseFlow emits error when BillingClient rejects the flow`() = runTest(mainDispatcherRule.testDispatcher) {
        every { billingClient.queryProductDetailsAsync(any(), any()) } answers {
            secondArg<ProductDetailsResponseListener>().onProductDetailsResponse(
                billingResult(BillingClient.BillingResponseCode.OK),
                listOf(buildTestProductDetails(PremiumTier.MONTHLY.productId))
            )
        }
        every { billingClient.launchBillingFlow(any(), any()) } returns
            billingResult(BillingClient.BillingResponseCode.ERROR, "boom")
        manager.startConnection()

        manager.purchaseEvents.test {
            manager.launchPurchaseFlow(mockk<Activity>(relaxed = true), PremiumTier.MONTHLY.productId)
            val event = assertIs<PurchaseEvent.Error>(awaitItem())
            assertEquals("boom", event.message)
        }
    }

    @Test
    fun `onPurchasesUpdated success acknowledges and emits Success then updates purchases`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val purchase = fakePurchase(listOf(PremiumTier.MONTHLY.productId), acknowledged = false)

            manager.purchases.test {
                assertEquals(emptyList(), awaitItem())
                manager.purchaseEvents.test {
                    manager.onPurchasesUpdated(
                        billingResult(BillingClient.BillingResponseCode.OK),
                        listOf(purchase)
                    )
                    val event = assertIs<PurchaseEvent.Success>(awaitItem())
                    assertEquals(listOf(PremiumTier.MONTHLY.productId), event.productIds)
                }
                val purchases = awaitItem()
                assertEquals(1, purchases.size)
                assertEquals(purchase.purchaseToken, purchases.first().purchaseToken)
            }
            verify(exactly = 1) { billingClient.acknowledgePurchase(any(), any()) }
        }

    @Test
    fun `onPurchasesUpdated does not re-acknowledge already acknowledged purchases`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val purchase = fakePurchase(listOf(PremiumTier.MONTHLY.productId), acknowledged = true)
            manager.purchaseEvents.test {
                manager.onPurchasesUpdated(billingResult(BillingClient.BillingResponseCode.OK), listOf(purchase))
                assertIs<PurchaseEvent.Success>(awaitItem())
            }
            verify(exactly = 0) { billingClient.acknowledgePurchase(any(), any()) }
        }

    @Test
    fun `onPurchasesUpdated pending purchase emits Pending`() = runTest(mainDispatcherRule.testDispatcher) {
        val purchase = fakePurchase(
            listOf(PremiumTier.MONTHLY.productId),
            purchaseState = Purchase.PurchaseState.PENDING
        )
        manager.purchaseEvents.test {
            manager.onPurchasesUpdated(billingResult(BillingClient.BillingResponseCode.OK), listOf(purchase))
            assertEquals(PurchaseEvent.Pending, awaitItem())
        }
    }

    @Test
    fun `onPurchasesUpdated user cancelled emits Cancelled`() = runTest(mainDispatcherRule.testDispatcher) {
        manager.purchaseEvents.test {
            manager.onPurchasesUpdated(billingResult(BillingClient.BillingResponseCode.USER_CANCELED), null)
            assertEquals(PurchaseEvent.Cancelled, awaitItem())
        }
    }

    @Test
    fun `onPurchasesUpdated network related codes emit NetworkError`() = runTest(mainDispatcherRule.testDispatcher) {
        for (code in listOf(
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingClient.BillingResponseCode.NETWORK_ERROR
        )) {
            manager.purchaseEvents.test {
                manager.onPurchasesUpdated(billingResult(code), null)
                assertEquals(PurchaseEvent.NetworkError, awaitItem())
            }
        }
    }

    @Test
    fun `onPurchasesUpdated unexpected error emits Error with debug message`() = runTest(mainDispatcherRule.testDispatcher) {
        manager.purchaseEvents.test {
            manager.onPurchasesUpdated(billingResult(BillingClient.BillingResponseCode.DEVELOPER_ERROR, "dev oops"), null)
            val event = assertIs<PurchaseEvent.Error>(awaitItem())
            assertEquals("dev oops", event.message)
        }
    }

    @Test
    fun `refreshPurchases (restore) populates purchases without emitting purchase events`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val purchase = fakePurchase(listOf(PremiumTier.YEARLY.productId), acknowledged = true)
            // refreshPurchases() queries SUBS then INAPP in that order; only the first (SUBS)
            // call should return the purchase so it isn't double-counted across both results.
            var queryCallCount = 0
            every { billingClient.queryPurchasesAsync(any<QueryPurchasesParams>(), any()) } answers {
                val result = if (queryCallCount == 0) listOf(purchase) else emptyList()
                queryCallCount++
                secondArg<PurchasesResponseListener>()
                    .onQueryPurchasesResponse(billingResult(BillingClient.BillingResponseCode.OK), result)
            }

            manager.purchases.test {
                assertEquals(emptyList(), awaitItem())
                manager.purchaseEvents.test {
                    manager.refreshPurchases()
                    expectNoEvents()
                }
                val purchases = awaitItem()
                assertEquals(1, purchases.size)
                assertEquals(purchase.purchaseToken, purchases.first().purchaseToken)
            }
            // Restore path never acknowledges on the caller's behalf beyond what's already acknowledged.
            verify(exactly = 0) { billingClient.acknowledgePurchase(any(), any()) }
        }

    @Test
    fun `refreshPurchasesIfReady only refreshes when client is ready`() = runTest(mainDispatcherRule.testDispatcher) {
        every { billingClient.isReady } returns false
        manager.refreshPurchasesIfReady()
        verify(exactly = 0) { billingClient.queryPurchasesAsync(any<QueryPurchasesParams>(), any()) }

        every { billingClient.isReady } returns true
        manager.refreshPurchasesIfReady()
        // Once for SUBS, once for INAPP.
        verify(exactly = 2) { billingClient.queryPurchasesAsync(any<QueryPurchasesParams>(), any()) }
    }

    @Test
    fun `endConnection delegates to BillingClient`() = runTest(mainDispatcherRule.testDispatcher) {
        manager.endConnection()
        verify(exactly = 1) { billingClient.endConnection() }
    }
}
