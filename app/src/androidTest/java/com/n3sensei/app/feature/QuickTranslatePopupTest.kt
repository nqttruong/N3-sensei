package com.n3sensei.app.feature

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
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
import com.n3sensei.app.domain.translate.Sourced
import com.n3sensei.app.domain.translate.SpeechState
import com.n3sensei.app.domain.translate.TranslationExample
import com.n3sensei.app.domain.translate.TranslationFailure
import com.n3sensei.app.domain.translate.TranslationMode
import com.n3sensei.app.domain.translate.TranslationResult
import com.n3sensei.app.domain.translate.Utterance
import com.n3sensei.app.domain.translate.VoiceAvailability
import com.n3sensei.app.feature.translate.BookmarkUiState
import com.n3sensei.app.feature.translate.QuickTranslatePopup
import com.n3sensei.app.feature.translate.QuickTranslateTags
import com.n3sensei.app.feature.translate.QuickTranslateUiState
import com.n3sensei.app.feature.translate.TranslationUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The small "Dịch nhanh" popup in fixed states (test values only). */
@RunWith(AndroidJUnit4::class)
class QuickTranslatePopupTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun <T> available(vararg values: T, provider: Provider = Provider.MACHINE_TRANSLATION) =
        FieldState.Available(values.map { Sourced(it, provider) })

    private fun result(
        text: String,
        source: Language,
        vi: FieldState<String> = FieldState.NotRequested,
        en: FieldState<String> = FieldState.NotRequested,
        ja: FieldState<JapaneseText> = FieldState.NotRequested,
    ) = TranslationResult(
        text, source, vi, en, FieldState.Unavailable, ja,
        // A reading, examples and notes the popup must not show.
        japaneseReading = available("けいけん", provider = Provider.CONTENT_DICTIONARY),
        japaneseRomaji = FieldState.Unavailable,
        examples = available(TranslationExample("日本で働いた経験があります。", null, "Tôi có kinh nghiệm làm việc ở Nhật."), provider = Provider.CONTENT_DICTIONARY),
        usageNotes = available("danh từ", provider = Provider.CONTENT_DICTIONARY),
    )

    private val voices = SpeechState(voices = Language.entries.associateWith { VoiceAvailability.AVAILABLE })

    private fun state(text: String, source: Language?, translation: TranslationUiState, mode: TranslationMode = TranslationMode.BOTH, enabled: Boolean = true) =
        QuickTranslateUiState(
            selected = SelectedText.from(text, readOnly = true),
            detection = source?.let { LanguageDetection(it, null, isAmbiguous = false, candidates = listOf(it)) },
            chosenSource = null, settingsMode = mode, chosenMode = null, enabled = enabled,
            translation = translation, speech = voices,
            bookmark = BookmarkUiState(available = translation is TranslationUiState.Success),
        )

    private fun show(state: QuickTranslateUiState, onSpeak: (Utterance) -> Unit = {}, onBookmark: () -> Unit = {}, onClose: () -> Unit = {}) =
        composeRule.setContent { N3SenseiTheme { QuickTranslatePopup(state, onClose = onClose, onSpeak = onSpeak, onBookmark = onBookmark) } }

    private fun absent(text: String) = assertTrue("\"$text\" shown", composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isEmpty())

    @Test
    fun english_showsTheWord_andTheOtherTwoLanguages_only() {
        var spoken: Utterance? = null
        var bookmarked = 0
        var closed = 0
        show(
            state("and", Language.ENGLISH, TranslationUiState.Success(result("and", Language.ENGLISH, vi = available("và"), ja = available(JapaneseText("と", null))))),
            onSpeak = { spoken = it }, onBookmark = { bookmarked++ }, onClose = { closed++ },
        )
        composeRule.onNodeWithTag(QuickTranslateTags.TEXT).assertTextEquals("and")
        composeRule.onNodeWithText("VI").assertIsDisplayed()
        composeRule.onNodeWithText("và").assertIsDisplayed()
        composeRule.onNodeWithText("JA").assertIsDisplayed()
        composeRule.onNodeWithText("と").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag(QuickTranslateTags.result(Language.ENGLISH)).fetchSemanticsNodes().isEmpty())
        // One speaker per row, one bookmark, X.
        composeRule.onNodeWithTag(QuickTranslateTags.speak(Language.VIETNAMESE)).performClick()
        assertEquals(Utterance("và", Language.VIETNAMESE), spoken)
        composeRule.onNodeWithTag(QuickTranslateTags.speak(Language.JAPANESE)).performClick()
        assertEquals(Utterance("と", Language.JAPANESE), spoken)
        assertEquals(1, composeRule.onAllNodesWithTag(QuickTranslateTags.BOOKMARK).fetchSemanticsNodes().size)
        composeRule.onNodeWithTag(QuickTranslateTags.BOOKMARK).performClick()
        assertEquals(1, bookmarked)
        composeRule.onNodeWithTag(QuickTranslateTags.CLOSE).performClick()
        assertEquals(1, closed)
        // None of the big dialog's parts.
        listOf(
            "Văn bản đã chọn", "Ngôn ngữ của đoạn đã chọn", "Chế độ dịch", "Cả hai", "Dịch máy trên thiết bị", "Từ điển N3 Sensei",
            "Tự nhận diện", "Ví dụ và cách dùng", "Cách dùng", "けいけん", "日本で働いた", "danh từ", "Nghe",
        ).forEach(::absent)
    }

    @Test
    fun vietnamese_andJapanese_sources_showTheirTwoTargets() {
        show(state("học tập", Language.VIETNAMESE, TranslationUiState.Success(result("học tập", Language.VIETNAMESE, en = available("study"), ja = available(JapaneseText("勉強", "べんきょう"), provider = Provider.CONTENT_DICTIONARY)))))
        composeRule.onNodeWithText("EN").assertIsDisplayed()
        composeRule.onNodeWithText("study").assertIsDisplayed()
        composeRule.onNodeWithText("勉強").assertIsDisplayed()
        absent("べんきょう") // no reading in the popup
        assertTrue(composeRule.onAllNodesWithTag(QuickTranslateTags.result(Language.VIETNAMESE)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun aJapaneseSource_showsVietnameseAndEnglish() {
        show(state("勉強", Language.JAPANESE, TranslationUiState.Success(result("勉強", Language.JAPANESE, vi = available("học tập", provider = Provider.CONTENT_DICTIONARY), en = available("study")))))
        composeRule.onNodeWithText("học tập").assertIsDisplayed()
        composeRule.onNodeWithText("study").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag(QuickTranslateTags.result(Language.JAPANESE)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun oneTargetMode_showsOneRow_noEmptyRow() {
        show(state("and", Language.ENGLISH, TranslationUiState.Success(result("and", Language.ENGLISH, vi = available("và"))), mode = TranslationMode.ENGLISH))
        assertEquals(1, composeRule.onAllNodesWithTag(QuickTranslateTags.result(Language.VIETNAMESE)).fetchSemanticsNodes().size)
        assertTrue(composeRule.onAllNodesWithTag(QuickTranslateTags.result(Language.JAPANESE)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun loading_partial_modelNeeded_error_andEmpty_stayCompact() {
        var current by mutableStateOf(state("and", Language.ENGLISH, TranslationUiState.Loading))
        var downloads = 0
        var retries = 0
        composeRule.setContent {
            N3SenseiTheme { QuickTranslatePopup(current, onClose = {}, onDownloadModels = { downloads++ }, onRetry = { retries++ }) }
        }
        composeRule.onNodeWithTag(QuickTranslateTags.TEXT).assertTextEquals("and") // the word while it translates
        composeRule.onNodeWithTag(QuickTranslateTags.LOADING).assertIsDisplayed()
        // The bookmark waits for something to save.
        composeRule.onNodeWithTag(QuickTranslateTags.BOOKMARK).assertIsNotEnabled()

        current = state("and", Language.ENGLISH, TranslationUiState.PartialSuccess(result("and", Language.ENGLISH, vi = available("và"), ja = FieldState.Failed(TranslationFailure.TRANSLATION_FAILED))))
        composeRule.onNodeWithText("và").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.quick_translate_error_translation_short)).assertIsDisplayed()

        current = state("and", Language.ENGLISH, TranslationUiState.ModelRequired(result("and", Language.ENGLISH, vi = FieldState.NeedsModel(setOf(Language.VIETNAMESE)), ja = FieldState.NeedsModel(setOf(Language.JAPANESE))), setOf(Language.VIETNAMESE, Language.JAPANESE), 84))
        composeRule.onNodeWithText(string(R.string.quick_translate_model_short, "VI, JA", 84)).assertIsDisplayed()
        composeRule.onNodeWithTag(QuickTranslateTags.DOWNLOAD).performClick()
        assertEquals(1, downloads)

        current = state("and", Language.ENGLISH, TranslationUiState.Error(TranslationFailure.OFFLINE))
        composeRule.onNodeWithText(string(R.string.quick_translate_error_offline_short)).assertIsDisplayed()
        composeRule.onNodeWithTag(QuickTranslateTags.RETRY).performClick()
        assertEquals(1, retries)

        current = state("and", Language.ENGLISH, TranslationUiState.Empty(result("and", Language.ENGLISH, vi = FieldState.Unavailable)))
        composeRule.onNodeWithText(string(R.string.quick_translate_empty)).assertIsDisplayed()
    }

    @Test
    fun aMissingVoice_disablesItsSpeaker_aFailedOneIsSaidShortly() {
        val missingJa = voices.copy(voices = voices.voices + (Language.JAPANESE to VoiceAvailability.MISSING_DATA))
        var current by mutableStateOf(
            state("and", Language.ENGLISH, TranslationUiState.Success(result("and", Language.ENGLISH, vi = available("và"), ja = available(JapaneseText("と", null))))).copy(speech = missingJa),
        )
        composeRule.setContent { N3SenseiTheme { QuickTranslatePopup(current, onClose = {}) } }
        composeRule.onNodeWithTag(QuickTranslateTags.speak(Language.JAPANESE)).assertIsNotEnabled()
        composeRule.onNodeWithTag(QuickTranslateTags.speak(Language.VIETNAMESE)).assertIsEnabled()
        current = current.copy(speech = voices.copy(failed = Language.VIETNAMESE))
        composeRule.onNodeWithText(string(R.string.quick_translate_speak_failed_short, string(R.string.quick_translate_language_vi))).assertIsDisplayed()
    }

    @Test
    fun noText_tooLong_unknownLanguage_andTurnedOff_areShortNotices() {
        var current by mutableStateOf(state("", null, TranslationUiState.Idle))
        composeRule.setContent { N3SenseiTheme { QuickTranslatePopup(current, onClose = {}) } }
        composeRule.onNodeWithText(string(R.string.quick_translate_no_text)).assertIsDisplayed()
        current = state("Привет", null, TranslationUiState.Idle)
        composeRule.onNodeWithText(string(R.string.quick_translate_unknown_language)).assertIsDisplayed()
        current = state("経験", Language.JAPANESE, TranslationUiState.Idle, enabled = false)
        composeRule.onNodeWithText(string(R.string.quick_translate_disabled)).assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag(QuickTranslateTags.BOOKMARK).fetchSemanticsNodes().isEmpty())
        current = state("a".repeat(201), Language.ENGLISH, TranslationUiState.Idle)
        composeRule.onNodeWithText(string(R.string.quick_translate_too_long, 201, 200)).assertIsDisplayed()
    }

    @Test
    fun small_atMost280dp_evenWithALargeFont() {
        val long = "I have worked in Japan for two years and I want to work there again."
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                N3SenseiTheme {
                    QuickTranslatePopup(
                        state(long, Language.ENGLISH, TranslationUiState.Success(result(long, Language.ENGLISH, vi = available("Tôi đã làm việc ở Nhật hai năm."), ja = available(JapaneseText("日本で二年間働きました。", null))))),
                        onClose = {},
                    )
                }
            }
        }
        val bounds = composeRule.onNodeWithTag(QuickTranslateTags.POPUP).getUnclippedBoundsInRoot()
        val width = bounds.right - bounds.left
        assertTrue("width $width", width <= 280.dp)
        // The language codes stay on one line, the buttons stay reachable.
        composeRule.onNodeWithText("VI").assertIsDisplayed()
        composeRule.onNodeWithTag(QuickTranslateTags.CLOSE).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(R.string.quick_translate_bookmark)).assertIsDisplayed()
    }
}
