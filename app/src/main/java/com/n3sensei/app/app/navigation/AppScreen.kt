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
) {
    HOME(HomeRoute::class, R.string.app_name, TopLevelDestination.HOME, isTabRoot = true),
    LEARN_HUB(LearnHubRoute::class, R.string.learn_title, TopLevelDestination.LEARN, isTabRoot = true),
    VOCABULARY(VocabularyRoute::class, R.string.vocabulary_title, TopLevelDestination.LEARN, isTabRoot = false),
    KANJI(KanjiRoute::class, R.string.kanji_title, TopLevelDestination.LEARN, isTabRoot = false),
    GRAMMAR(GrammarRoute::class, R.string.grammar_title, TopLevelDestination.LEARN, isTabRoot = false),
    REVIEW(ReviewRoute::class, R.string.review_title, TopLevelDestination.REVIEW, isTabRoot = true),
    STATS(StatsRoute::class, R.string.stats_title, TopLevelDestination.STATS, isTabRoot = true),
    SETTINGS(SettingsRoute::class, R.string.settings_title, TopLevelDestination.SETTINGS, isTabRoot = true);

    fun matches(destination: NavDestination): Boolean = destination.hasRoute(route)

    companion object {
        fun from(destination: NavDestination?): AppScreen? =
            destination?.let { dest -> entries.firstOrNull { it.matches(dest) } }
    }
}
