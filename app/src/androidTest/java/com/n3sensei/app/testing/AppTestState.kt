package com.n3sensei.app.testing

import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.N3SenseiApplication
import com.n3sensei.app.app.di.DefaultAppContainer
import com.n3sensei.app.domain.model.ThemeMode
import kotlinx.coroutines.runBlocking

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

    fun reset(seedContent: Boolean, onboardingCompleted: Boolean = true) = runBlocking {
        container.database.clearAllTables()
        if (seedContent) container.database.contentPackDao().replacePack(SeedData.bundle())
        with(container.userPreferencesRepository) {
            setOnboardingCompleted(onboardingCompleted)
            setThemeMode(ThemeMode.SYSTEM)
            setShowFurigana(true)
        }
    }

    val preferences get() = container.userPreferencesRepository.preferences
}
