package com.n3sensei.app.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.navigation
import androidx.navigation.toRoute
import com.n3sensei.app.R
import com.n3sensei.app.app.di.appViewModel
import com.n3sensei.app.app.reminder.ReminderDeepLinks
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.LearningStatus
import com.n3sensei.app.domain.practice.PracticeResult
import com.n3sensei.app.domain.srs.SubmitReviewUseCase
import com.n3sensei.app.feature.grammar.GrammarDetailScreen
import com.n3sensei.app.feature.grammar.GrammarDetailViewModel
import com.n3sensei.app.feature.grammar.GrammarListScreen
import com.n3sensei.app.feature.grammar.GrammarListViewModel
import com.n3sensei.app.feature.home.HomeScreen
import com.n3sensei.app.feature.home.HomeViewModel
import com.n3sensei.app.feature.kanji.KanjiDetailScreen
import com.n3sensei.app.feature.kanji.KanjiDetailViewModel
import com.n3sensei.app.feature.kanji.KanjiListScreen
import com.n3sensei.app.feature.kanji.KanjiListViewModel
import com.n3sensei.app.feature.learn.LearnHubScreen
import com.n3sensei.app.feature.lessons.LessonDetailScreen
import com.n3sensei.app.feature.lessons.LessonDetailViewModel
import com.n3sensei.app.feature.lessons.LessonListScreen
import com.n3sensei.app.feature.lessons.LessonListViewModel
import com.n3sensei.app.feature.practice.PracticeResultScreen
import com.n3sensei.app.feature.practice.PracticeScreen
import com.n3sensei.app.feature.practice.PracticeViewModel
import com.n3sensei.app.feature.review.ReviewScreen
import com.n3sensei.app.feature.review.ReviewViewModel
import com.n3sensei.app.feature.reviewsession.ReviewSessionScreen
import com.n3sensei.app.feature.reviewsession.ReviewSessionViewModel
import com.n3sensei.app.feature.settings.ReminderSettingsScreen
import com.n3sensei.app.feature.settings.ReminderSettingsViewModel
import com.n3sensei.app.feature.settings.ReviewSettingsScreen
import com.n3sensei.app.feature.settings.ReviewSettingsViewModel
import com.n3sensei.app.feature.settings.SettingsScreen
import com.n3sensei.app.feature.settings.SettingsViewModel
import com.n3sensei.app.feature.stats.StatsScreen
import com.n3sensei.app.feature.stats.StatsViewModel
import com.n3sensei.app.feature.vocabulary.VocabularyDetailScreen
import com.n3sensei.app.feature.vocabulary.VocabularyDetailViewModel
import com.n3sensei.app.feature.vocabulary.VocabularyListScreen
import com.n3sensei.app.feature.vocabulary.VocabularyListViewModel

/**
 * The whole navigation graph. ViewModels are created here from the app container and passed
 * to feature screens together with navigation callbacks; features never see the NavController
 * or the container.
 */
