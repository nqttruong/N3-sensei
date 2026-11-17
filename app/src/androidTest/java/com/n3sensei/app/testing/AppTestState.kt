package com.n3sensei.app.testing

import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.N3SenseiApplication
import com.n3sensei.app.app.di.DefaultAppContainer
import com.n3sensei.app.domain.model.ThemeMode
import com.n3sensei.app.domain.reminder.ReminderSettings
import com.n3sensei.app.domain.srs.KanjiReviewLessons
import com.n3sensei.app.domain.srs.ReviewSettings
import kotlinx.coroutines.runBlocking
import com.n3sensei.app.domain.translate.QuickTranslateSettings

/**
 * Puts the real app container (same database file and DataStore the app uses) into a known
 * state before an end-to-end test launches MainActivity.
 */
object AppTestState {
    private val container: DefaultAppContainer
        get() {
            val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as N3SenseiApplication
            return app.container as DefaultAppContainer
        }

    /**
     * @param bundledContent whether MainActivity may install the illustration pack; off by
     *   default so tests see only the data they set up.
     */
    fun reset(
        seedContent: Boolean,
        onboardingCompleted: Boolean = true,
        bundledContent: Boolean = false,
        seedCountsAsRealContent: Boolean = false,
    ) = runBlocking {
        container.androidContentImportRepository.bundledContentEnabled = bundledContent
        container.database.clearAllTables()
        // A notice left by an earlier corrupt-database run would cover every screen.
        container.databaseRecovery.dismiss()
        if (seedContent) container.database.contentPackDao().replacePack(SeedData.bundle(isSeed = !seedCountsAsRealContent))
        with(container.userPreferencesRepository) {
            setOnboardingCompleted(onboardingCompleted)
            setThemeMode(ThemeMode.SYSTEM)
            setShowFurigana(true)
            setReviewSettings(ReviewSettings())
            // Both levels back to their defaults (N3: every lesson, N2: none).
            setKanjiReviewLessons(KanjiReviewLessons(mapOf("N3" to KanjiReviewLessons.default("N3"), "N2" to KanjiReviewLessons.default("N2"))))
            setReminderSettings(ReminderSettings())
            setQuickTranslateSettings(QuickTranslateSettings())
        }
        container.reminderScheduler.apply(ReminderSettings())
    }

    val preferences get() = container.userPreferencesRepository.preferences

    val appContainer: DefaultAppContainer get() = container
}
