package com.n3sensei.app.app

import androidx.core.view.WindowCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.MainActivity
import com.n3sensei.app.domain.model.ThemeMode
import com.n3sensei.app.testing.AppTestState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression: with the system in dark mode and the app set to "Sáng", the status bar icons
 * were white on the light app background (and the reverse), because their colour followed the
 * system setting instead of the app theme.
 */
@RunWith(AndroidJUnit4::class)
class SystemBarAppearanceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun systemNightMode(on: Boolean) {
        instrumentation.uiAutomation.executeShellCommand("cmd uimode night ${if (on) "yes" else "no"}").close()
        instrumentation.waitForIdleSync()
    }

    @After
    fun restore() = systemNightMode(false)

    private fun lightIcons(theme: ThemeMode, systemDark: Boolean): Pair<Boolean, Boolean> {
        systemNightMode(systemDark)
        AppTestState.reset(seedContent = false)
        runBlocking { AppTestState.appContainer.userPreferencesRepository.setThemeMode(theme) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            Thread.sleep(500) // first composition reads the theme from DataStore
            var result = false to false
            scenario.onActivity { activity ->
                val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                result = controller.isAppearanceLightStatusBars to controller.isAppearanceLightNavigationBars
            }
            return result
        }
    }

    @Test
    fun lightAppTheme_onDarkSystem_usesDarkIcons() {
        // "Light appearance" = dark icons, readable on the light app background.
        assertEquals(true to true, lightIcons(ThemeMode.LIGHT, systemDark = true))
    }

    @Test
    fun darkAppTheme_onLightSystem_usesLightIcons() {
        assertEquals(false to false, lightIcons(ThemeMode.DARK, systemDark = false))
    }

    @Test
    fun systemTheme_followsTheSystem() {
        assertEquals(false to false, lightIcons(ThemeMode.SYSTEM, systemDark = true))
        assertEquals(true to true, lightIcons(ThemeMode.SYSTEM, systemDark = false))
    }
}
