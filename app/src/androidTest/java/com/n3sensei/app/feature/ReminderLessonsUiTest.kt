package com.n3sensei.app.feature

import androidx.annotation.StringRes
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.MainActivity
import com.n3sensei.app.R
import com.n3sensei.app.app.navigation.TopLevelDestination
import com.n3sensei.app.domain.reminder.LessonSelection
import com.n3sensei.app.domain.reminder.ReminderContent
import com.n3sensei.app.feature.settings.ReminderLessonsTags
import com.n3sensei.app.testing.AppTestState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Settings → Thông báo học tập: kinds and lessons are chosen, saved, and still there after a restart (SEED TEST DATA). */
@RunWith(AndroidJUnit4::class)
class ReminderLessonsUiTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun string(@StringRes id: Int, vararg args: Any) = context.getString(id, *args)

    private fun waitFor(text: String) = composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun openReminderSettings() {
        composeRule.onNodeWithTag(TopLevelDestination.SETTINGS.testTag).performClick()
        composeRule.onNode(hasText(string(R.string.reminder_settings_title)) and hasClickAction()).performScrollTo().performClick()
        waitFor(string(R.string.reminder_kinds))
    }

    private val reminders get() = runBlocking { AppTestState.preferences.first().reminderSettings }

    @Test
    fun kindsAndLessons_areChosenSaved_andKeptAfterARestart() {
        AppTestState.reset(seedContent = true)
        ActivityScenario.launch(MainActivity::class.java).use {
            openReminderSettings()
            // Kanji on (vocabulary is on by default).
            composeRule.onNodeWithTag("reminder_kind_${ReminderContent.KANJI.name}").performScrollTo().performClick()
            composeRule.waitUntil(5_000) { ReminderContent.KANJI in reminders.content }

            // Vocabulary lessons: none, then the only lesson again (back to "every lesson").
            composeRule.onNode(
                hasText(string(R.string.reminder_lessons_choose)) and hasAnyAncestor(hasTestTag("reminder_kind_${ReminderContent.VOCABULARY.name}")),
            ).performScrollTo().performClick()
            waitFor(string(R.string.reminder_lessons_select_all))
            composeRule.onNode(hasText(string(R.string.reminder_lessons_clear)) and hasClickAction()).performClick()
            waitFor(string(R.string.reminder_lessons_none))
            assertEquals(LessonSelection.Only(emptySet()), reminders.lessons.vocabulary)
            Espresso.pressBack()
            waitFor(string(R.string.reminder_lessons_none))
        }
        // Closed and opened again: still there.
        ActivityScenario.launch(MainActivity::class.java).use {
            openReminderSettings()
            waitFor(string(R.string.reminder_lessons_none))
            assertEquals(setOf(ReminderContent.VOCABULARY, ReminderContent.KANJI), reminders.content)
            composeRule.onNode(
                hasText(string(R.string.reminder_lessons_choose)) and hasAnyAncestor(hasTestTag("reminder_kind_${ReminderContent.VOCABULARY.name}")),
            ).performScrollTo().performClick()
            waitFor(string(R.string.reminder_lessons_select_all))
            val lesson = runBlocking {
                com.n3sensei.app.domain.reminder.ReminderLessonCatalog.observe(AppTestState.appContainer.contentRepository).first()
                    .optionsOf(com.n3sensei.app.domain.model.ItemType.VOCABULARY).single().key
            }
            composeRule.onNodeWithTag(ReminderLessonsTags.lesson(lesson)).performClick()
            composeRule.waitUntil(5_000) { reminders.lessons.vocabulary == LessonSelection.All }
            waitFor(string(R.string.reminder_lessons_all))
        }
    }
}
