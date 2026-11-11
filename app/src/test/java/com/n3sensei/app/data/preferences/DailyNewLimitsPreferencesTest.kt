package com.n3sensei.app.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.n3sensei.app.domain.srs.DailyLimits
import com.n3sensei.app.domain.srs.ReviewSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The three daily new-item caps in a real Preferences DataStore file, and settings saved before them. */
class DailyNewLimitsPreferencesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val file by lazy { File(folder.root, "prefs.preferences_pb") }

    /** Opens the file (as after an app restart), runs [block], then closes it. */
    private fun <T> withStore(block: suspend (DataStore<Preferences>) -> T): T {
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)
        return runBlocking {
            try {
                block(PreferenceDataStoreFactory.create(scope = scope) { file })
            } finally {
                job.cancelAndJoin()
            }
        }
    }

    private fun limits() = withStore { DataStoreUserPreferencesRepository(it).preferences.first().reviewSettings.limits }

    @Test
    fun defaults_are20_10_10() {
        assertEquals(DailyLimits(newVocabulary = 20, newKanji = 10, newGrammar = 10, reviewsPerDay = 200), limits())
    }

    @Test
    fun theThreeCaps_areSavedApart_andKeptAfterARestart() {
        withStore { DataStoreUserPreferencesRepository(it).setReviewSettings(ReviewSettings(limits = DailyLimits(newVocabulary = 0, newKanji = 100, newGrammar = 7, reviewsPerDay = 300))) }
        assertEquals(DailyLimits(newVocabulary = 0, newKanji = 100, newGrammar = 7, reviewsPerDay = 300), limits())
        // Changing one leaves the others as saved.
        withStore {
            val repo = DataStoreUserPreferencesRepository(it)
            val current = repo.preferences.first().reviewSettings
            repo.setReviewSettings(current.copy(limits = current.limits.copy(newGrammar = 15)))
        }
        assertEquals(DailyLimits(newVocabulary = 0, newKanji = 100, newGrammar = 15, reviewsPerDay = 300), limits())
    }

    @Test
    fun settingsFromBeforePerTypeCaps_keepTheirNumberForVocabulary_kanjiAndGrammarStartAt10() {
        // As written by the previous version: one shared cap, an edited ladder.
        withStore { store ->
            store.edit {
                it[intPreferencesKey("new_per_day")] = 35
                it[intPreferencesKey("reviews_per_day")] = 150
                it[stringPreferencesKey("review_intervals")] = "10m,1d,3d"
            }
        }
        val settings = withStore { DataStoreUserPreferencesRepository(it).preferences.first().reviewSettings }
        assertEquals(DailyLimits(newVocabulary = 35, newKanji = 10, newGrammar = 10, reviewsPerDay = 150), settings.limits)
        assertEquals("10m,1d,3d", settings.ladder.encode())
    }

    @Test
    fun anOldSharedCapAbove100_isClampedTo100_and0StaysOff() {
        withStore { store -> store.edit { it[intPreferencesKey("new_per_day")] = 180 } }
        assertEquals(100, limits().newVocabulary)
        withStore { store -> store.edit { it[intPreferencesKey("new_per_day")] = 0 } }
        assertEquals(0, limits().newVocabulary)
        // Once saved with per-type caps, the old key no longer decides anything.
        withStore { DataStoreUserPreferencesRepository(it).setReviewSettings(ReviewSettings(limits = DailyLimits(newVocabulary = 12))) }
        withStore { store -> store.edit { it[intPreferencesKey("new_per_day")] = 90 } }
        assertEquals(12, limits().newVocabulary)
    }

    @Test
    fun handEditedOutOfRangeValues_fallBackToTheDefaults() {
        withStore { store ->
            store.edit {
                it[intPreferencesKey("new_kanji_per_day")] = 500
                it[intPreferencesKey("new_grammar_per_day")] = -3
            }
        }
        assertEquals(10, limits().newKanji)
        assertEquals(10, limits().newGrammar)
    }
}
