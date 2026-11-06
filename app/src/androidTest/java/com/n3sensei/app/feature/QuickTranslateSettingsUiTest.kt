package com.n3sensei.app.feature

import android.content.Intent
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.MainActivity
import com.n3sensei.app.app.navigation.TopLevelDestination
import com.n3sensei.app.app.translate.QuickTranslateMenu
import com.n3sensei.app.domain.translate.QuickTranslateSettings
import com.n3sensei.app.domain.translate.TranslationMode
import com.n3sensei.app.feature.settings.SettingsTags
import com.n3sensei.app.testing.AppTestState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Settings → Dịch nhanh: saved in DataStore, kept after a restart, and applied to the text selection menu entry. */
@RunWith(AndroidJUnit4::class)
class QuickTranslateSettingsUiTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val settings get() = runBlocking { AppTestState.preferences.first().quickTranslate }

    private fun openSettings() {
        composeRule.onNodeWithTag(TopLevelDestination.SETTINGS.testTag).performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(SettingsTags.QUICK_TRANSLATE_ENABLED)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** What a text selection menu sees: the PROCESS_TEXT handlers the system lists. */
    @Suppress("DEPRECATION")
    private fun offeredForSelectedText(): Boolean = context.packageManager
        .queryIntentActivities(Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain"), 0)
        .any { it.activityInfo.packageName == context.packageName }

    @After
    fun tearDown() {
        AppTestState.reset(seedContent = false)
        composeRule.waitUntil(5_000) { QuickTranslateMenu.isOffered(context) }
    }

    @Test
    fun switchAndLanguage_areSaved_keptAfterARestart_andApplyToTheMenu() {
        AppTestState.reset(seedContent = true)
        ActivityScenario.launch(MainActivity::class.java).use {
            openSettings()
            composeRule.onNodeWithTag(SettingsTags.QUICK_TRANSLATE_ENABLED).performScrollTo().assertIsOn()
            composeRule.onNodeWithTag(SettingsTags.quickTranslateMode(TranslationMode.BOTH)).performScrollTo().assertIsSelected()

            composeRule.onNodeWithTag(SettingsTags.quickTranslateMode(TranslationMode.JAPANESE)).performScrollTo().performClick()
            composeRule.waitUntil(5_000) { settings.mode == TranslationMode.JAPANESE }
            composeRule.onNodeWithTag(SettingsTags.QUICK_TRANSLATE_ENABLED).performScrollTo().performClick()
            composeRule.waitUntil(5_000) { !settings.enabled }
            // Off: the component is disabled, so the system no longer offers "Dịch nhanh".
            composeRule.waitUntil(5_000) { !QuickTranslateMenu.isOffered(context) }
            assertEquals(false, offeredForSelectedText())
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            openSettings()
            composeRule.onNodeWithTag(SettingsTags.QUICK_TRANSLATE_ENABLED).performScrollTo().assertIsOff()
            composeRule.onNodeWithTag(SettingsTags.quickTranslateMode(TranslationMode.JAPANESE)).performScrollTo().assertIsSelected()
            assertEquals(QuickTranslateSettings(enabled = false, mode = TranslationMode.JAPANESE), settings)

            composeRule.onNodeWithTag(SettingsTags.QUICK_TRANSLATE_ENABLED).performScrollTo().performClick()
            composeRule.waitUntil(5_000) { settings.enabled && QuickTranslateMenu.isOffered(context) }
            assertEquals(true, offeredForSelectedText())
            assertEquals(TranslationMode.JAPANESE, settings.mode)
        }
    }
}
