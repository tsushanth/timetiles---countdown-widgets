package com.factory.timetilescountdownwidgets.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.factory.timetilescountdownwidgets.util.effectiveTargetMillis
import com.factory.timetilescountdownwidgets.util.remainingTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDeleted: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val event = state.event
    val haptics = LocalHapticFeedback.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(event?.title ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (event != null) {
                        IconButton(onClick = { onEdit(event.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit ${event.title}")
                        }
                        IconButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showDeleteDialog = true
                            }
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete ${event.title}")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (event == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Filled.EventBusy,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("This countdown no longer exists.", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onBack) { Text("Go back") }
            }
        } else {
            val accentColor = runCatching { Color(android.graphics.Color.parseColor(event.colorHex)) }
                .getOrDefault(MaterialTheme.colorScheme.primary)

            val effectiveTarget = effectiveTargetMillis(event, state.now)
            val remaining = remainingTime(effectiveTarget, state.now)
            val dateFormat = remember { SimpleDateFormat("EEEE, MMMM d, yyyy 'at' h:mm a", Locale.getDefault()) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .background(accentColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(event.emoji, fontSize = 44.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = dateFormat.format(Date(effectiveTarget)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (remaining.isPast) {
                    Text(
                        text = "Happened ${remaining.days}d ${remaining.hours}h ${remaining.minutes}m ago",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TimeUnitBlock(remaining.days, "days")
                        TimeUnitBlock(remaining.hours, "hours")
                        TimeUnitBlock(remaining.minutes, "min")
                        TimeUnitBlock(remaining.seconds, "sec")
                    }
                }
            }
        }
    }

    if (showDeleteDialog && event != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete this countdown?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    showDeleteDialog = false
                    viewModel.delete(onDeleted)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun TimeUnitBlock(value: Long, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "$value $label"
        }
    ) {
        Text(text = value.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}
