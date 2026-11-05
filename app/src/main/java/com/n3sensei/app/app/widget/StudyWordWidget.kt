package com.n3sensei.app.app.widget

import android.content.Context
import android.content.Intent
import android.text.SpannableString
import android.text.Spanned
import android.text.style.LocaleSpan
import android.util.TypedValue
import android.view.Gravity
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.wrapContentHeight
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.n3sensei.app.MainActivity
import com.n3sensei.app.N3SenseiApplication
import com.n3sensei.app.R
import com.n3sensei.app.app.reminder.ReminderDeepLinks
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.practice.PracticeItem
import com.n3sensei.app.domain.widget.WidgetPick
import com.n3sensei.app.domain.widget.WidgetSettings
import com.n3sensei.app.domain.widget.WidgetTap
import com.n3sensei.app.domain.widget.WidgetUpdater
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Home-screen widget of N3 Sensei: one kanji, word or grammar point from Room, chosen by the
 * "Widget" settings ([WidgetSettings]). The same content serves the 1×1 and 2×2 widgets; the
 * layout follows the widget's real size ([SizeMode.Exact]), as launcher cells differ between
 * devices. Each widget keeps the item it shows in its own Glance state, so it stays on that
 * item until the user asks for another (tap or "Đổi") or the settings change.
 *
 * It does not set `updatePeriodMillis` and schedules no work: it is refreshed when its
 * settings change, when the app goes to the background and after a reminder run.
 *
 * This is a home-screen widget. Phones do not generally support third-party widgets on the
 * lock screen; the lock-screen experience is the reminder notification.
 */
open class StudyWordWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as N3SenseiApplication).container
        val settingsFlow = container.userPreferencesRepository.preferences.map { it.widgetSettings }.distinctUntilChanged()
        val initialSettings = settingsFlow.first()
        val initialPick = resolve(context, id, initialSettings)
        // While the widget's session runs, update() recomposes this content without calling
        // provideGlance again: the settings and the shown item are therefore read here, so
        // "Đổi" and a change of settings show at once.
        provideContent {
            val settings by settingsFlow.collectAsState(initialSettings)
            val state = currentState<Preferences>()
            val shownKey = state[KEY_ITEM] to state[KEY_SELECTION]
            val pick by produceState(initialPick, settings, shownKey) { value = resolve(context, id, settings) }
            WidgetContent(context, settings, pick)
        }
    }

    companion object {
        /**
         * The item to show for [settings]: the one shown so far while it still fits (same mode
         * and lesson, still in the database), otherwise a new one, which is saved.
         */
        internal suspend fun resolve(context: Context, id: GlanceId, settings: WidgetSettings): WidgetPick {
            val container = (context.applicationContext as N3SenseiApplication).container
            val state = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
            val shown = state.itemRef().takeIf { state[KEY_SELECTION] == settings.selectionKey() }
            val pick = runCatching { container.widgetContentPicker.current(settings, shown) }.getOrElse { WidgetPick.Empty }
            // Saved only when it changed, so saving does not trigger yet another round.
            if (pick is WidgetPick.Item && (pick.item.ref != shown)) saveShown(context, id, settings, pick.item.ref)
            return pick
        }

        internal val KEY_ITEM = stringPreferencesKey("widget_item")
        internal val KEY_SELECTION = stringPreferencesKey("widget_selection")

        internal suspend fun saveShown(context: Context, id: GlanceId, settings: WidgetSettings, ref: ItemRef) {
            updateAppWidgetState(context, id) { prefs ->
                prefs[KEY_ITEM] = "${ref.type.name}:${ref.id}"
                prefs[KEY_SELECTION] = settings.selectionKey()
            }
        }

        /** Re-renders every N3 Sensei widget on the home screen (both sizes). */
        suspend fun updateAllSizes(context: Context) {
            runCatching { StudyWordWidget().updateAll(context) }
            runCatching { SmallStudyWordWidget().updateAll(context) }
        }
    }
}

/** The 1×1 widget: same content, its own receiver so it is listed separately in the picker. */
class SmallStudyWordWidget : StudyWordWidget()

/** 2×2 (resizable). Kept under its first name so widgets already on home screens keep working. */
class StudyWordWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StudyWordWidget()
}

class SmallStudyWordWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SmallStudyWordWidget()
}

/** "Đổi" and, when chosen in the settings, a tap on the widget: shows another item. */
class NextWidgetItemAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val container = (context.applicationContext as N3SenseiApplication).container
        val settings = container.userPreferencesRepository.preferences.first().widgetSettings
        val state = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        val pick = runCatching { container.widgetContentPicker.next(settings, state.itemRef()) }.getOrNull()
        if (pick is WidgetPick.Item) StudyWordWidget.saveShown(context, glanceId, settings, pick.item.ref)
        // Either class renders either widget: the layout depends on the size only.
        StudyWordWidget().update(context, glanceId)
    }
}

/** Refreshes the widgets when their settings change (the feature layer only sees [WidgetUpdater]). */
class GlanceWidgetUpdater(private val context: Context, private val launch: (suspend () -> Unit) -> Unit) : WidgetUpdater {
    override fun refresh() = launch { StudyWordWidget.updateAllSizes(context) }
}

private fun Preferences.itemRef(): ItemRef? {
    val value = this[StudyWordWidget.KEY_ITEM] ?: return null
    val type = ItemType.entries.firstOrNull { value.startsWith(it.name + ":") } ?: return null
    return ItemRef(type, value.removePrefix(type.name + ":"))
}

