package com.factory.timetilescountdownwidgets.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.factory.timetilescountdownwidgets.data.CountdownEvent
import com.factory.timetilescountdownwidgets.premium.FreeTierLimits
import com.factory.timetilescountdownwidgets.ui.components.CountdownCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    isPremium: Boolean,
    adsRemoved: Boolean,
    onAddClick: () -> Unit,
    onItemClick: (Long) -> Unit,
    onUpgradeRequired: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val events by viewModel.uiState.collectAsState()
    var pendingDelete by remember { mutableStateOf<CountdownEvent?>(null) }
    val canAddMore = isPremium || events.size < FreeTierLimits.MAX_COUNTDOWNS
    val haptics = LocalHapticFeedback.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TimeTiles") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (canAddMore) onAddClick() else onUpgradeRequired()
                }
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add countdown")
            }
        }
    ) { padding ->
        if (events.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.EventNote,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No countdowns yet",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Add your first event to start counting down.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (canAddMore) onAddClick() else onUpgradeRequired()
                    }
                ) {
                    Text("Add a countdown")
                }
            }
        } else {
            val now = System.currentTimeMillis()
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!adsRemoved) {
                    item { RemoveAdsBanner(onClick = onUpgradeRequired) }
                }
                if (!isPremium) {
                    item {
                        Text(
                            "${events.size}/${FreeTierLimits.MAX_COUNTDOWNS} free countdowns used",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(events, key = { it.event.id }) { uiModel ->
                    CountdownCard(
                        event = uiModel.event,
                        effectiveTargetMillis = uiModel.effectiveTargetMillis,
                        now = now,
                        onClick = { onItemClick(uiModel.event.id) },
                        onDelete = { pendingDelete = uiModel.event }
                    )
                }
            }
        }
    }

    pendingDelete?.let { event ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${event.title}?") },
            text = { Text("This countdown will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.deleteEvent(event)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun RemoveAdsBanner(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Enjoying TimeTiles? Remove ads for $1.99" }
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Box(modifier = Modifier.padding(12.dp)) {
            Text(
                "Enjoying TimeTiles? Remove ads for $1.99 →",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
