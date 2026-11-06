package com.n3sensei.app.feature

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.theme.N3SenseiTheme
import com.n3sensei.app.domain.translate.Language
import com.n3sensei.app.domain.translate.ScriptLanguageDetector
import com.n3sensei.app.domain.translate.SelectedText
import com.n3sensei.app.domain.translate.TranslationMode
import com.n3sensei.app.feature.translate.QuickTranslateScreen
import com.n3sensei.app.feature.translate.QuickTranslateTags
import com.n3sensei.app.feature.translate.QuickTranslateUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The dialog content in states the activity tests cannot reach easily. */
@RunWith(AndroidJUnit4::class)
class QuickTranslateScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun state(text: String, enabled: Boolean = true) = QuickTranslateUiState(
        selected = SelectedText.Ready(text, readOnly = true),
        detection = ScriptLanguageDetector().detect(text),
        chosenSource = null,
        settingsMode = TranslationMode.BOTH,
        chosenMode = null,
        enabled = enabled,
    )

    @Test
    fun whenTurnedOff_itSaysSo_andOffersNoChoices() {
        composeRule.setContent { N3SenseiTheme { QuickTranslateScreen(state("経験", enabled = false), {}, {}, {}) } }
        composeRule.onNodeWithText("経験").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.quick_translate_disabled)).assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag(QuickTranslateTags.source(Language.JAPANESE)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun anUnknownLanguage_asksToChoose_andSelectsNothing() {
        composeRule.setContent { N3SenseiTheme { QuickTranslateScreen(state("Привет"), {}, {}, {}) } }
        composeRule.onNodeWithText(context.getString(R.string.quick_translate_detected_none)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.quick_translate_targets_none)).assertIsDisplayed()
    }

    @Test
    fun mixedText_saysItIsMixed() {
        composeRule.setContent { N3SenseiTheme { QuickTranslateScreen(state("日本で働いた経験 · kinh nghiệm làm việc ở Nhật"), {}, {}, {}) } }
        val vi = context.getString(R.string.quick_translate_language_vi)
        composeRule.onNodeWithText(context.getString(R.string.quick_translate_detected_mixed, vi)).assertIsDisplayed()
    }
}
