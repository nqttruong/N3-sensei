package com.n3sensei.app.app.navigation

import androidx.annotation.StringRes
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.n3sensei.app.R
import kotlin.reflect.KClass

/**
 * Registry of every screen (leaf destination): its route, top bar title and owning tab.
 *
 * @property isTabRoot true for the first screen of a tab; other screens show an Up button.
 */
enum class AppScreen(
    val route: KClass<*>,
    @StringRes val titleRes: Int,
    val tab: TopLevelDestination,
    val isTabRoot: Boolean,
    /** A study session: the bottom bar is hidden so the card has the room and the attention. */
    val isFocusSession: Boolean = false,
) {
    HOME(HomeRoute::class, R.string.app_name, TopLevelDestination.HOME, isTabRoot = true),
    LEARN_HUB(LearnHubRoute::class, R.string.learn_title, TopLevelDestination.LEARN, isTabRoot = true),
    LESSONS(LessonListRoute::class, R.string.lessons_title, TopLevelDestination.LEARN, isTabRoot = false),
    LESSON_DETAIL(LessonDetailRoute::class, R.string.lesson_detail_title, TopLevelDestination.LEARN, isTabRoot = false),
    VOCABULARY_GROUPS(VocabularyGroupsRoute::class, R.string.vocabulary_title, TopLevelDestination.LEARN, isTabRoot = false),
    VOCABULARY_GROUP(VocabularyGroupRoute::class, R.string.vocabulary_group_screen_title, TopLevelDestination.LEARN, isTabRoot = false),
    VOCABULARY(VocabularyRoute::class, R.string.vocabulary_all, TopLevelDestination.LEARN, isTabRoot = false),
    MY_WORDS(MyWordsRoute::class, R.string.learn_my_words, TopLevelDestination.LEARN, isTabRoot = false),
    EXAM_LIST(ExamListRoute::class, R.string.exam_list_title, TopLevelDestination.LEARN, isTabRoot = false),
    EXAM_INTRO(ExamIntroRoute::class, R.string.exam_intro_title, TopLevelDestination.LEARN, isTabRoot = false),
    EXAM_SESSION(ExamSessionRoute::class, R.string.exam_session_title, TopLevelDestination.LEARN, isTabRoot = false, isFocusSession = true),
    EXAM_RESULT(ExamResultRoute::class, R.string.exam_result_title, TopLevelDestination.LEARN, isTabRoot = false),
    EXAM_REVIEW(ExamReviewRoute::class, R.string.exam_review_title, TopLevelDestination.LEARN, isTabRoot = false),
    MISTAKE_ANALYSIS(MistakeAnalysisRoute::class, R.string.mistakes_analysis_title, TopLevelDestination.LEARN, isTabRoot = false),
    ERROR_NOTEBOOK(ErrorNotebookRoute::class, R.string.mistakes_title, TopLevelDestination.LEARN, isTabRoot = false),
    ERROR_DETAIL(ErrorDetailRoute::class, R.string.error_detail_title, TopLevelDestination.LEARN, isTabRoot = false),
    ERROR_REVIEW(ErrorReviewRoute::class, R.string.error_session_title, TopLevelDestination.LEARN, isTabRoot = false, isFocusSession = true),
    EXAM_DASHBOARD(ExamDashboardRoute::class, R.string.jlpt_dashboard_title, TopLevelDestination.LEARN, isTabRoot = false),
    CONTRAST_HOME(ContrastHomeRoute::class, R.string.contrast_title, TopLevelDestination.LEARN, isTabRoot = false),
    CONTRAST_LIBRARY(ContrastLibraryRoute::class, R.string.contrast_library_title, TopLevelDestination.LEARN, isTabRoot = false),
    CONTRAST_GROUP(ContrastGroupRoute::class, R.string.contrast_group_title, TopLevelDestination.LEARN, isTabRoot = false),
    CONTRAST_PRACTICE(ContrastPracticeRoute::class, R.string.contrast_practice_title, TopLevelDestination.LEARN, isTabRoot = false, isFocusSession = true),
    CONTRAST_SUMMARY(ContrastSummaryRoute::class, R.string.contrast_summary_title, TopLevelDestination.LEARN, isTabRoot = false),
    CONTRAST_ERRORS(ContrastErrorsRoute::class, R.string.contrast_errors_title, TopLevelDestination.LEARN, isTabRoot = false),
    CONTRAST_REVIEW(ContrastReviewRoute::class, R.string.contrast_review_title, TopLevelDestination.LEARN, isTabRoot = false),
    CONTRAST_PROGRESS(ContrastProgressRoute::class, R.string.contrast_progress_title, TopLevelDestination.LEARN, isTabRoot = false),
    SKILL_DETAIL(SkillDetailRoute::class, R.string.skill_detail_title, TopLevelDestination.LEARN, isTabRoot = false),
    WEAKNESS(WeaknessRoute::class, R.string.weakness_title, TopLevelDestination.LEARN, isTabRoot = false),
    VOCABULARY_DETAIL(VocabularyDetailRoute::class, R.string.vocabulary_detail_title, TopLevelDestination.LEARN, isTabRoot = false),
    KANJI(KanjiRoute::class, R.string.kanji_title, TopLevelDestination.LEARN, isTabRoot = false),
    KANJI_DETAIL(KanjiDetailRoute::class, R.string.kanji_detail_title, TopLevelDestination.LEARN, isTabRoot = false),
    KANJI_EDIT(KanjiEditRoute::class, R.string.kanji_edit_title, TopLevelDestination.LEARN, isTabRoot = false),
    VOCABULARY_EDIT(VocabularyEditRoute::class, R.string.vocabulary_edit_title, TopLevelDestination.LEARN, isTabRoot = false),
    ADD_WORD(AddWordRoute::class, R.string.add_word_title, TopLevelDestination.LEARN, isTabRoot = false),
    GRAMMAR(GrammarRoute::class, R.string.grammar_title, TopLevelDestination.LEARN, isTabRoot = false),
    GRAMMAR_DETAIL(GrammarDetailRoute::class, R.string.grammar_detail_title, TopLevelDestination.LEARN, isTabRoot = false),
    GRAMMAR_EDIT(GrammarEditRoute::class, R.string.grammar_edit_title, TopLevelDestination.LEARN, isTabRoot = false),
    PRACTICE(PracticeRoute::class, R.string.practice_title, TopLevelDestination.LEARN, isTabRoot = false, isFocusSession = true),
    PRACTICE_RESULT(PracticeResultRoute::class, R.string.practice_result_title, TopLevelDestination.LEARN, isTabRoot = false),
    REVIEW(ReviewRoute::class, R.string.review_title, TopLevelDestination.REVIEW, isTabRoot = true),
    REVIEW_SESSION(ReviewSessionRoute::class, R.string.review_session_title, TopLevelDestination.REVIEW, isTabRoot = false, isFocusSession = true),
    REVIEW_RESULT(ReviewResultRoute::class, R.string.review_result_title, TopLevelDestination.REVIEW, isTabRoot = false),
    STATS(StatsRoute::class, R.string.stats_title, TopLevelDestination.STATS, isTabRoot = true),
    SETTINGS(SettingsRoute::class, R.string.settings_title, TopLevelDestination.SETTINGS, isTabRoot = true),
    REVIEW_SETTINGS(ReviewSettingsRoute::class, R.string.review_settings_title, TopLevelDestination.SETTINGS, isTabRoot = false),
    KANJI_REVIEW_LESSONS(KanjiReviewLessonsRoute::class, R.string.kanji_review_lessons_title, TopLevelDestination.SETTINGS, isTabRoot = false),
    REMINDER_SETTINGS(ReminderSettingsRoute::class, R.string.reminder_settings_title, TopLevelDestination.SETTINGS, isTabRoot = false),
    REMINDER_LESSONS(ReminderLessonsRoute::class, R.string.reminder_lessons_title, TopLevelDestination.SETTINGS, isTabRoot = false),
    WIDGET_SETTINGS(WidgetSettingsRoute::class, R.string.widget_settings_title, TopLevelDestination.SETTINGS, isTabRoot = false);

    fun matches(destination: NavDestination): Boolean = destination.hasRoute(route)

    companion object {
        fun from(destination: NavDestination?): AppScreen? =
            destination?.let { dest -> entries.firstOrNull { it.matches(dest) } }
    }
}