@Composable
fun N3SenseiNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.START.route,
        modifier = modifier,
    ) {
        composable<HomeRoute> {
            HomeScreen(
                viewModel = appViewModel { HomeViewModel(analyticsRepository, reviewSessionRepository, userPreferencesRepository, clock) },
                onOpenReview = { navController.navigateToTopLevel(TopLevelDestination.REVIEW) },
                onOpenLessons = { navController.navigateToLessons() },
                onOpenItem = { navController.navigateToItem(it) },
                onOpenStats = { navController.navigateToTopLevel(TopLevelDestination.STATS) },
            )
        }
        navigation<LearnGraphRoute>(startDestination = LearnHubRoute) {
            composable<LearnHubRoute> {
                LearnHubScreen(
                    onOpenLessons = { navController.navigate(LessonListRoute) },
                    onOpenVocabulary = { navController.navigate(VocabularyRoute()) },
                    onOpenKanji = { navController.navigate(KanjiRoute()) },
                    onOpenGrammar = { navController.navigate(GrammarRoute()) },
                )
            }
            composable<LessonListRoute> {
                LessonListScreen(
                    viewModel = appViewModel { LessonListViewModel(contentRepository) },
                    onOpenLesson = { navController.navigate(LessonDetailRoute(it)) },
                )
            }
            composable<LessonDetailRoute> { entry ->
                val route = entry.toRoute<LessonDetailRoute>()
                LessonDetailScreen(
                    viewModel = appViewModel { LessonDetailViewModel(route.lessonId, contentRepository) },
                    onOpenVocabulary = { navController.navigate(VocabularyDetailRoute(it)) },
                    onOpenKanji = { navController.navigate(KanjiDetailRoute(it)) },
                    onOpenGrammar = { navController.navigate(GrammarDetailRoute(it)) },
                    onStartPractice = { navController.navigate(PracticeRoute(lessonId = route.lessonId)) },
                )
            }
            composable<VocabularyRoute> { entry ->
                val status = entry.toRoute<VocabularyRoute>().status.toLearningStatus()
                VocabularyListScreen(
                    viewModel = appViewModel { VocabularyListViewModel(contentRepository, status) },
                    onOpenVocabulary = { navController.navigate(VocabularyDetailRoute(it)) },
                    onPracticeMarked = { navController.navigate(PracticeRoute(markedOnly = true)) },
                )
            }
            composable<VocabularyDetailRoute>(
                deepLinks = listOf(navDeepLink<VocabularyDetailRoute>(basePath = ReminderDeepLinks.VOCABULARY_BASE)),
            ) { entry ->
                val id = entry.toRoute<VocabularyDetailRoute>().vocabularyId
                VocabularyDetailScreen(
                    viewModel = appViewModel { VocabularyDetailViewModel(id, contentRepository, progressRepository, userPreferencesRepository, clock) },
                    onPracticeLesson = { navController.navigate(PracticeRoute(lessonId = it)) },
                )
            }
            composable<KanjiRoute> { entry ->
                val status = entry.toRoute<KanjiRoute>().status.toLearningStatus()
                KanjiListScreen(
                    viewModel = appViewModel { KanjiListViewModel(contentRepository, status) },
                    onOpenKanji = { navController.navigate(KanjiDetailRoute(it)) },
                    onPracticeMarked = { navController.navigate(PracticeRoute(markedOnly = true)) },
                )
            }
            composable<KanjiDetailRoute> { entry ->
                val id = entry.toRoute<KanjiDetailRoute>().kanjiId
                KanjiDetailScreen(
                    viewModel = appViewModel { KanjiDetailViewModel(id, contentRepository, progressRepository, userPreferencesRepository, clock) },
                    onPracticeLesson = { navController.navigate(PracticeRoute(lessonId = it)) },
                )
            }
            composable<GrammarRoute> { entry ->
                val status = entry.toRoute<GrammarRoute>().status.toLearningStatus()
                GrammarListScreen(
                    viewModel = appViewModel { GrammarListViewModel(contentRepository, status) },
                    onOpenGrammar = { navController.navigate(GrammarDetailRoute(it)) },
                    onPracticeMarked = { navController.navigate(PracticeRoute(markedOnly = true)) },
                )
            }
            composable<GrammarDetailRoute> { entry ->
                val id = entry.toRoute<GrammarDetailRoute>().grammarId
                GrammarDetailScreen(
                    viewModel = appViewModel { GrammarDetailViewModel(id, contentRepository, progressRepository, userPreferencesRepository, clock) },
                    onPracticeLesson = { navController.navigate(PracticeRoute(lessonId = it)) },
                )
            }
            composable<PracticeRoute> { entry ->
                val route = entry.toRoute<PracticeRoute>()
                PracticeScreen(
                    viewModel = appViewModel { PracticeViewModel(route.lessonId, route.markedOnly, contentRepository, clock) },
                    markedOnly = route.markedOnly,
                    onFinished = { result ->
                        // Replace the finished session so Back from the result returns to where it started.
                        navController.navigate(result.toRoute(route)) {
                            popUpTo<PracticeRoute> { inclusive = true }
                        }
                    },
                )
            }
            composable<PracticeResultRoute> { entry ->
                val route = entry.toRoute<PracticeResultRoute>()
                PracticeResultScreen(
                    result = PracticeResult(route.total, route.again, route.hard, route.good, route.easy, route.durationSeconds),
                    onPracticeAgain = {
                        navController.navigate(PracticeRoute(route.lessonId, route.markedOnly)) {
                            popUpTo<PracticeResultRoute> { inclusive = true }
                        }
                    },
                    onBackToLesson = { navController.popBackStack() },
                    backLabelRes = if (route.markedOnly) R.string.common_navigate_up else R.string.practice_result_back,
                )
            }
        }
        composable<ReviewRoute> {
            ReviewScreen(
                viewModel = appViewModel { ReviewViewModel(progressRepository, userPreferencesRepository, clock) },
                onStartSession = { navController.navigate(ReviewSessionRoute) },
                onOpenLessons = { navController.navigateToLessons() },
                onPracticeMarked = { navController.navigate(PracticeRoute(markedOnly = true)) },
            )
        }
        composable<ReviewSessionRoute> {
            ReviewSessionScreen(
                viewModel = appViewModel {
                    ReviewSessionViewModel(
                        contentRepository, progressRepository, userPreferencesRepository,
                        SubmitReviewUseCase(progressRepository), reviewSessionRepository, clock,
                    )
                },
                onFinished = { result ->
                    navController.navigate(
                        ReviewResultRoute(result.total, result.again, result.hard, result.good, result.easy, result.durationSeconds),
                    ) { popUpTo<ReviewSessionRoute> { inclusive = true } }
                },
            )
        }
        composable<ReviewResultRoute> { entry ->
            val route = entry.toRoute<ReviewResultRoute>()
            PracticeResultScreen(
                result = PracticeResult(route.total, route.again, route.hard, route.good, route.easy, route.durationSeconds),
                onPracticeAgain = {
                    navController.navigate(ReviewSessionRoute) { popUpTo<ReviewResultRoute> { inclusive = true } }
                },
                onBackToLesson = { navController.popBackStack() },
                backLabelRes = R.string.review_result_back,
                againLabelRes = R.string.review_result_again,
                saved = true,
            )
        }
        composable<StatsRoute> {
            StatsScreen(
                viewModel = appViewModel { StatsViewModel(analyticsRepository, clock) },
                onOpenReview = { navController.navigateToTopLevel(TopLevelDestination.REVIEW) },
                onOpenList = { type, status -> navController.navigateToItemList(type, status) },
                onOpenItem = { ref -> navController.navigateToItem(ref) },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                viewModel = appViewModel { SettingsViewModel(userPreferencesRepository, contentRepository, contentImportRepository) },
                onOpenReviewSettings = { navController.navigate(ReviewSettingsRoute) },
                onOpenReminderSettings = { navController.navigate(ReminderSettingsRoute) },
            )
        }
        composable<ReminderSettingsRoute> {
            ReminderSettingsScreen(
                viewModel = appViewModel {
                    ReminderSettingsViewModel(userPreferencesRepository, reminderScheduler, reminderNotifier, reminderRunner)
                },
            )
        }
        composable<ReviewSettingsRoute> {
            ReviewSettingsScreen(viewModel = appViewModel { ReviewSettingsViewModel(userPreferencesRepository) })
        }
    }
}

private fun PracticeResult.toRoute(practice: PracticeRoute) =
    PracticeResultRoute(practice.lessonId, practice.markedOnly, total, again, hard, good, easy, durationSeconds)

private fun String?.toLearningStatus(): LearningStatus? = LearningStatus.entries.firstOrNull { it.name == this }

/** Opens a vocabulary / kanji / grammar list in the Learn tab, optionally filtered by status. */
private fun NavHostController.navigateToItemList(type: ItemType, status: LearningStatus?) {
    navigateToTopLevel(TopLevelDestination.LEARN)
    when (type) {
        ItemType.VOCABULARY -> navigate(VocabularyRoute(status?.name))
        ItemType.KANJI -> navigate(KanjiRoute(status?.name))
        ItemType.GRAMMAR -> navigate(GrammarRoute(status?.name))
    }
}

private fun NavHostController.navigateToItem(ref: ItemRef) {
    navigateToTopLevel(TopLevelDestination.LEARN)
    when (ref.type) {
        ItemType.VOCABULARY -> navigate(VocabularyDetailRoute(ref.id))
        ItemType.KANJI -> navigate(KanjiDetailRoute(ref.id))
        ItemType.GRAMMAR -> navigate(GrammarDetailRoute(ref.id))
    }
}
