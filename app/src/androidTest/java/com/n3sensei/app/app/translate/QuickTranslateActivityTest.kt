package com.n3sensei.app.app.translate

import android.content.Intent
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.n3sensei.app.MainActivity
import com.n3sensei.app.R
import com.n3sensei.app.domain.model.ThemeMode
import com.n3sensei.app.domain.translate.Language
import com.n3sensei.app.domain.translate.QuickTranslateSettings
import com.n3sensei.app.domain.translate.SelectedText
import com.n3sensei.app.domain.translate.TranslationMode
import com.n3sensei.app.feature.translate.QuickTranslateTags
import com.n3sensei.app.testing.AppTestState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** "Dịch nhanh" opened by another app's text selection menu (ACTION_PROCESS_TEXT). */
@RunWith(AndroidJUnit4::class)
class QuickTranslateActivityTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun processText(text: CharSequence?, readOnly: Boolean = true) =
        Intent(Intent.ACTION_PROCESS_TEXT).setClass(context, QuickTranslateActivity::class.java).setType("text/plain").apply {
            if (text != null) putExtra(Intent.EXTRA_PROCESS_TEXT, text)
            putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, readOnly)
        }

    private fun waitForDialog() = composeRule.waitUntil(5_000) {
        composeRule.onAllNodesWithTag(QuickTranslateTags.DIALOG).fetchSemanticsNodes().isNotEmpty()
    }

    private fun mainActivityStarted(): Boolean {
        var found = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            found = Stage.entries.any { stage ->
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage).any { it is MainActivity }
            }
        }
        return found
    }

    /**
     * Closed = finished at once (paused, no longer shown). The system destroys it right after;
     * with no activity behind it (as in this test, unlike over a real app) it may wait its
     * 5-second stop timeout first, hence the longer limit.
     */
    private fun waitUntilClosed(scenario: ActivityScenario<QuickTranslateActivity>) =
        composeRule.waitUntil(CLOSE_TIMEOUT_MS) { scenario.state == Lifecycle.State.DESTROYED }

    private companion object {
        const val CLOSE_TIMEOUT_MS = 15_000L
    }

    @After
    fun tearDown() = runBlocking {
        AppTestState.appContainer.userPreferencesRepository.setThemeMode(ThemeMode.SYSTEM)
        AppTestState.appContainer.userPreferencesRepository.setQuickTranslateSettings(QuickTranslateSettings())
    }

    @Test
    fun theSystemOffersIt_forSelectedPlainText() {
        // The int overload works on every supported API level (26+).
        @Suppress("DEPRECATION")
        val handlers = context.packageManager.queryIntentActivities(Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain"), 0)
        val ours = handlers.single { it.activityInfo.packageName == context.packageName }
        assertEquals(QuickTranslateActivity::class.java.name, ours.activityInfo.name)
        assertEquals(string(R.string.quick_translate_action), ours.loadLabel(context.packageManager).toString())
        assertTrue(ours.activityInfo.exported)
    }

    @Test
    fun theSelectedText_isShownAsGiven_andXCloses_withoutOpeningTheApp() {
        ActivityScenario.launch<QuickTranslateActivity>(processText("日本で働いた経験 · Việt Nam · experience 👍")).use { scenario ->
            waitForDialog()
            composeRule.onNodeWithText(string(R.string.quick_translate_selected_label)).assertIsDisplayed()
            composeRule.onNodeWithText("日本で働いた経験 · Việt Nam · experience 👍").assertIsDisplayed()
            // Translation starts at once; a missing model is only offered, never downloaded on its own.
            composeRule.onNodeWithText(string(R.string.quick_translate_downloading, ""), substring = true).assertDoesNotExist()
            composeRule.onNodeWithTag(QuickTranslateTags.CLOSE).performClick()
            waitUntilClosed(scenario)
        }
        assertTrue(!mainActivityStarted())
    }

    @Test
    fun backCloses() {
        ActivityScenario.launch<QuickTranslateActivity>(processText("experience")).use { scenario ->
            waitForDialog()
            Espresso.pressBackUnconditionally()
            waitUntilClosed(scenario)
        }
    }

    @Test
    fun aWrongOrEmptyIntent_saysThereIsNoText() {
        val wrongAction = Intent(Intent.ACTION_VIEW).setClass(context, QuickTranslateActivity::class.java)
        listOf(wrongAction, processText(null), processText("   ")).forEach { intent ->
            ActivityScenario.launch<QuickTranslateActivity>(intent).use {
                waitForDialog()
                composeRule.onNodeWithText(string(R.string.quick_translate_no_text)).assertIsDisplayed()
                assertTrue(composeRule.onAllNodesWithTag(QuickTranslateTags.TEXT).fetchSemanticsNodes().isEmpty())
            }
        }
    }

    @Test
    fun tooLongText_isRefusedWithItsLength_notCut() {
        val max = SelectedText.MAX_CODE_POINTS
        ActivityScenario.launch<QuickTranslateActivity>(processText("あ".repeat(max + 5))).use {
            waitForDialog()
            composeRule.onNodeWithText(string(R.string.quick_translate_too_long, max + 5, max)).assertIsDisplayed()
            assertTrue(composeRule.onAllNodesWithTag(QuickTranslateTags.TEXT).fetchSemanticsNodes().isEmpty())
        }
    }

    @Test
    fun twoSelectionsInARow_eachShowsItsOwnText() {
        ActivityScenario.launch<QuickTranslateActivity>(processText("最初の言葉")).use {
            waitForDialog()
            composeRule.onNodeWithText("最初の言葉").assertIsDisplayed()
            composeRule.onNodeWithTag(QuickTranslateTags.CLOSE).performClick()
        }
        ActivityScenario.launch<QuickTranslateActivity>(processText("second words")).use {
            waitForDialog()
            composeRule.onNodeWithText("second words").assertIsDisplayed()
            assertTrue(composeRule.onAllNodesWithText("最初の言葉").fetchSemanticsNodes().isEmpty())
        }
    }

    @Test
    fun theDetectedSourceIsShown_andCanBeChanged_withoutLosingTheText() {
        ActivityScenario.launch<QuickTranslateActivity>(processText("経験")).use {
            waitForDialog()
            val japanese = string(R.string.quick_translate_language_ja)
            // Kanji alone: Japanese, as a guess to check.
            composeRule.onNodeWithText(string(R.string.quick_translate_detected_unsure, japanese)).assertIsDisplayed()
            composeRule.onNodeWithTag(QuickTranslateTags.source(Language.JAPANESE)).assertIsSelected()
            // Default mode (both): Japanese text → Vietnamese and English.
            val vi = string(R.string.quick_translate_language_vi)
            val en = string(R.string.quick_translate_language_en)
            composeRule.onNodeWithText(string(R.string.quick_translate_targets, "$vi, $en")).assertIsDisplayed()

            composeRule.onNodeWithTag(QuickTranslateTags.source(Language.VIETNAMESE)).performScrollTo().performClick()
            composeRule.onNodeWithTag(QuickTranslateTags.source(Language.VIETNAMESE)).assertIsSelected()
            composeRule.onNodeWithText(string(R.string.quick_translate_source_chosen, vi)).assertIsDisplayed()
            composeRule.onNodeWithText(string(R.string.quick_translate_targets, "$en, $japanese")).assertIsDisplayed()

            composeRule.onNodeWithTag(QuickTranslateTags.mode(TranslationMode.ENGLISH)).performScrollTo().performClick()
            composeRule.onNodeWithText(string(R.string.quick_translate_targets, en)).performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText(string(R.string.quick_translate_mode_changed, string(R.string.quick_translate_mode_both))).performScrollTo().assertIsDisplayed()
            // The text is still the selected one, and Settings were not changed.
            composeRule.onNodeWithTag(QuickTranslateTags.TEXT).assertTextEquals("経験")
            assertEquals(QuickTranslateSettings(), runBlocking { AppTestState.appContainer.userPreferencesRepository.preferences.first().quickTranslate })
        }
    }

    @Test
    fun rotation_keepsTheTextAndTheLearnersChoices() {
        ActivityScenario.launch<QuickTranslateActivity>(processText("経験")).use { scenario ->
            waitForDialog()
            composeRule.onNodeWithTag(QuickTranslateTags.source(Language.VIETNAMESE)).performScrollTo().performClick()
            composeRule.onNodeWithTag(QuickTranslateTags.mode(TranslationMode.ENGLISH)).performScrollTo().performClick()
            // Rotation, theme or font change: the activity is rebuilt, the dialog stays as it was.
            scenario.recreate()
            waitForDialog()
            composeRule.onNodeWithTag(QuickTranslateTags.TEXT).assertTextEquals("経験")
            composeRule.onNodeWithTag(QuickTranslateTags.source(Language.VIETNAMESE)).performScrollTo().assertIsSelected()
            composeRule.onNodeWithTag(QuickTranslateTags.mode(TranslationMode.ENGLISH)).performScrollTo().assertIsSelected()
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    @Test
    fun theModeFromSettings_isUsed() {
        runBlocking { AppTestState.appContainer.userPreferencesRepository.setQuickTranslateSettings(QuickTranslateSettings(mode = TranslationMode.JAPANESE)) }
        ActivityScenario.launch<QuickTranslateActivity>(processText("kinh nghiệm")).use {
            waitForDialog()
            composeRule.onNodeWithText(string(R.string.quick_translate_detected, string(R.string.quick_translate_language_vi))).assertIsDisplayed()
            composeRule.onNodeWithTag(QuickTranslateTags.mode(TranslationMode.JAPANESE)).assertIsSelected()
            composeRule.onNodeWithText(string(R.string.quick_translate_targets, string(R.string.quick_translate_language_ja))).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun followsTheAppsDarkAndLightChoice() {
        fun surfaceLuminance(): Float {
            val image = composeRule.onNodeWithTag(QuickTranslateTags.DIALOG).captureToImage().toPixelMap()
            // Middle of the bottom padding: on the surface, clear of the text and of the rounded corners.
            return image[image.width / 2, image.height - 10].luminance()
        }
        runBlocking { AppTestState.appContainer.userPreferencesRepository.setThemeMode(ThemeMode.DARK) }
        ActivityScenario.launch<QuickTranslateActivity>(processText("経験")).use {
            waitForDialog()
            composeRule.onNodeWithText("経験").assertIsDisplayed()
            surfaceLuminance().let { assertTrue("dark surface expected, luminance $it", it < 0.3f) }
        }
        runBlocking { AppTestState.appContainer.userPreferencesRepository.setThemeMode(ThemeMode.LIGHT) }
        ActivityScenario.launch<QuickTranslateActivity>(processText("経験")).use {
            waitForDialog()
            surfaceLuminance().let { assertTrue("light surface expected, luminance $it", it > 0.6f) }
        }
    }
}