// Paper and ink of the app's palette, day and night.
private val background = ColorProvider(day = Color(0xFFFBF9F4), night = Color(0xFF1F201C))
private val ink = ColorProvider(day = Color(0xFF1B1C18), night = Color(0xFFE5E3DB))
private val muted = ColorProvider(day = Color(0xFF47473F), night = Color(0xFFC9C7BB))
private val accent = ColorProvider(day = Color(0xFF4A653A), night = Color(0xFFB0CF9C))

/** Below this (a 1×1 cell on most launchers) only the item itself is shown. */
private val COMPACT = DpSize(120.dp, 120.dp)

@Composable
private fun WidgetContent(context: Context, settings: WidgetSettings, pick: WidgetPick) {
    val size = LocalSize.current
    val compact = size.width < COMPACT.width || size.height < COMPACT.height
    val item = (pick as? WidgetPick.Item)?.item
    val open = if (item != null) {
        actionStartActivity(ReminderDeepLinks.itemIntent(context, item.ref))
    } else {
        actionStartActivity(Intent(context, MainActivity::class.java))
    }
    val tap = if (item != null && settings.tap == WidgetTap.NEXT) actionRunCallback<NextWidgetItemAction>() else open
    Column(
        modifier = GlanceModifier.fillMaxSize().background(background).cornerRadius(16.dp)
            .padding(if (compact) 6.dp else 12.dp).clickable(tap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = if (compact) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        if (pick !is WidgetPick.Item) {
            Text(
                context.getString(if (compact) R.string.widget_empty_short else R.string.widget_empty),
                style = TextStyle(color = muted, fontSize = if (compact) 11.sp else 14.sp, textAlign = if (compact) TextAlign.Center else TextAlign.Start),
                maxLines = 4,
            )
            return@Column
        }
        val face = face(context, pick.item)
        if (compact) {
            if (settings.showFurigana && face.reading != null && size.height >= 72.dp) {
                Text(face.reading, style = TextStyle(color = muted, fontSize = 10.sp, textAlign = TextAlign.Center), maxLines = 1)
            }
            JapaneseText(context, face.text, mainSize(face.text, compact = true), centered = true)
            return@Column
        }
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label(context, face.type, pick.lesson),
                style = TextStyle(color = accent, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                context.getString(R.string.widget_next),
                style = TextStyle(color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp).clickable(actionRunCallback<NextWidgetItemAction>()),
            )
        }
        Spacer(GlanceModifier.height(4.dp))
        if (settings.showFurigana && face.reading != null) {
            Text(face.reading, style = TextStyle(color = muted, fontSize = 13.sp), maxLines = 1)
        }
        JapaneseText(context, face.text, mainSize(face.text, compact = false), centered = false)
        Spacer(GlanceModifier.height(2.dp))
        Text(
            if (settings.showMeaning) face.meaning else context.getString(R.string.widget_tap_for_answer),
            style = TextStyle(color = if (settings.showMeaning) ink else muted, fontSize = 13.sp),
            maxLines = 3,
        )
    }
}

/**
 * The item's Japanese text, marked Japanese with a [LocaleSpan] so Han characters take their
 * Japanese forms (戻, 直, 骨), as `japanese()` does in the app.
 */
@Composable
private fun JapaneseText(context: Context, text: String, size: TextUnit, centered: Boolean) {
    val spanned = SpannableString(text).apply { setSpan(LocaleSpan(Locale.JAPAN), 0, length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE) }
    val views = RemoteViews(context.packageName, R.layout.widget_japanese_text).apply {
        setTextViewText(R.id.widget_japanese_text, spanned)
        setTextViewTextSize(R.id.widget_japanese_text, TypedValue.COMPLEX_UNIT_SP, size.value)
        setInt(R.id.widget_japanese_text, "setMaxLines", 2)
        setInt(R.id.widget_japanese_text, "setGravity", if (centered) Gravity.CENTER_HORIZONTAL else Gravity.START)
    }
    // Only as tall as the text, so the lines below it keep their place.
    AndroidRemoteViews(views, modifier = GlanceModifier.fillMaxWidth().wrapContentHeight())
}

/** What the widget shows of an item, as stored: the text, its reading (furigana) and its meaning. */
private data class Face(val type: ItemType, val text: String, val reading: String?, val meaning: String)

private fun face(context: Context, item: PracticeItem): Face = when (item) {
    is PracticeItem.VocabularyItem -> item.vocabulary.let { v ->
        Face(ItemType.VOCABULARY, v.expression, v.reading.takeIf { it != v.expression }, v.meaningVi)
    }
    is PracticeItem.KanjiItem -> item.kanji.let { k ->
        Face(
            ItemType.KANJI, k.character,
            (k.onReadings + k.kunReadings).joinToString("、").ifEmpty { null },
            listOfNotNull(k.hanViet, k.meaningVi).joinToString(" · ").ifEmpty { context.getString(R.string.common_none) },
        )
    }
    is PracticeItem.GrammarItem -> Face(ItemType.GRAMMAR, item.grammar.pattern, null, item.grammar.meaningVi)
}

private fun label(context: Context, type: ItemType, lesson: Int?): String {
    val kind = context.getString(
        when (type) {
            ItemType.VOCABULARY -> R.string.totals_vocabulary
            ItemType.KANJI -> R.string.totals_kanji
            ItemType.GRAMMAR -> R.string.totals_grammar
        },
    )
    return lesson?.let { context.getString(R.string.location_in_course, context.getString(R.string.course_lesson_title, it), kind) } ?: kind
}

/** Smaller type for longer text, so a word or pattern fits without being cut. */
private fun mainSize(text: String, compact: Boolean): TextUnit = when {
    compact && text.length <= 1 -> 34.sp
    compact && text.length <= 3 -> 20.sp
    compact -> 14.sp
    text.length <= 1 -> 44.sp
    text.length <= 4 -> 30.sp
    text.length <= 8 -> 22.sp
    else -> 17.sp
}
