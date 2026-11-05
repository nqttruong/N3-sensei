package com.n3sensei.app.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppScreenTest {

    @Test
    fun everyScreenHasItsOwnRoute() {
        val routes = AppScreen.entries.map { it.route }
        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun everyTabHasExactlyOneRootScreen() {
        TopLevelDestination.entries.forEach { tab ->
            val roots = AppScreen.entries.filter { it.tab == tab && it.isTabRoot }
            assertEquals("Tab $tab must have one root screen", 1, roots.size)
        }
    }

    @Test
    fun tabRootOfALeafTabIsTheTabRouteItself() {
        // Tabs whose route is a leaf (not a nested graph) must point at their own root screen.
        TopLevelDestination.entries
            .filter { it.route != LearnGraphRoute }
            .forEach { tab ->
                val root = AppScreen.entries.single { it.tab == tab && it.isTabRoot }
                assertEquals(tab.route::class, root.route)
            }
    }

    @Test
    fun requiredAreasAreRegistered() {
        val expected = setOf(
            HomeRoute::class, LearnHubRoute::class, LessonListRoute::class, LessonDetailRoute::class,
            VocabularyGroupsRoute::class, VocabularyGroupRoute::class, VocabularyRoute::class, MyWordsRoute::class, VocabularyDetailRoute::class, KanjiRoute::class, KanjiDetailRoute::class,
            KanjiEditRoute::class, VocabularyEditRoute::class, AddWordRoute::class,
            GrammarRoute::class, GrammarDetailRoute::class, GrammarEditRoute::class, PracticeRoute::class, PracticeResultRoute::class,
            ReviewRoute::class, ReviewSessionRoute::class, ReviewResultRoute::class,
            StatsRoute::class, SettingsRoute::class, ReviewSettingsRoute::class, ReminderSettingsRoute::class, WidgetSettingsRoute::class,
        )
        assertEquals(expected, AppScreen.entries.map { it.route }.toSet())
    }

    @Test
    fun homeAndStatsHaveNoSubScreens() {
        val nonRootTabs = AppScreen.entries.filterNot { it.isTabRoot }.map { it.tab }.toSet()
        assertEquals(setOf(TopLevelDestination.LEARN, TopLevelDestination.REVIEW, TopLevelDestination.SETTINGS), nonRootTabs)
        assertTrue(TopLevelDestination.HOME !in nonRootTabs)
    }
}
