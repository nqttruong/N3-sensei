package com.n3sensei.app.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.n3sensei.app.domain.reminder.LessonSelection
import com.n3sensei.app.domain.srs.DailyLimits
import com.n3sensei.app.domain.srs.KanjiReviewLessons
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

/** The kanji lessons chosen for review, in a real Preferences DataStore file across restarts. */
class KanjiReviewLessonsPreferencesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val file by lazy { File(folder.root, "prefs.preferences_pb") }

    private fun <T> withStore(block: suspend (DataStoreUserPreferencesRepository) -> T): T {
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)
        return runBlocking {
            try {
                val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) { file }
                block(DataStoreUserPreferencesRepository(store))
            } finally {
                job.cancelAndJoin()
            }
        }
    }

    private fun read() = withStore { it.preferences.first().kanjiReviewLessons }

    @Test
    fun nothingSaved_n3EveryLesson_n2None() {
        assertEquals(LessonSelection.All, read().of("N3"))
        assertEquals(LessonSelection.Only(emptySet()), read().of("N2"))
    }

    @Test
    fun theChoice_isKeptAfterARestart_perLevel() {
        withStore {
            it.setKanjiReviewLessons(
                KanjiReviewLessons().with("N2", LessonSelection.Only(setOf("n2k-l01", "n2k-l03"))).with("N3", LessonSelection.Only(setOf("n3k-l02"))),
            )
        }
        assertEquals(LessonSelection.Only(setOf("n2k-l01", "n2k-l03")), read().of("N2"))
        assertEquals(LessonSelection.Only(setOf("n3k-l02")), read().of("N3"))
        // All N2 lessons, and N3 back to its default.
        withStore { it.setKanjiReviewLessons(read().with("N2", LessonSelection.All).with("N3", LessonSelection.All)) }
        assertEquals(LessonSelection.All, read().of("N2"))
        assertEquals(LessonSelection.All, read().of("N3"))
        // None again: an empty choice is kept as such, not taken for the default.
        withStore { it.setKanjiReviewLessons(read().with("N2", LessonSelection.Only(emptySet())).with("N3", LessonSelection.Only(emptySet()))) }
        assertEquals(LessonSelection.Only(emptySet()), read().of("N2"))
        assertEquals(LessonSelection.Only(emptySet()), read().of("N3"))
    }

    @Test
    fun savingReviewSettings_leavesTheLessonsAlone_andTheirsTheLimits() {
        withStore { it.setKanjiReviewLessons(KanjiReviewLessons().with("N2", LessonSelection.Only(setOf("n2k-l05")))) }
        withStore { it.setReviewSettings(ReviewSettings(limits = DailyLimits(newVocabulary = 8, newKanji = 4, newGrammar = 2))) }
        assertEquals(LessonSelection.Only(setOf("n2k-l05")), read().of("N2"))
        withStore { it.setKanjiReviewLessons(read().with("N2", LessonSelection.All)) }
        assertEquals(DailyLimits(newVocabulary = 8, newKanji = 4, newGrammar = 2), withStore { it.preferences.first().reviewSettings.limits })
    }
}
