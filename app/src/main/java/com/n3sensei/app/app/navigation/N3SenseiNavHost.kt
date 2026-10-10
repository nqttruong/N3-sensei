package com.n3sensei.app.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import androidx.navigation.toRoute
import com.n3sensei.app.app.di.appViewModel
import com.n3sensei.app.domain.practice.PracticeResult
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
                viewModel = appViewModel { HomeViewModel(contentRepository, progressRepository, clock) },
                onOpenLessons = { navController.navigateToLessons() },
                onOpenReview = { navController.navigateToTopLevel(TopLevelDestination.REVIEW) },
            )
        }
        navigation<LearnGraphRoute>(startDestination = LearnHubRoute) {
            composable<LearnHubRoute> {
                LearnHubScreen(
                    onOpenLessons = { navController.navigate(LessonListRoute) },
                    onOpenVocabulary = { navController.navigate(VocabularyRoute) },
                    onOpenKanji = { navController.navigate(KanjiRoute) },
                    onOpenGrammar = { navController.navigate(GrammarRoute) },
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
                    onStartPractice = { navController.navigate(PracticeRoute(route.lessonId)) },
                )
            }
            composable<VocabularyRoute> {
                VocabularyListScreen(
                    viewModel = appViewModel { VocabularyListViewModel(contentRepository) },
                    onOpenVocabulary = { navController.navigate(VocabularyDetailRoute(it)) },
                )
            }
            composable<VocabularyDetailRoute> { entry ->
                val id = entry.toRoute<VocabularyDetailRoute>().vocabularyId
                VocabularyDetailScreen(viewModel = appViewModel { VocabularyDetailViewModel(id, contentRepository) })
            }
            composable<KanjiRoute> {
                KanjiListScreen(
                    viewModel = appViewModel { KanjiListViewModel(contentRepository) },
                    onOpenKanji = { navController.navigate(KanjiDetailRoute(it)) },
                )
            }
            composable<KanjiDetailRoute> { entry ->
                val id = entry.toRoute<KanjiDetailRoute>().kanjiId
                KanjiDetailScreen(viewModel = appViewModel { KanjiDetailViewModel(id, contentRepository) })
            }
            composable<GrammarRoute> {
                GrammarListScreen(
                    viewModel = appViewModel { GrammarListViewModel(contentRepository) },
                    onOpenGrammar = { navController.navigate(GrammarDetailRoute(it)) },
                )
            }
            composable<GrammarDetailRoute> { entry ->
                val id = entry.toRoute<GrammarDetailRoute>().grammarId
                GrammarDetailScreen(viewModel = appViewModel { GrammarDetailViewModel(id, contentRepository) })
            }
            composable<PracticeRoute> { entry ->
                val lessonId = entry.toRoute<PracticeRoute>().lessonId
                PracticeScreen(
                    viewModel = appViewModel { PracticeViewModel(lessonId, contentRepository, clock) },
                    onFinished = { result ->
                        // Replace the finished session so Back from the result returns to the lesson.
                        navController.navigate(result.toRoute(lessonId)) {
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
                        navController.navigate(PracticeRoute(route.lessonId)) {
                            popUpTo<PracticeResultRoute> { inclusive = true }
                        }
                    },
                    onBackToLesson = { navController.popBackStack() },
                )
            }
        }
        composable<ReviewRoute> {
            ReviewScreen(
                viewModel = appViewModel { ReviewViewModel(progressRepository, clock) },
                onOpenLessons = { navController.navigateToLessons() },
            )
        }
        composable<StatsRoute> {
            StatsScreen(viewModel = appViewModel { StatsViewModel(contentRepository, progressRepository, clock) })
        }
        composable<SettingsRoute> {
            SettingsScreen(viewModel = appViewModel { SettingsViewModel(userPreferencesRepository, contentRepository) })
        }
    }
}

private fun PracticeResult.toRoute(lessonId: String) =
    PracticeResultRoute(lessonId, total, again, hard, good, easy, durationSeconds)
