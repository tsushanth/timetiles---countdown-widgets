package com.factory.timetilescountdownwidgets.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.factory.timetilescountdownwidgets.billing.BillingManager
import com.factory.timetilescountdownwidgets.billing.PremiumManager
import com.factory.timetilescountdownwidgets.util.isOnline
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    billingManager: BillingManager,
    premiumManager: PremiumManager,
    onUpgradeClick: () -> Unit,
    onBack: () -> Unit
) {
    val isPremium by premiumManager.isPremium.collectAsState()
    val activeTier by premiumManager.activeTier.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = if (isPremium) {
                            "You're a Premium member"
                        } else {
                            "Upgrade to Premium"
                        }
                    }
                    .clickable(enabled = !isPremium) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onUpgradeClick()
                    },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        if (isPremium) "You're a Premium member ✨" else "Upgrade to Premium",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (isPremium) {
                            "Plan: ${activeTier?.displayName ?: "Premium"}"
                        } else {
                            "Unlock unlimited countdowns, recurring events, full customization, and remove ads."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Restore purchases", style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    scope.launch {
                        if (!context.isOnline()) {
                            snackbarHostState.showSnackbar("No internet connection. Please check your network and try again.")
                            return@launch
                        }
                        billingManager.refreshPurchases()
                        val message = if (premiumManager.isPremium.value || premiumManager.adsRemoved.value) {
                            "Purchases restored."
                        } else {
                            "No previous purchases found."
                        }
                        snackbarHostState.showSnackbar(message)
                    }
                }) {
                    Text("Restore")
                }
            }
        }
    }
}
