package com.n3sensei.app.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.n3sensei.app.MainActivity
import com.n3sensei.app.N3SenseiApplication
import com.n3sensei.app.R
import com.n3sensei.app.app.reminder.ReminderDeepLinks
import com.n3sensei.app.domain.reminder.ReminderMessage
import com.n3sensei.app.domain.reminder.ReminderReason
import kotlinx.coroutines.flow.first

/**
 * Home-screen widget "Từ cần học": the same word a reminder would show now, from Room.
 * Refreshed when a reminder run happens and when the user leaves the app; it does not set
 * `updatePeriodMillis`, so it never wakes the device on its own.
 *
 * This is a home-screen widget. Phones do not generally support third-party widgets on the
 * lock screen; the lock-screen experience is the reminder notification.
 */
class StudyWordWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as N3SenseiApplication).container
        val settings = container.userPreferencesRepository.preferences.first().reminderSettings
        val message = runCatching { container.reminderRunner.nextMessage(settings) }.getOrNull()
        provideContent { WidgetContent(context, message) }
    }
}

private val background = ColorProvider(Color(0xFF1E2A5A))
private val primaryText = ColorProvider(Color.White)
private val secondaryText = ColorProvider(Color(0xFFDEE0FF))

@androidx.compose.runtime.Composable
private fun WidgetContent(context: Context, message: ReminderMessage?) {
    val open = if (message != null) {
        actionStartActivity(ReminderDeepLinks.openVocabularyIntent(context, message.vocabulary.id))
    } else {
        actionStartActivity(Intent(context, MainActivity::class.java))
    }
    Column(
        modifier = GlanceModifier.fillMaxSize().background(background).padding(12.dp).clickable(open),
    ) {
        Text(context.getString(R.string.widget_title), style = TextStyle(color = secondaryText, fontSize = 12.sp))
        if (message == null) {
            Text(context.getString(R.string.widget_empty), style = TextStyle(color = primaryText, fontSize = 14.sp))
            return@Column
        }
        val v = message.vocabulary
        Text(v.expression, style = TextStyle(color = primaryText, fontSize = 28.sp, fontWeight = FontWeight.Bold))
        if (message.settings.showReading && v.reading != v.expression) {
            Text(v.reading, style = TextStyle(color = secondaryText, fontSize = 14.sp))
        }
        Text(
            if (message.settings.showMeaning) v.meaningVi else context.getString(R.string.reminder_tap_for_meaning),
            style = TextStyle(color = primaryText, fontSize = 14.sp),
        )
        Text(
            context.getString(
                when (message.reason) {
                    ReminderReason.DUE -> R.string.reminder_reason_due
                    ReminderReason.MARKED -> R.string.reminder_reason_marked
                    ReminderReason.NEW -> R.string.reminder_reason_new
                },
            ),
            style = TextStyle(color = secondaryText, fontSize = 12.sp),
        )
    }
}

class StudyWordWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StudyWordWidget()
}
