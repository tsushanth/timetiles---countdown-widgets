package com.factory.timetilescountdownwidgets.ui.addedit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.factory.timetilescountdownwidgets.premium.FreeTierLimits
import com.factory.timetilescountdownwidgets.ui.components.ProBadge
import com.factory.timetilescountdownwidgets.ui.theme.EmojiOptions
import com.factory.timetilescountdownwidgets.ui.theme.TileColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditScreen(
    viewModel: AddEditViewModel,
    isPremium: Boolean,
    onDone: () -> Unit,
    onBack: () -> Unit,
    onUpgradeRequired: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val titleFocusRequester = remember { FocusRequester() }
    val snackbarHostState = remember { SnackbarHostState() }
    var titleTouched by remember { mutableStateOf(false) }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onDone()
    }

    LaunchedEffect(state.saveErrorMessage) {
        state.saveErrorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissSaveError()
        }
    }

    LaunchedEffect(state.isLoading, state.eventId) {
        if (!state.isLoading && state.eventId == null) {
            titleFocusRequester.requestFocus()
        }
    }

    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (state.loadError) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Edit Countdown") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("This countdown no longer exists.", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onBack) { Text("Go back") }
            }
        }
        return
    }

    val calendar = remember(state.targetDateTime) {
        Calendar.getInstance().apply { timeInMillis = state.targetDateTime }
    }
    val dateFormat = remember { SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.eventId == null) "New Countdown" else "Edit Countdown") },
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
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::updateTitle,
                label = { Text("Title") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(titleFocusRequester)
                    .onFocusChanged { if (!it.isFocused) titleTouched = true }
                    .semantics { contentDescription = "Countdown title" },
                singleLine = true,
                isError = titleTouched && state.title.isEmpty(),
                supportingText = {
                    if (titleTouched && state.title.isEmpty()) {
                        Text("Title is required")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    }
                )
            )

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Emoji", style = MaterialTheme.typography.labelLarge)
                    if (!isPremium) {
                        Box(modifier = Modifier.padding(start = 8.dp)) { ProBadge() }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(EmojiOptions) { emoji ->
                        val index = EmojiOptions.indexOf(emoji)
                        val locked = !isPremium && index >= FreeTierLimits.FREE_EMOJI_COUNT
                        val selected = emoji == state.emoji
                        val description = when {
                            locked -> "Emoji $emoji, locked, upgrade to Premium to use"
                            selected -> "Emoji $emoji, selected"
                            else -> "Emoji $emoji"
                        }
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    width = if (selected) 2.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape
                                )
                                .alpha(if (locked) 0.4f else 1f)
                                .semantics {
                                    contentDescription = description
                                    stateDescription = if (selected) "Selected" else ""
                                }
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (locked) onUpgradeRequired() else viewModel.updateEmoji(emoji)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji, fontSize = 22.sp)
                        }
                    }
                }
            }

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Color", style = MaterialTheme.typography.labelLarge)
                    if (!isPremium) {
                        Box(modifier = Modifier.padding(start = 8.dp)) { ProBadge() }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(TileColors) { hex ->
                        val index = TileColors.indexOf(hex)
                        val locked = !isPremium && index >= FreeTierLimits.FREE_COLOR_COUNT
                        val selected = hex == state.colorHex
                        val description = when {
                            locked -> "Color swatch $hex, locked, upgrade to Premium to use"
                            selected -> "Color swatch $hex, selected"
                            else -> "Color swatch $hex"
                        }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(hex)))
                                .border(
                                    width = if (selected) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape
                                )
                                .alpha(if (locked) 0.4f else 1f)
                                .semantics {
                                    contentDescription = description
                                    stateDescription = if (selected) "Selected" else ""
                                }
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (locked) onUpgradeRequired() else viewModel.updateColor(hex)
                                }
                        )
                    }
                }
            }

            Column {
                Text("Date & time", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showDatePicker = true
                        },
                        modifier = Modifier.semantics {
                            contentDescription = "Date, ${dateFormat.format(calendar.time)}"
                        }
                    ) {
                        Text(dateFormat.format(calendar.time))
                    }
                    Button(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showTimePicker = true
                        },
                        modifier = Modifier.semantics {
                            contentDescription = "Time, ${timeFormat.format(calendar.time)}"
                        }
                    ) {
                        Text(timeFormat.format(calendar.time))
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Repeats yearly (birthday/anniversary)", style = MaterialTheme.typography.bodyMedium)
                    if (!isPremium) {
                        Box(modifier = Modifier.padding(start = 8.dp)) { ProBadge() }
                    }
                }
                Switch(
                    checked = state.repeatsYearly,
                    onCheckedChange = { checked ->
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (!isPremium && checked) onUpgradeRequired() else viewModel.updateRepeatsYearly(checked)
                    },
                    modifier = Modifier.semantics {
                        contentDescription = if (!isPremium) {
                            "Repeats yearly, requires Premium"
                        } else {
                            "Repeats yearly"
                        }
                        stateDescription = if (state.repeatsYearly) "On" else "Off"
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    viewModel.save()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.title.isNotBlank()
            ) {
                Text("Save")
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = state.targetDateTime)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { pickedUtcMillis ->
                        val newCal = Calendar.getInstance().apply {
                            timeInMillis = state.targetDateTime
                        }
                        val pickedCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                            timeInMillis = pickedUtcMillis
                        }
                        newCal.set(Calendar.YEAR, pickedCal.get(Calendar.YEAR))
                        newCal.set(Calendar.MONTH, pickedCal.get(Calendar.MONTH))
                        newCal.set(Calendar.DAY_OF_MONTH, pickedCal.get(Calendar.DAY_OF_MONTH))
                        viewModel.updateTargetDateTime(newCal.timeInMillis)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = calendar.get(Calendar.HOUR_OF_DAY),
            initialMinute = calendar.get(Calendar.MINUTE),
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val newCal = Calendar.getInstance().apply {
                        timeInMillis = state.targetDateTime
                        set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                        set(Calendar.MINUTE, timePickerState.minute)
                        set(Calendar.SECOND, 0)
                    }
                    viewModel.updateTargetDateTime(newCal.timeInMillis)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
            text = {
                TimePicker(state = timePickerState)
            }
        )
    }
}
