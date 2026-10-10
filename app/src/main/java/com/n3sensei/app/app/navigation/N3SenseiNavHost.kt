package com.n3sensei.app.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.n3sensei.app.feature.learn.LearnScreen
import com.n3sensei.app.feature.review.ReviewScreen
import com.n3sensei.app.feature.settings.SettingsScreen
import com.n3sensei.app.feature.stats.StatsScreen

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
        composable(TopLevelDestination.LEARN.route) { LearnScreen() }
        composable(TopLevelDestination.REVIEW.route) { ReviewScreen() }
        composable(TopLevelDestination.STATS.route) { StatsScreen() }
        composable(TopLevelDestination.SETTINGS.route) { SettingsScreen() }
    }
}
