package com.n3sensei.app.feature

import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.R
import com.n3sensei.app.core.designsystem.theme.N3SenseiTheme
import com.n3sensei.app.domain.translate.FieldState
import com.n3sensei.app.domain.translate.JapaneseText
import com.n3sensei.app.domain.translate.Language
import com.n3sensei.app.domain.translate.LanguageDetection
import com.n3sensei.app.domain.translate.Provider
import com.n3sensei.app.domain.translate.SelectedText
import com.n3sensei.app.domain.translate.SpeechState
import com.n3sensei.app.domain.translate.VoiceAvailability
import com.n3sensei.app.domain.translate.Sourced
import com.n3sensei.app.domain.translate.TranslationExample
import com.n3sensei.app.domain.translate.TranslationFailure
import com.n3sensei.app.domain.translate.TranslationMode
import com.n3sensei.app.domain.translate.TranslationResult
import com.n3sensei.app.feature.translate.QuickTranslateScreen
import com.n3sensei.app.feature.translate.QuickTranslateTags
import com.n3sensei.app.feature.translate.QuickTranslateUiState
import com.n3sensei.app.feature.translate.TranslationUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The translation part of the dialog in fixed states (test values only). */
@RunWith(AndroidJUnit4::class)
class QuickTranslateResultsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun <T> available(vararg values: T, provider: Provider = Provider.MACHINE_TRANSLATION) =
        FieldState.Available(values.map { Sourced(it, provider) })

    private fun result(
        source: Language,
        vi: FieldState<String> = FieldState.NotRequested,
        en: FieldState<String> = FieldState.NotRequested,
        ja: FieldState<JapaneseText> = FieldState.NotRequested,
        reading: FieldState<String> = FieldState.Unavailable,
        examples: FieldState<TranslationExample> = FieldState.Unavailable,
        notes: FieldState<String> = FieldState.Unavailable,
    ) = TranslationResult("x", source, vi, en, FieldState.Unavailable, ja, reading, FieldState.Unavailable, examples, notes)

    private fun state(text: String, source: Language, mode: TranslationMode, translation: TranslationUiState, expanded: Boolean = false) = QuickTranslateUiState(
        selected = SelectedText.Ready(text, readOnly = true),
        detection = LanguageDetection(source, null, isAmbiguous = false, candidates = listOf(source)),
        chosenSource = null, settingsMode = mode, chosenMode = null, enabled = true,
        translation = translation, detailsExpanded = expanded,
    )

    private fun show(state: QuickTranslateUiState, dark: Boolean = false, onDownload: () -> Unit = {}) =
        composeRule.setContent { N3SenseiTheme(darkTheme = dark) { QuickTranslateScreen(state, {}, {}, {}, onDownloadModels = onDownload) } }

    private fun shown(tag: String) = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun longSelection_wraps_andKeepsTheSourceSpeakerOnScreen() {
        // Found in the Chrome check: a sentence that wraps pushed the speaker out of the row.
        show(state("I have worked in Japan for two years and I want to work there again.", Language.ENGLISH, TranslationMode.BOTH, TranslationUiState.Success(
            result(Language.ENGLISH, vi = available("Tôi đã làm việc ở Nhật hai năm."), ja = available(JapaneseText("日本で二年間働きました。", null))),
        )))
        composeRule.onNodeWithTag(QuickTranslateTags.SPEAK_SOURCE).assertIsDisplayed()
    }

    @Test
    fun aMissingVoice_orOneThatFailed_isSaid_withTheWayToInstallVoices() {
        val base = state("experience", Language.ENGLISH, TranslationMode.BOTH, TranslationUiState.Success(
            result(Language.ENGLISH, vi = available("kinh nghiệm"), ja = available(JapaneseText("経験", "けいけん"))),
        ))
        val ready = Language.entries.associateWith { VoiceAvailability.AVAILABLE }
        var current by mutableStateOf(base.copy(speech = SpeechState(voices = ready + (Language.JAPANESE to VoiceAvailability.MISSING_DATA))))
        composeRule.setContent { N3SenseiTheme { QuickTranslateScreen(current, {}, {}, {}) } }
        composeRule.onNodeWithText(string(R.string.quick_translate_voices_missing, "Tiếng Nhật")).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(QuickTranslateTags.INSTALL_VOICES).performScrollTo().assertIsDisplayed()

        // Every voice "available", but the Japanese one failed when tapped (offline, on the emulator).
        current = base.copy(speech = SpeechState(voices = ready, failed = Language.JAPANESE))
        composeRule.onNodeWithText(string(R.string.quick_translate_speak_failed, "Tiếng Nhật")).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(QuickTranslateTags.INSTALL_VOICES).performScrollTo().assertIsDisplayed()

        current = base.copy(speech = SpeechState(voices = ready))
        composeRule.waitForIdle()
        assertTrue(!shown(QuickTranslateTags.VOICE_HINT))
    }

    @Test
    fun vietnameseSource_both_showsEnglishAndJapanese_withoutRepeatingVietnamese() {
        show(state("kinh nghiệm", Language.VIETNAMESE, TranslationMode.BOTH, TranslationUiState.Success(
            result(Language.VIETNAMESE, en = available("experience"), ja = available(JapaneseText("経験", "けいけん"), provider = Provider.CONTENT_DICTIONARY), reading = available("けいけん")),
        )))
        assertTrue(!shown(QuickTranslateTags.result(Language.VIETNAMESE)))
        composeRule.onNodeWithText("experience").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("経験").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.quick_translate_reading, "けいけん")).performScrollTo().assertIsDisplayed()
        // Where each answer comes from.
        composeRule.onNodeWithText(string(R.string.quick_translate_from_machine)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.quick_translate_from_dictionary)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun englishSource_englishMode_givesOnlyTheVietnameseMeaning_noIpaLine() {
        show(state("bank", Language.ENGLISH, TranslationMode.ENGLISH, TranslationUiState.Success(result(Language.ENGLISH, vi = available("ngân hàng")))))
        composeRule.onNodeWithText("ngân hàng").performScrollTo().assertIsDisplayed()
        assertTrue(!shown(QuickTranslateTags.result(Language.ENGLISH)))
        assertTrue(!shown(QuickTranslateTags.result(Language.JAPANESE)))
        assertTrue(composeRule.onAllNodesWithText(string(R.string.quick_translate_ipa, ""), substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun englishSource_japaneseMode_machineJapaneseWithoutKana_saysSo() {
        show(state("hard work", Language.ENGLISH, TranslationMode.JAPANESE, TranslationUiState.Success(
            result(Language.ENGLISH, vi = available("nỗ lực"), ja = available(JapaneseText("努力", null))),
        )))
        composeRule.onNodeWithText("努力").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.quick_translate_no_reading)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun japaneseSource_showsItsReading_meaning_andFoldedDetails_thatUnfold() {
        var expanded by mutableStateOf(false)
        val translation = TranslationUiState.Success(
            result(
                Language.JAPANESE,
                vi = available("kinh nghiệm", provider = Provider.CONTENT_DICTIONARY),
                reading = available("けいけん", provider = Provider.CONTENT_DICTIONARY),
                examples = available(TranslationExample("日本で働いた経験があります。", null, "Tôi có kinh nghiệm làm việc ở Nhật."), provider = Provider.CONTENT_DICTIONARY),
                notes = available("danh từ, động từ する", provider = Provider.CONTENT_DICTIONARY),
            ),
        )
        composeRule.setContent {
            N3SenseiTheme {
                QuickTranslateScreen(state("経験", Language.JAPANESE, TranslationMode.JAPANESE, translation, expanded), {}, {}, {}, onToggleDetails = { expanded = !expanded })
            }
        }
        composeRule.onNodeWithText(string(R.string.quick_translate_reading, "けいけん")).assertIsDisplayed()
        composeRule.onNodeWithText("kinh nghiệm").performScrollTo().assertIsDisplayed()
        // Folded by default.
        assertTrue(composeRule.onAllNodesWithText("Tôi có kinh nghiệm làm việc ở Nhật.").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag(QuickTranslateTags.DETAILS).performScrollTo().performClick()
        composeRule.onNodeWithText("Tôi có kinh nghiệm làm việc ở Nhật.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("• danh từ, động từ する").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(QuickTranslateTags.DETAILS).performScrollTo().performClick()
        composeRule.waitUntil(3_000) { composeRule.onAllNodesWithText("Tôi có kinh nghiệm làm việc ở Nhật.").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun noExamplesNoNotes_noDetailsRow() {
        show(state("hello", Language.ENGLISH, TranslationMode.ENGLISH, TranslationUiState.Success(result(Language.ENGLISH, vi = available("xin chào")))))
        assertTrue(!shown(QuickTranslateTags.DETAILS))
    }

    @Test
    fun partial_modelRequired_andErrors_areSaidShortly() {
        var downloads = 0
        show(
            state("hello", Language.ENGLISH, TranslationMode.BOTH, TranslationUiState.ModelRequired(
                result(Language.ENGLISH, vi = available("xin chào"), ja = FieldState.NeedsModel(setOf(Language.JAPANESE))), setOf(Language.JAPANESE), 48,
            )),
            onDownload = { downloads++ },
        )
        composeRule.onNodeWithText("xin chào").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.quick_translate_field_needs_model)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.quick_translate_model_needed, string(R.string.quick_translate_language_ja), 48)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(QuickTranslateTags.DOWNLOAD).performScrollTo().performClick()
        assertEquals(1, downloads)
    }

    @Test
    fun offlineError_andLoading_andDownloading() {
        var translation by mutableStateOf<TranslationUiState>(TranslationUiState.Error(TranslationFailure.OFFLINE))
        composeRule.setContent { N3SenseiTheme { QuickTranslateScreen(state("hello", Language.ENGLISH, TranslationMode.BOTH, translation), {}, {}, {}) } }
        composeRule.onNodeWithText(string(R.string.quick_translate_error_offline)).performScrollTo().assertIsDisplayed()
        translation = TranslationUiState.Loading
        composeRule.onNodeWithText(string(R.string.quick_translate_loading)).performScrollTo().assertIsDisplayed()
        translation = TranslationUiState.DownloadingModel(setOf(Language.JAPANESE))
        composeRule.onNodeWithText(string(R.string.quick_translate_downloading, string(R.string.quick_translate_language_ja))).performScrollTo().assertIsDisplayed()
        translation = TranslationUiState.Empty(result(Language.ENGLISH, vi = FieldState.Unavailable))
        composeRule.onNodeWithText(string(R.string.quick_translate_empty)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun longText_scrolls_toTheResult() {
        val long = "あ".repeat(200)
        show(state(long, Language.JAPANESE, TranslationMode.ENGLISH, TranslationUiState.Success(result(Language.JAPANESE, vi = available("(dài)"), en = available("long")))))
        composeRule.onNodeWithText("long").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun darkMode_darkSurface() {
        show(state("hello", Language.ENGLISH, TranslationMode.ENGLISH, TranslationUiState.Success(result(Language.ENGLISH, vi = available("xin chào")))), dark = true)
        val image = composeRule.onNodeWithTag(QuickTranslateTags.DIALOG).captureToImage().toPixelMap()
        val luminance = image[image.width / 2, image.height - 10].luminance()
        assertTrue("luminance $luminance", luminance < 0.3f)
        composeRule.onNodeWithText("xin chào").assertIsDisplayed()
    }
}
