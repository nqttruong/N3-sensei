package com.n3sensei.app.app.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.navigation
import androidx.navigation.toRoute
import com.n3sensei.app.R
import com.n3sensei.app.app.di.appSavedStateViewModel
import com.n3sensei.app.app.di.appViewModel
import com.n3sensei.app.app.reminder.ReminderDeepLinks
import com.n3sensei.app.core.designsystem.theme.LocalReduceMotion
import com.n3sensei.app.core.designsystem.theme.N3Motion
import com.n3sensei.app.domain.edit.UserWords
import com.n3sensei.app.domain.dashboard.ExamDashboardManager
import com.n3sensei.app.domain.dashboard.PlanTask
import com.n3sensei.app.domain.exam.AnswerManager
import com.n3sensei.app.domain.exam.ExamSkill
import com.n3sensei.app.domain.exam.ExamResultManager
import com.n3sensei.app.domain.exam.ExamSessionManager
import com.n3sensei.app.domain.exam.ExamSubmissionManager
import com.n3sensei.app.domain.mistakes.ErrorReviewSessionManager
import com.n3sensei.app.domain.mistakes.MistakeAnalysisManager
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.LearningStatus
import com.n3sensei.app.domain.practice.PracticeResult
import com.n3sensei.app.domain.srs.SubmitReviewUseCase
import com.n3sensei.app.feature.edit.ContentEditScreen
import com.n3sensei.app.feature.edit.ContentEditViewModel
import com.n3sensei.app.feature.edit.EditTarget
import com.n3sensei.app.feature.exam.ExamIntroScreen
import com.n3sensei.app.feature.exam.ExamIntroViewModel
import com.n3sensei.app.feature.exam.ExamListScreen
import com.n3sensei.app.feature.exam.ExamListViewModel
import com.n3sensei.app.feature.exam.ExamResultScreen
import com.n3sensei.app.feature.exam.ExamResultViewModel
import com.n3sensei.app.feature.exam.ExamReviewScreen
import com.n3sensei.app.feature.exam.ExamReviewViewModel
import com.n3sensei.app.feature.exam.MistakeActions
import com.n3sensei.app.feature.exam.MistakeAnalysisScreen
import com.n3sensei.app.feature.exam.MistakeAnalysisViewModel
import com.n3sensei.app.feature.exam.ErrorDetailScreen
import com.n3sensei.app.feature.exam.ExamDashboardScreen
import com.n3sensei.app.feature.exam.ExamDashboardViewModel
import com.n3sensei.app.feature.exam.SkillDetailScreen
import com.n3sensei.app.feature.exam.SkillDetailViewModel
import com.n3sensei.app.feature.exam.ErrorDetailViewModel
import com.n3sensei.app.feature.exam.ErrorNotebookScreen
import com.n3sensei.app.feature.exam.ErrorNotebookViewModel
import com.n3sensei.app.feature.exam.ErrorReviewSessionScreen
import com.n3sensei.app.feature.exam.ErrorReviewSessionViewModel
import com.n3sensei.app.feature.exam.WeaknessScreen
import com.n3sensei.app.feature.exam.WeaknessViewModel
import com.n3sensei.app.feature.exam.ReviewFilter
import com.n3sensei.app.feature.exam.ExamSessionScreen
import com.n3sensei.app.feature.exam.ExamSessionViewModel
import com.n3sensei.app.feature.grammar.GrammarDetailScreen
import com.n3sensei.app.feature.grammar.GrammarDetailViewModel
import com.n3sensei.app.feature.grammar.GrammarListScreen
import com.n3sensei.app.feature.grammar.GrammarListViewModel
import com.n3sensei.app.feature.home.HomeScreen
import com.n3sensei.app.core.ui.state.DashboardViewModel
import com.n3sensei.app.feature.home.ProverbViewModel
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
import com.n3sensei.app.feature.settings.BackupViewModel
import com.n3sensei.app.feature.settings.AndroidSyncViewModel
import com.n3sensei.app.feature.settings.JlptAiReviewViewModel
import com.n3sensei.app.feature.settings.ReminderLessonsScreen
import com.n3sensei.app.feature.settings.ReminderLessonsViewModel
import com.n3sensei.app.feature.settings.ReminderSettingsScreen
import com.n3sensei.app.feature.settings.ReminderSettingsViewModel
import com.n3sensei.app.feature.settings.KanjiReviewLessonsScreen
import com.n3sensei.app.feature.settings.KanjiReviewLessonsViewModel
import com.n3sensei.app.feature.settings.ReviewSettingsScreen
import com.n3sensei.app.feature.settings.ReviewSettingsViewModel
import com.n3sensei.app.feature.settings.SettingsScreen
import com.n3sensei.app.feature.settings.SettingsViewModel
import com.n3sensei.app.feature.settings.WidgetSettingsScreen
import com.n3sensei.app.feature.settings.WidgetSettingsViewModel
import com.n3sensei.app.feature.stats.StatsScreen
import com.n3sensei.app.feature.stats.StatsViewModel
import com.n3sensei.app.feature.vocabulary.VocabularyDetailScreen
import com.n3sensei.app.feature.vocabulary.VocabularyDetailViewModel
import com.n3sensei.app.feature.vocabulary.VocabularyGroupScreen
import com.n3sensei.app.feature.vocabulary.VocabularyGroupViewModel
import com.n3sensei.app.feature.vocabulary.VocabularyGroupsScreen
import com.n3sensei.app.feature.vocabulary.VocabularyGroupsViewModel
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
    // Short fade with a small rise instead of the default 700 ms crossfade, which made every
    // tap feel slow. Only the fade is kept when the system asks for reduced motion.
    val reduceMotion = LocalReduceMotion.current
    val rise = if (reduceMotion) 0 else with(LocalDensity.current) { 24.dp.roundToPx() }
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.START.route,
        modifier = modifier,
        enterTransition = {
            fadeIn(tween(N3Motion.MEDIUM, easing = N3Motion.EnterEasing)) +
                slideInVertically(tween(N3Motion.MEDIUM, easing = N3Motion.EnterEasing)) { rise }
        },
        exitTransition = { fadeOut(tween(N3Motion.SHORT)) },
        popEnterTransition = { fadeIn(tween(N3Motion.MEDIUM, easing = N3Motion.EnterEasing)) },
        popExitTransition = {
            fadeOut(tween(N3Motion.SHORT)) + slideOutVertically(tween(N3Motion.SHORT)) { rise }
        },
    ) {
        composable<HomeRoute> {
            HomeScreen(
                viewModel = appViewModel { DashboardViewModel(analyticsRepository, reviewSessionRepository, userPreferencesRepository, clock, progressRepository) },
                proverbViewModel = appViewModel { ProverbViewModel(proverbRepository, clock) },
                onOpenReview = { navController.navigateToTopLevel(TopLevelDestination.REVIEW) },
                onStartSession = { navController.navigate(ReviewSessionRoute()) },
                onOpenLessons = { navController.navigateToLessons() },
                onOpenItem = { navController.navigateToItem(it) },
            )
        }
        navigation<LearnGraphRoute>(startDestination = LearnHubRoute) {
            composable<LearnHubRoute> {
                LearnHubScreen(
                    onOpenLessons = { navController.navigate(LessonListRoute) },
                    onOpenVocabulary = { navController.navigate(VocabularyGroupsRoute) },
                    onOpenKanji = { navController.navigate(KanjiRoute()) },
                    onOpenGrammar = { navController.navigate(GrammarRoute()) },
                    onOpenMyWords = { navController.navigate(MyWordsRoute) },
                    onOpenExams = { navController.navigate(ExamListRoute) },
                    onOpenErrors = { navController.navigate(ErrorNotebookRoute()) },
                    onOpenJlpt = { navController.navigate(ExamDashboardRoute) },
                )
            }
            composable<ExamListRoute> {
                ExamListScreen(
                    viewModel = appViewModel { ExamListViewModel(examRepository, ExamSubmissionManager(examRepository, clock)) },
                    onOpenExam = { navController.navigate(ExamIntroRoute(it)) },
                )
            }
            composable<ExamIntroRoute> { entry ->
                val id = entry.toRoute<ExamIntroRoute>().examSetId
                ExamIntroScreen(
                    viewModel = appViewModel {
                        ExamIntroViewModel(id, examRepository, ExamSessionManager(examRepository, ExamSubmissionManager(examRepository, clock)), clock)
                    },
                    onStart = { navController.navigate(ExamSessionRoute(id)) },
                    onResume = { navController.navigate(ExamSessionRoute(id, attemptId = it)) },
                    onOpenResult = { navController.navigate(ExamResultRoute(it)) },
                )
            }
            composable<ExamSessionRoute> { entry ->
                val route = entry.toRoute<ExamSessionRoute>()
                ExamSessionScreen(
                    viewModel = appSavedStateViewModel { saved ->
                        val submission = ExamSubmissionManager(examRepository, clock)
                        ExamSessionViewModel(
                            route.examSetId, saved, ExamSessionManager(examRepository, submission), AnswerManager(examRepository, clock),
                            submission, clock, resumeAttemptId = route.attemptId, autoStart = route.attemptId == null,
                        )
                    },
                    onDone = { navController.popBackStack() },
                    onSubmitted = { attemptId ->
                        navController.navigate(ExamResultRoute(attemptId)) { popUpTo<ExamSessionRoute> { inclusive = true } }
                    },
                )
            }
            composable<ExamResultRoute> { entry ->
                val attemptId = entry.toRoute<ExamResultRoute>().attemptId
                ExamResultScreen(
                    viewModel = appViewModel { ExamResultViewModel(attemptId, ExamResultManager(examRepository)) },
                    onReview = { filter, position -> navController.navigate(ExamReviewRoute(attemptId, filter.name, position)) },
                    onOpenNotebook = { navController.navigate(ErrorNotebookRoute()) },
                    onNewExam = { navController.navigate(ExamListRoute) { popUpTo<ExamListRoute> { inclusive = true } } },
                    onRetake = { setId -> navController.navigate(ExamIntroRoute(setId)) { popUpTo<ExamIntroRoute> { inclusive = true } } },
                    onOpenAttempt = { navController.navigate(ExamResultRoute(it)) },
                    onAnalyze = { navController.navigate(MistakeAnalysisRoute(attemptId)) },
                )
            }
            composable<ExamReviewRoute> { entry ->
                val route = entry.toRoute<ExamReviewRoute>()
                val filter = ReviewFilter.entries.firstOrNull { it.name == route.filter } ?: ReviewFilter.ALL
                ExamReviewScreen(
                    viewModel = appViewModel { ExamReviewViewModel(route.attemptId, filter, ExamResultManager(examRepository), errorRepository) },
                    startPosition = route.position,
                    onOpenError = { navController.navigate(ErrorDetailRoute(it)) },
                )
            }
            composable<MistakeAnalysisRoute> { entry ->
                val attemptId = entry.toRoute<MistakeAnalysisRoute>().attemptId
                val viewModel = appViewModel {
                    MistakeAnalysisViewModel(attemptId, MistakeAnalysisManager(examRepository, contentRepository, progressRepository))
                }
                MistakeAnalysisScreen(viewModel, navController.mistakeActions { refs, then -> viewModel.practiceWords(refs, then) })
            }
            composable<ErrorNotebookRoute> {
                ErrorNotebookScreen(
                    viewModel = appSavedStateViewModel { saved -> ErrorNotebookViewModel(errorRepository, saved, clock) },
                    onOpenError = { navController.navigate(ErrorDetailRoute(it)) },
                    onOpenWeakness = { navController.navigate(WeaknessRoute) },
                    onReviewDue = { navController.navigate(ErrorReviewRoute()) },
                )
            }
            composable<ErrorDetailRoute> { entry ->
                val errorId = entry.toRoute<ErrorDetailRoute>().errorId
                val viewModel = appViewModel {
                    ErrorDetailViewModel(errorId, errorRepository, MistakeAnalysisManager(examRepository, contentRepository, progressRepository))
                }
                ErrorDetailScreen(
                    viewModel = viewModel,
                    actions = navController.mistakeActions { refs, then -> viewModel.practiceWords(refs, then) },
                    onReviewNow = { navController.navigate(ErrorReviewRoute(errorId)) },
                )
            }
            composable<ErrorReviewRoute>(
                deepLinks = listOf(navDeepLink<ErrorReviewRoute>(basePath = ReminderDeepLinks.ERROR_REVIEW)),
            ) { entry ->
                val route = entry.toRoute<ErrorReviewRoute>()
                ErrorReviewSessionScreen(
                    viewModel = appViewModel {
                        ErrorReviewSessionViewModel(
                            route.errorId, ErrorReviewSessionManager(errorRepository, examRepository, clock),
                            ExamSkill.entries.firstOrNull { it.name == route.skill }, route.limit,
                        )
                    },
                    onDone = { navController.popBackStack() },
                )
            }
            composable<WeaknessRoute> {
                val viewModel = appViewModel { WeaknessViewModel(MistakeAnalysisManager(examRepository, contentRepository, progressRepository)) }
                WeaknessScreen(viewModel, navController.mistakeActions { refs, then -> viewModel.practiceWords(refs, then) })
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
                    viewModel = appViewModel { LessonDetailViewModel(route.number, contentRepository) },
                    onOpenVocabulary = { navController.navigate(VocabularyDetailRoute(it)) },
                    onOpenKanji = { navController.navigate(KanjiDetailRoute(it)) },
                    onOpenGrammar = { navController.navigate(GrammarDetailRoute(it)) },
                    onStartPractice = { navController.navigate(PracticeRoute(courseLesson = route.number)) },
                )
            }
            composable<VocabularyRoute> { entry ->
                val route = entry.toRoute<VocabularyRoute>()
                val status = route.status.toLearningStatus()
                VocabularyListScreen(
                    viewModel = appViewModel { VocabularyListViewModel(contentRepository, status, route.lessonId) },
                    onOpenVocabulary = { navController.navigate(VocabularyDetailRoute(it)) },
                    onPracticeMarked = { navController.navigate(PracticeRoute(markedOnly = true)) },
                    onAddWord = { navController.navigate(AddWordRoute) },
                )
            }
            composable<VocabularyGroupsRoute> {
                VocabularyGroupsScreen(
                    viewModel = appViewModel { VocabularyGroupsViewModel(contentRepository) },
                    onOpenGroup = { navController.navigate(VocabularyGroupRoute(it)) },
                    onOpenAll = { navController.navigate(VocabularyRoute()) },
                )
            }
            composable<VocabularyGroupRoute> { entry ->
                val key = entry.toRoute<VocabularyGroupRoute>().key
                VocabularyGroupScreen(
                    viewModel = appViewModel { VocabularyGroupViewModel(key, contentRepository) },
                    onOpenVocabulary = { navController.navigate(VocabularyDetailRoute(it)) },
                    onPractice = { navController.navigate(PracticeRoute(vocabularyGroup = key)) },
                )
            }
            composable<MyWordsRoute> {
                VocabularyListScreen(
                    viewModel = appViewModel { VocabularyListViewModel(contentRepository, myWords = true) },
                    onOpenVocabulary = { navController.navigate(VocabularyDetailRoute(it)) },
                    // Marked words of this list only, not marked words of every pack.
                    onPracticeMarked = { navController.navigate(PracticeRoute(lessonId = UserWords.LESSON_ID, markedOnly = true)) },
                    onAddWord = { navController.navigate(AddWordRoute) },
                )
            }
            composable<VocabularyDetailRoute>(
                deepLinks = listOf(navDeepLink<VocabularyDetailRoute>(basePath = ReminderDeepLinks.VOCABULARY_BASE)),
            ) { entry ->
                val id = entry.toRoute<VocabularyDetailRoute>().vocabularyId
                VocabularyDetailScreen(
                    viewModel = appViewModel { VocabularyDetailViewModel(id, contentRepository, progressRepository, userPreferencesRepository, clock) },
                    onPracticeLesson = { navController.navigate(PracticeRoute(lessonId = it)) },
                    onEdit = { navController.navigate(VocabularyEditRoute(id)) },
                    onOpenKanji = { navController.navigate(KanjiDetailRoute(it)) },
                )
            }
            composable<VocabularyEditRoute> { entry ->
                val id = entry.toRoute<VocabularyEditRoute>().vocabularyId
                ContentEditScreen(
                    viewModel = appViewModel { ContentEditViewModel(EditTarget.Existing(ItemType.VOCABULARY, id), userContentRepository) },
                    onDone = { deleted ->
                        navController.popBackStack()
                        // The word no longer exists: leave its detail screen too.
                        if (deleted) navController.popBackStack()
                    },
                    onCancel = { navController.popBackStack() },
                    onOpenWord = { navController.navigate(VocabularyDetailRoute(it)) },
                )
            }
            composable<AddWordRoute> {
                ContentEditScreen(
                    viewModel = appViewModel { ContentEditViewModel(EditTarget.NewWord, userContentRepository) },
                    onDone = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() },
                    onOpenWord = { navController.navigate(VocabularyDetailRoute(it)) },
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
            composable<KanjiDetailRoute>(
                deepLinks = listOf(navDeepLink<KanjiDetailRoute>(basePath = ReminderDeepLinks.KANJI_BASE)),
            ) { entry ->
                val id = entry.toRoute<KanjiDetailRoute>().kanjiId
                KanjiDetailScreen(
                    viewModel = appViewModel { KanjiDetailViewModel(id, contentRepository, progressRepository, userPreferencesRepository, clock) },
                    onPracticeLesson = { navController.navigate(PracticeRoute(lessonId = it)) },
                    onEdit = { navController.navigate(KanjiEditRoute(id)) },
                    onOpenVocabulary = { navController.navigate(VocabularyDetailRoute(it)) },
                )
            }
            composable<KanjiEditRoute> { entry ->
                val id = entry.toRoute<KanjiEditRoute>().kanjiId
                ContentEditScreen(
                    viewModel = appViewModel { ContentEditViewModel(EditTarget.Existing(ItemType.KANJI, id), userContentRepository) },
                    onDone = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() },
                    onOpenWord = { navController.navigate(VocabularyDetailRoute(it)) },
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
            composable<GrammarDetailRoute>(
                deepLinks = listOf(navDeepLink<GrammarDetailRoute>(basePath = ReminderDeepLinks.GRAMMAR_BASE)),
            ) { entry ->
                val id = entry.toRoute<GrammarDetailRoute>().grammarId
                GrammarDetailScreen(
                    viewModel = appViewModel { GrammarDetailViewModel(id, contentRepository, progressRepository, userPreferencesRepository, clock) },
                    onPracticeLesson = { navController.navigate(PracticeRoute(lessonId = it)) },
                    onEdit = { navController.navigate(GrammarEditRoute(id)) },
                )
            }
            composable<GrammarEditRoute> { entry ->
                val id = entry.toRoute<GrammarEditRoute>().grammarId
                ContentEditScreen(
                    viewModel = appViewModel { ContentEditViewModel(EditTarget.Existing(ItemType.GRAMMAR, id), userContentRepository) },
                    onDone = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() },
                    onOpenWord = { navController.navigate(VocabularyDetailRoute(it)) },
                )
            }
            composable<PracticeRoute> { entry ->
                val route = entry.toRoute<PracticeRoute>()
                PracticeScreen(
                    viewModel = appViewModel {
                        PracticeViewModel(
                            route.lessonId, route.markedOnly, contentRepository, clock,
                            progressRepository, userPreferencesRepository, route.courseLesson, route.vocabularyGroup,
                        )
                    },
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
                        navController.navigate(PracticeRoute(route.lessonId, route.markedOnly, route.courseLesson, route.vocabularyGroup)) {
                            popUpTo<PracticeResultRoute> { inclusive = true }
                        }
                    },
                    onBackToLesson = { navController.popBackStack() },
                    backLabelRes = if (route.markedOnly) R.string.common_navigate_up else R.string.practice_result_back,
                )
            }
            composable<ExamDashboardRoute> {
                val viewModel = appViewModel {
                    ExamDashboardViewModel(
                        ExamDashboardManager(examRepository, errorRepository, progressRepository, contentRepository, userPreferencesRepository, clock),
                    )
                }
                ExamDashboardScreen(
                    viewModel = viewModel,
                    onTask = { task ->
                        when (task) {
                            is PlanTask.ReviewErrors -> navController.navigate(ErrorReviewRoute(skill = task.skill?.name, limit = task.count))
                            is PlanTask.StudyWeakItems -> viewModel.practice(task.refs) { navController.navigate(PracticeRoute(markedOnly = true)) }
                            is PlanTask.TakeSectionExam -> navController.navigate(ExamIntroRoute(task.examSetId))
                            is PlanTask.ReviewFlashcards -> navController.navigate(ReviewSessionRoute())
                        }
                    },
                    onOpenSkill = { navController.navigate(SkillDetailRoute(it.name)) },
                )
            }
            composable<SkillDetailRoute> { entry ->
                val skill = ExamSkill.entries.firstOrNull { it.name == entry.toRoute<SkillDetailRoute>().skill } ?: ExamSkill.VOCABULARY
                SkillDetailScreen(
                    viewModel = appViewModel {
                        SkillDetailViewModel(
                            skill,
                            ExamDashboardManager(examRepository, errorRepository, progressRepository, contentRepository, userPreferencesRepository, clock),
                        )
                    },
                    onOpenItem = { navController.navigateToItem(it) },
                    onOpenErrors = { navController.navigate(ErrorNotebookRoute(it.name)) },
                )
            }
        }
        composable<ReviewRoute> {
            ReviewScreen(
                viewModel = appViewModel { ReviewViewModel(progressRepository, userPreferencesRepository, clock, errorRepository) },
                onStartErrorReview = { navController.navigate(ErrorReviewRoute()) },
                onStartSession = { navController.navigate(ReviewSessionRoute(it)) },
                onOpenLessons = { navController.navigateToLessons() },
                onPracticeMarked = { navController.navigate(PracticeRoute(markedOnly = true)) },
            )
        }
        composable<ReviewSessionRoute> { entry ->
            val route = entry.toRoute<ReviewSessionRoute>()
            ReviewSessionScreen(
                viewModel = appViewModel {
                    ReviewSessionViewModel(
                        contentRepository, progressRepository, userPreferencesRepository,
                        SubmitReviewUseCase(progressRepository), reviewSessionRepository, clock,
                        scope = route.scope,
                    )
                },
                onFinished = { result ->
                    navController.navigate(
                        ReviewResultRoute(result.total, result.again, result.hard, result.good, result.easy, result.durationSeconds, route.type, route.mode),
                    ) { popUpTo<ReviewSessionRoute> { inclusive = true } }
                },
            )
        }
        composable<ReviewResultRoute> { entry ->
            val route = entry.toRoute<ReviewResultRoute>()
            PracticeResultScreen(
                result = PracticeResult(route.total, route.again, route.hard, route.good, route.easy, route.durationSeconds),
                onPracticeAgain = {
                    navController.navigate(ReviewSessionRoute(route.type, route.mode)) { popUpTo<ReviewResultRoute> { inclusive = true } }
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
                dashboardViewModel = appViewModel { DashboardViewModel(analyticsRepository, reviewSessionRepository, userPreferencesRepository, clock, progressRepository) },
                onOpenReview = { navController.navigateToTopLevel(TopLevelDestination.REVIEW) },
                onOpenList = { type, status -> navController.navigateToItemList(type, status) },
                onOpenItem = { ref -> navController.navigateToItem(ref) },
                onOpenJlpt = { navController.navigateToExamDashboard() },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                viewModel = appViewModel { SettingsViewModel(userPreferencesRepository, contentRepository, contentImportRepository) },
                backupViewModel = appViewModel { BackupViewModel(backupRepository, clock) },
                syncViewModel = appViewModel { AndroidSyncViewModel(syncRepository) },
                jlptAiReviewViewModel = appViewModel { JlptAiReviewViewModel(syncRepository) },
                onOpenReviewSettings = { navController.navigate(ReviewSettingsRoute) },
                onOpenReminderSettings = { navController.navigate(ReminderSettingsRoute) },
                onOpenWidgetSettings = { navController.navigate(WidgetSettingsRoute) },
            )
        }
        composable<WidgetSettingsRoute> {
            WidgetSettingsScreen(viewModel = appViewModel { WidgetSettingsViewModel(userPreferencesRepository, contentRepository, widgetUpdater) })
        }
        composable<ReminderSettingsRoute> {
            ReminderSettingsScreen(
                viewModel = appViewModel {
                    ReminderSettingsViewModel(userPreferencesRepository, reminderScheduler, reminderNotifier, reminderRunner, contentRepository)
                },
                onChooseLessons = { navController.navigate(ReminderLessonsRoute(it.name)) },
            )
        }
        composable<ReminderLessonsRoute> { entry ->
            val type = ItemType.entries.firstOrNull { it.name == entry.toRoute<ReminderLessonsRoute>().type } ?: ItemType.VOCABULARY
            ReminderLessonsScreen(
                viewModel = appViewModel { ReminderLessonsViewModel(type, userPreferencesRepository, contentRepository) },
            )
        }
        composable<ReviewSettingsRoute> {
            ReviewSettingsScreen(
                viewModel = appViewModel { ReviewSettingsViewModel(userPreferencesRepository) },
                onChooseKanjiLessons = { navController.navigate(KanjiReviewLessonsRoute) },
            )
        }
        composable<KanjiReviewLessonsRoute> {
            KanjiReviewLessonsScreen(viewModel = appViewModel { KanjiReviewLessonsViewModel(userPreferencesRepository, contentRepository) })
        }
    }
}

private fun PracticeResult.toRoute(practice: PracticeRoute) =
    PracticeResultRoute(
        practice.lessonId, practice.markedOnly, total, again, hard, good, easy, durationSeconds, practice.courseLesson, practice.vocabularyGroup,
    )

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

/** Where the mistake analysis links to: the existing detail, lesson and practice screens. */
private fun NavHostController.mistakeActions(mark: (List<ItemRef>, () -> Unit) -> Unit) = MistakeActions(
    onOpenItem = { ref ->
        when (ref.type) {
            ItemType.VOCABULARY -> navigate(VocabularyDetailRoute(ref.id))
            ItemType.KANJI -> navigate(KanjiDetailRoute(ref.id))
            ItemType.GRAMMAR -> navigate(GrammarDetailRoute(ref.id))
        }
    },
    onOpenLesson = { navigate(LessonDetailRoute(it)) },
    onPracticeWords = { refs -> mark(refs) { navigate(PracticeRoute(markedOnly = true)) } },
)
