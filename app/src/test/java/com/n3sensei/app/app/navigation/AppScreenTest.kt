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
            HomeRoute::class, LearnHubRoute::class, VocabularyRoute::class, KanjiRoute::class,
            GrammarRoute::class, ReviewRoute::class, StatsRoute::class, SettingsRoute::class,
        )
        assertEquals(expected, AppScreen.entries.map { it.route }.toSet())
    }

    @Test
    fun nonRootScreensBelongToLearnTab() {
        val nonRoots = AppScreen.entries.filterNot { it.isTabRoot }
        assertTrue(nonRoots.all { it.tab == TopLevelDestination.LEARN })
    }
}
