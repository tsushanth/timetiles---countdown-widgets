package com.factory.timetilescountdownwidgets.ui.paywall

import android.app.Activity
import app.cash.turbine.test
import com.android.billingclient.api.ProductDetails
import com.factory.timetilescountdownwidgets.billing.BillingConnectionState
import com.factory.timetilescountdownwidgets.billing.BillingManager
import com.factory.timetilescountdownwidgets.billing.PremiumManager
import com.factory.timetilescountdownwidgets.billing.PremiumTier
import com.factory.timetilescountdownwidgets.billing.PurchaseEvent
import com.factory.timetilescountdownwidgets.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PaywallViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var billingManager: BillingManager
    private lateinit var premiumManager: PremiumManager

    private lateinit var connectionState: MutableStateFlow<BillingConnectionState>
    private lateinit var productDetails: MutableStateFlow<Map<String, ProductDetails>>
    private lateinit var purchaseEvents: MutableSharedFlow<PurchaseEvent>
    private lateinit var isPremium: MutableStateFlow<Boolean>
    private lateinit var adsRemoved: MutableStateFlow<Boolean>
    private lateinit var activeTier: MutableStateFlow<PremiumTier?>

    @Before
    fun setUp() {
        connectionState = MutableStateFlow(BillingConnectionState.Connecting)
        productDetails = MutableStateFlow(emptyMap())
        purchaseEvents = MutableSharedFlow(extraBufferCapacity = 4)
        isPremium = MutableStateFlow(false)
        adsRemoved = MutableStateFlow(false)
        activeTier = MutableStateFlow<PremiumTier?>(null)

        billingManager = mockk(relaxed = true)
        every { billingManager.connectionState } returns connectionState
        every { billingManager.productDetails } returns productDetails
        every { billingManager.purchaseEvents } returns purchaseEvents

        premiumManager = mockk(relaxed = true)
        every { premiumManager.isPremium } returns isPremium
        every { premiumManager.adsRemoved } returns adsRemoved
        every { premiumManager.activeTier } returns activeTier
    }

    @Test
    fun `starts a billing connection when not already connected`() = runTest(mainDispatcherRule.testDispatcher) {
        PaywallViewModel(billingManager, premiumManager)
        verify(exactly = 1) { billingManager.startConnection() }
    }

    @Test
    fun `re-queries product details instead of reconnecting when already connected`() =
        runTest(mainDispatcherRule.testDispatcher) {
            connectionState.value = BillingConnectionState.Connected

            PaywallViewModel(billingManager, premiumManager)

            verify(exactly = 0) { billingManager.startConnection() }
            coVerify(exactly = 1) { billingManager.queryProductDetails() }
        }

    @Test
    fun `uiState reflects premium status and active tier changes`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = PaywallViewModel(billingManager, premiumManager)
        viewModel.uiState.test {
            assertFalse(awaitItem().isPremium)

            // Each write below runs synchronously against the Unconfined dispatcher, and the
            // combine() collector wakes up inline per-source-flow-update rather than batching
            // consecutive writes — so each needs its own awaitItem() rather than one combined await.
            isPremium.value = true
            assertTrue(awaitItem().isPremium)

            activeTier.value = PremiumTier.YEARLY
            assertEquals(PremiumTier.YEARLY, awaitItem().activeTier)
        }
    }

    @Test
    fun `purchase delegates to billingManager launchPurchaseFlow`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = PaywallViewModel(billingManager, premiumManager)
        val activity = mockk<Activity>(relaxed = true)

        viewModel.purchase(activity, PremiumTier.MONTHLY.productId)

        verify(exactly = 1) { billingManager.launchPurchaseFlow(activity, PremiumTier.MONTHLY.productId) }
    }

    @Test
    fun `restorePurchases shows progress then a success message when entitlements are found`() =
        runTest(mainDispatcherRule.testDispatcher) {
            // yield() forces a real suspension between the "Restoring…" write and the final
            // message write below, so the combine() collector gets scheduled in between and
            // both messages surface as distinct uiState emissions instead of the second one
            // overwriting the first before it's ever collected.
            coEvery { billingManager.refreshPurchases() } coAnswers {
                yield()
                isPremium.value = true
            }
            val viewModel = PaywallViewModel(billingManager, premiumManager)

            viewModel.uiState.test {
                awaitItem()
                viewModel.restorePurchases()
                assertEquals("Restoring purchases…", awaitItem().message)
                assertEquals("Purchases restored.", awaitItem().message)
            }
        }

    @Test
    fun `restorePurchases shows a not-found message when nothing to restore`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { billingManager.refreshPurchases() } coAnswers { yield() }
            val viewModel = PaywallViewModel(billingManager, premiumManager)

            viewModel.uiState.test {
                awaitItem()
                viewModel.restorePurchases()
                assertEquals("Restoring purchases…", awaitItem().message)
                assertEquals("No previous purchases found.", awaitItem().message)
            }
        }

    @Test
    fun `retryConnection delegates to billingManager startConnection`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = PaywallViewModel(billingManager, premiumManager)

        viewModel.retryConnection()

        // once from init (state starts Connecting, not Connected) + once from the explicit retry
        verify(exactly = 2) { billingManager.startConnection() }
    }

    @Test
    fun `dismissMessage clears the message`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = PaywallViewModel(billingManager, premiumManager)

        viewModel.uiState.test {
            awaitItem()
            purchaseEvents.emit(PurchaseEvent.Pending)
            awaitItem()

            viewModel.dismissMessage()
            assertNull(awaitItem().message)
        }
    }

    @Test
    fun `purchase success event sets a success message`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = PaywallViewModel(billingManager, premiumManager)

        viewModel.uiState.test {
            awaitItem()
            purchaseEvents.emit(PurchaseEvent.Success(listOf(PremiumTier.MONTHLY.productId)))
            assertEquals("Purchase successful — premium unlocked!", awaitItem().message)
        }
    }

    @Test
    fun `purchase cancelled event clears the message`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = PaywallViewModel(billingManager, premiumManager)

        viewModel.uiState.test {
            awaitItem()
            purchaseEvents.emit(PurchaseEvent.Error("boom"))
            awaitItem()

            purchaseEvents.emit(PurchaseEvent.Cancelled)
            assertNull(awaitItem().message)
        }
    }

    @Test
    fun `purchase error event sets the error message`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = PaywallViewModel(billingManager, premiumManager)

        viewModel.uiState.test {
            awaitItem()
            purchaseEvents.emit(PurchaseEvent.Error("boom"))
            assertEquals("Something went wrong: boom", awaitItem().message)
        }
    }

    @Test
    fun `purchase pending event sets the pending message`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = PaywallViewModel(billingManager, premiumManager)

        viewModel.uiState.test {
            awaitItem()
            purchaseEvents.emit(PurchaseEvent.Pending)
            assertEquals(
                "Your purchase is pending confirmation. Premium will unlock automatically once it clears.",
                awaitItem().message
            )
        }
    }

    @Test
    fun `purchase network error event sets the network error message`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = PaywallViewModel(billingManager, premiumManager)

        viewModel.uiState.test {
            awaitItem()
            purchaseEvents.emit(PurchaseEvent.NetworkError)
            assertEquals(
                "No internet connection. Please check your network and try again.",
                awaitItem().message
            )
        }
    }
}
