package com.n3sensei.app.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.n3sensei.app.domain.translate.QuickTranslateSettings
import com.n3sensei.app.domain.translate.TranslationMode
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

/** Settings → Dịch nhanh in a real Preferences DataStore file. */
class QuickTranslatePreferencesTest {

    @get:Rule
    val folder = TemporaryFolder()

    /** Opens the file, runs [block], then closes it (one DataStore per file at a time). */
    private fun <T> withRepository(file: File, block: suspend (DataStoreUserPreferencesRepository) -> T): T {
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)
        return runBlocking {
            try {
                block(DataStoreUserPreferencesRepository(PreferenceDataStoreFactory.create(scope = scope) { file }))
            } finally {
                // Closed for good before the file is opened again.
                job.cancelAndJoin()
            }
        }
    }

    private suspend fun DataStoreUserPreferencesRepository.read() = preferences.first().quickTranslate

    @Test
    fun defaults_onAndBothLanguages() {
        val file = File(folder.root, "prefs.preferences_pb")
        assertEquals(QuickTranslateSettings(enabled = true, mode = TranslationMode.BOTH), withRepository(file) { it.read() })
    }

    @Test
    fun eachModeAndTheSwitch_areSaved_andReadBackAfterReopening() {
        val file = File(folder.root, "prefs.preferences_pb")
        TranslationMode.entries.forEach { mode ->
            withRepository(file) { it.setQuickTranslateSettings(QuickTranslateSettings(enabled = true, mode = mode)) }
            assertEquals(mode, withRepository(file) { it.read() }.mode)
        }
        withRepository(file) { it.setQuickTranslateSettings(QuickTranslateSettings(enabled = false, mode = TranslationMode.JAPANESE)) }
        assertEquals(QuickTranslateSettings(enabled = false, mode = TranslationMode.JAPANESE), withRepository(file) { it.read() })
        withRepository(file) { it.setQuickTranslateSettings(QuickTranslateSettings(enabled = true, mode = TranslationMode.JAPANESE)) }
        assertEquals(true, withRepository(file) { it.read() }.enabled)
        // Other settings are left alone.
        assertEquals(true, withRepository(file) { it.preferences.first().showFurigana })
    }
}
