package com.n3sensei.app.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.n3sensei.app.feature.grammar.GrammarScreen
import com.n3sensei.app.feature.home.HomeScreen
import com.n3sensei.app.feature.kanji.KanjiScreen
import com.n3sensei.app.feature.learn.LearnHubScreen
import com.n3sensei.app.feature.review.ReviewScreen
import com.n3sensei.app.feature.settings.SettingsScreen
import com.n3sensei.app.feature.stats.StatsScreen
import com.n3sensei.app.feature.vocabulary.VocabularyScreen

/**
 * The whole navigation graph. Feature screens receive navigation callbacks, never the
 * NavController, so they stay previewable and testable in isolation.
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
                onOpenLearn = { navController.navigateToTopLevel(TopLevelDestination.LEARN) },
                onOpenReview = { navController.navigateToTopLevel(TopLevelDestination.REVIEW) },
            )
        }
        navigation<LearnGraphRoute>(startDestination = LearnHubRoute) {
            composable<LearnHubRoute> {
                LearnHubScreen(
                    onOpenVocabulary = { navController.navigate(VocabularyRoute) },
                    onOpenKanji = { navController.navigate(KanjiRoute) },
                    onOpenGrammar = { navController.navigate(GrammarRoute) },
                )
            }
            composable<VocabularyRoute> { VocabularyScreen() }
            composable<KanjiRoute> { KanjiScreen() }
            composable<GrammarRoute> { GrammarScreen() }
        }
        composable<ReviewRoute> { ReviewScreen() }
        composable<StatsRoute> { StatsScreen() }
        composable<SettingsRoute> { SettingsScreen() }
    }
}
