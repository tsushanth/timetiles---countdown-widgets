package com.factory.timetilescountdownwidgets.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.GlanceTheme
import androidx.glance.layout.padding
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.factory.timetilescountdownwidgets.MainActivity
import com.factory.timetilescountdownwidgets.data.CountdownEvent
import com.factory.timetilescountdownwidgets.util.countdownRepository
import com.factory.timetilescountdownwidgets.util.effectiveTargetMillis
import com.factory.timetilescountdownwidgets.util.remainingTime

val WIDGET_EVENT_ID_KEY = longPreferencesKey("selected_event_id")

class CountdownWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Single
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = context.countdownRepository()
        val allEvents = repository.getAllOnce()

        provideContent {
            val prefs = currentState<Preferences>()
            val selectedId = prefs[WIDGET_EVENT_ID_KEY]
            val event = allEvents.firstOrNull { it.id == selectedId }
                ?: allEvents.minByOrNull { effectiveTargetMillis(it, System.currentTimeMillis()) }

            GlanceTheme {
                WidgetContent(event)
            }
        }
    }
}

@Composable
private fun WidgetContent(event: CountdownEvent?) {
    val now = System.currentTimeMillis()
    val openAppDescription = if (event == null) {
        "Open TimeTiles to add a countdown"
    } else {
        "Open TimeTiles, ${event.emoji} ${event.title}"
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .padding(16.dp)
            .semantics { contentDescription = openAppDescription }
            .clickable(actionStartActivity<MainActivity>()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (event == null) {
            Text(
                text = "Add a countdown in TimeTiles",
                style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 14.sp)
            )
        } else {
            val target = effectiveTargetMillis(event, now)
            val remaining = remainingTime(target, now)

            Text(
                text = "${event.emoji} ${event.title}",
                style = TextStyle(
                    color = GlanceTheme.colors.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            )
            Text(
                text = if (remaining.isPast) "Today!" else "${remaining.days}d ${remaining.hours}h ${remaining.minutes}m",
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}
