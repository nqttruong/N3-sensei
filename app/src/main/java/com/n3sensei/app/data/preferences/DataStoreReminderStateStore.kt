package com.n3sensei.app.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.n3sensei.app.domain.reminder.ReminderState
import com.n3sensei.app.domain.reminder.ReminderStateStore
import kotlinx.coroutines.flow.first
import java.time.Instant

/** Last reminder time and word, kept next to the settings in the same DataStore file. */
class DataStoreReminderStateStore(
    private val dataStore: DataStore<Preferences>,
) : ReminderStateStore {

    private val lastShownAt = longPreferencesKey("reminder_last_shown_at")
    private val lastVocabularyId = stringPreferencesKey("reminder_last_vocabulary_id")

    override suspend fun read(): ReminderState {
        val prefs = dataStore.data.first()
        return ReminderState(prefs[lastShownAt]?.let(Instant::ofEpochMilli), prefs[lastVocabularyId])
    }

    override suspend fun write(state: ReminderState) {
        dataStore.edit { prefs ->
            state.lastShownAt?.let { prefs[lastShownAt] = it.toEpochMilli() } ?: prefs.remove(lastShownAt)
            state.lastVocabularyId?.let { prefs[lastVocabularyId] = it } ?: prefs.remove(lastVocabularyId)
        }
    }
}
