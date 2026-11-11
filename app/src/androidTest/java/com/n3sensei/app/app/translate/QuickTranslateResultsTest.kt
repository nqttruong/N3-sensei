package com.n3sensei.app.app.translate

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.n3sensei.app.R
import com.n3sensei.app.domain.translate.Language
import com.n3sensei.app.domain.translate.QuickTranslateSettings
import com.n3sensei.app.domain.translate.TranslationMode
import com.n3sensei.app.feature.translate.QuickTranslateTags
import com.n3sensei.app.testing.AppTestState
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import androidx.compose.ui.test.assertTextEquals
import com.n3sensei.app.testing.OverlayTesting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The popup with the real translation engine and the seed content (SEED TEST DATA), on a device
 * without ML Kit models: the dictionary answers, machine translation waits for the learner.
 */
@RunWith(AndroidJUnit4::class)
class QuickTranslateResultsTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun waitForText(text: String) = composeRule.waitUntil(10_000) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Before
    fun setUp() {
        AppTestState.reset(seedContent = true)
        OverlayTesting.permission(true)
        // As on a device that never downloaded them (an earlier run or a manual check may have).
        runBlocking {
            listOf(TranslateLanguage.VIETNAMESE, TranslateLanguage.JAPANESE).forEach {
                RemoteModelManager.getInstance().deleteDownloadedModel(TranslateRemoteModel.Builder(it).build()).await()
            }
        }
        runBlocking { AppTestState.appContainer.userPreferencesRepository.setQuickTranslateSettings(QuickTranslateSettings(mode = TranslationMode.JAPANESE)) }
    }

    @After
    fun tearDown() {
        OverlayTesting.close()
        AppTestState.reset(seedContent = false)
    }

    @Test
    fun aJapaneseWordOfTheContent_itsMeaningFromTheDictionary_inOneRow() {
        assertTrue(OverlayTesting.show("経験"))
        waitForText("kinh nghiệm")
        composeRule.onNodeWithTag(QuickTranslateTags.result(Language.VIETNAMESE)).assertIsDisplayed()
        // The popup keeps it short: no reading, source or example (they stay in the app).
        assertTrue(composeRule.onAllNodesWithText("けいけん", substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Tôi có kinh nghiệm làm việc ở Nhật.").fetchSemanticsNodes().isEmpty())
        // Nothing needed a model: no download offered.
        composeRule.onNodeWithTag(QuickTranslateTags.DOWNLOAD).assertDoesNotExist()
    }

    @Test
    fun englishAsked_withoutItsModel_waitsForTheLearner_neverDownloadsAlone() {
        runBlocking { AppTestState.appContainer.userPreferencesRepository.setQuickTranslateSettings(QuickTranslateSettings(mode = TranslationMode.BOTH)) }
        assertTrue(OverlayTesting.show("経験"))
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag(QuickTranslateTags.DOWNLOAD).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText(string(R.string.quick_translate_field_needs_model)).assertIsDisplayed()
        // The dictionary meaning stays while English waits.
        composeRule.onNodeWithText("kinh nghiệm").assertIsDisplayed()
    }

    @Test
    fun selectionsInQuickSuccession_endOnTheLastOne() {
        repeat(3) {
            assertTrue(OverlayTesting.show("kinh nghiệm"))
            assertTrue(OverlayTesting.show("experience"))
            assertTrue(OverlayTesting.show("経験"))
        }
        // The last text's result, not one of the cancelled requests.
        waitForText("kinh nghiệm")
        composeRule.onNodeWithTag(QuickTranslateTags.TEXT).assertTextEquals("経験")
        assertEquals(1, composeRule.onAllNodesWithTag(QuickTranslateTags.POPUP).fetchSemanticsNodes().size)
    }
}
