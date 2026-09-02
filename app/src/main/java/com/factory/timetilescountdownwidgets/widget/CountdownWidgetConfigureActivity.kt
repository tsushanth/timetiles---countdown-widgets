package com.factory.timetilescountdownwidgets.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.factory.timetilescountdownwidgets.ui.theme.TimeTilesTheme
import com.factory.timetilescountdownwidgets.util.countdownRepository
import kotlinx.coroutines.launch

class CountdownWidgetConfigureActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val repository = countdownRepository()

        setContent {
            TimeTilesTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val events by repository.observeAll().collectAsState(initial = emptyList())
                    val scope = rememberCoroutineScope()
                    val haptics = LocalHapticFeedback.current

                    Scaffold(topBar = { TopAppBar(title = { Text("Choose a countdown") }) }) { padding ->
                        if (events.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(padding)
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        "No countdowns yet",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        "Create a countdown in TimeTiles first, then add this widget.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(padding)
                            ) {
                                items(events) { event ->
                                    ListItem(
                                        headlineContent = { Text("${event.emoji} ${event.title}") },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .semantics {
                                                contentDescription = "Use ${event.title} for this widget"
                                            }
                                            .clickable {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                scope.launch {
                                                    val glanceId = GlanceAppWidgetManager(this@CountdownWidgetConfigureActivity)
                                                        .getGlanceIdBy(appWidgetId)
                                                    updateAppWidgetState(
                                                        context = this@CountdownWidgetConfigureActivity,
                                                        definition = PreferencesGlanceStateDefinition,
                                                        glanceId = glanceId
                                                    ) { prefs ->
                                                        prefs.toMutablePreferences().apply {
                                                            this[WIDGET_EVENT_ID_KEY] = event.id
                                                        }
                                                    }
                                                    CountdownWidget().update(this@CountdownWidgetConfigureActivity, glanceId)

                                                    val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                                    setResult(Activity.RESULT_OK, resultValue)
                                                    finish()
                                                }
                                            }
                                    )
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
