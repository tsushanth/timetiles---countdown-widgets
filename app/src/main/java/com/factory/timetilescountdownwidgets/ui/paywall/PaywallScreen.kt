package com.factory.timetilescountdownwidgets.ui.paywall

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.factory.timetilescountdownwidgets.billing.BillingConnectionState
import com.factory.timetilescountdownwidgets.billing.PaywallProduct
import com.factory.timetilescountdownwidgets.billing.PremiumTier
import com.factory.timetilescountdownwidgets.premium.PremiumFeature
import com.factory.timetilescountdownwidgets.util.isOnline

/** Placeholder legal URLs — replace with your published Terms of Service and Privacy Policy before release. */
private const val TERMS_OF_SERVICE_URL = "https://your-domain.example/terms"
private const val PRIVACY_POLICY_URL = "https://your-domain.example/privacy"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    viewModel: PaywallViewModel,
    onClose: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    LaunchedEffect(state.isPremium) {
        if (state.isPremium) onClose()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TimeTiles Premium") },
                actions = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(snackbarData = data)
            }
        }
    ) { padding ->
        when {
            !context.isOnline() -> OfflineContent(
                modifier = Modifier.padding(padding),
                onRetry = viewModel::retryConnection
            )
            state.connectionState is BillingConnectionState.Error ||
                state.connectionState is BillingConnectionState.Disconnected -> ConnectionErrorContent(
                modifier = Modifier.padding(padding),
                onRetry = viewModel::retryConnection
            )
            state.connectionState is BillingConnectionState.Connecting && state.subscriptionProducts.all { it.formattedPrice == null } -> LoadingContent(
                modifier = Modifier.padding(padding)
            )
            else -> {
                val haptics = LocalHapticFeedback.current
                PaywallContent(
                    modifier = Modifier.padding(padding),
                    state = state,
                    onSubscribe = { productId ->
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        (context as? Activity)?.let { viewModel.purchase(it, productId) }
                    },
                    onRestore = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.restorePurchases()
                    }
                )
            }
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun OfflineContent(modifier: Modifier = Modifier, onRetry: () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.CloudOff, contentDescription = null, modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text("You're offline", style = MaterialTheme.typography.titleMedium)
        Text(
            "Connect to the internet to view premium plans.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun ConnectionErrorContent(modifier: Modifier = Modifier, onRetry: () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Couldn't connect to Google Play", style = MaterialTheme.typography.titleMedium)
        Text(
            "Please try again in a moment.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun PaywallContent(
    modifier: Modifier = Modifier,
    state: PaywallUiState,
    onSubscribe: (String) -> Unit,
    onRestore: () -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Unlock everything TimeTiles has to offer",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        items(PremiumFeature.entries.toList()) { feature ->
            FeatureRow(feature)
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        items(state.subscriptionProducts) { product ->
            SubscriptionRow(
                product = product,
                isActive = state.activeTier == product.tier,
                onSubscribe = { onSubscribe(product.productId) }
            )
        }

        state.removeAdsProduct?.let { removeAds ->
            item {
                Spacer(modifier = Modifier.height(4.dp))
                RemoveAdsRow(product = removeAds, onPurchase = { onSubscribe(removeAds.productId) })
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
                Text("Restore purchases")
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(onClick = { openUrl(context, TERMS_OF_SERVICE_URL) }) {
                    Text("Terms of Service", style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = { openUrl(context, PRIVACY_POLICY_URL) }) {
                    Text("Privacy Policy", style = MaterialTheme.typography.labelSmall)
                }
            }
            Text(
                "Subscriptions auto-renew until cancelled. Cancel anytime in Google Play.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun FeatureRow(feature: PremiumFeature) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp)
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(feature.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                feature.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SubscriptionRow(product: PaywallProduct, isActive: Boolean, onSubscribe: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = if (isActive) {
                    "${product.title}, active plan"
                } else {
                    "${product.title}, ${product.displayPrice}"
                }
            }
            .clickable(enabled = !isActive, onClick = onSubscribe),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(product.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (product.tier == PremiumTier.LIFETIME) "One-time purchase" else "Auto-renewing",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isActive) {
                Text("Active", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            } else {
                Button(onClick = onSubscribe) {
                    Text(product.displayPrice)
                }
            }
        }
    }
}

@Composable
private fun RemoveAdsRow(product: PaywallProduct, onPurchase: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "${product.title}, ${product.displayPrice}" }
            .clickable(onClick = onPurchase),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(product.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "One-time purchase",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = onPurchase) {
                Text(product.displayPrice)
            }
        }
    }
}

private fun openUrl(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
