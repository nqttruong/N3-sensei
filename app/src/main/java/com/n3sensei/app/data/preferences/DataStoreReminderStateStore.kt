package com.n3sensei.app.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.reminder.ErrorReminderStateStore
import com.n3sensei.app.domain.reminder.ReminderState
import com.n3sensei.app.domain.reminder.ReminderStateStore
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate

/** Last reminder time and item, and today's count, kept next to the settings in the same DataStore file. */
class DataStoreReminderStateStore(
    private val dataStore: DataStore<Preferences>,
) : ReminderStateStore, ErrorReminderStateStore {

    private val lastShownAt = longPreferencesKey("reminder_last_shown_at")

    /** Written by earlier versions (words only); read when [lastItem] is absent. */
    private val lastVocabularyId = stringPreferencesKey("reminder_last_vocabulary_id")
    private val lastItem = stringPreferencesKey("reminder_last_item")
    private val countDay = stringPreferencesKey("reminder_count_day")
    private val countToday = intPreferencesKey("reminder_count_today")
    private val errorReminderDay = stringPreferencesKey("error_reminder_day")

    override suspend fun lastShownDay(): LocalDate? =
        dataStore.data.first()[errorReminderDay]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    override suspend fun setLastShownDay(day: LocalDate) {
        dataStore.edit { it[errorReminderDay] = day.toString() }
    }

    override suspend fun read(): ReminderState {
        val prefs = dataStore.data.first()
        val item = prefs[lastItem]?.let(::decode) ?: prefs[lastVocabularyId]?.let { ItemRef(ItemType.VOCABULARY, it) }
        return ReminderState(
            lastShownAt = prefs[lastShownAt]?.let(Instant::ofEpochMilli),
            lastItem = item,
            countDay = prefs[countDay]?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            countToday = prefs[countToday] ?: 0,
        )
    }

    override suspend fun write(state: ReminderState) {
        dataStore.edit { prefs ->
            state.lastShownAt?.let { prefs[lastShownAt] = it.toEpochMilli() } ?: prefs.remove(lastShownAt)
            state.lastItem?.let { prefs[lastItem] = "${it.type.name}:${it.id}" } ?: prefs.remove(lastItem)
            prefs.remove(lastVocabularyId)
            state.countDay?.let { prefs[countDay] = it.toString() } ?: prefs.remove(countDay)
            prefs[countToday] = state.countToday
        }
    }

    private fun decode(value: String): ItemRef? {
        val type = ItemType.entries.firstOrNull { value.startsWith(it.name + ":") } ?: return null
        return ItemRef(type, value.removePrefix(type.name + ":"))
    }
}
