package com.n3sensei.app.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TopLevelDestinationTest {

    @Test
    fun bottomBarHasFiveTabsInOrder() {
        assertEquals(
            listOf(HomeRoute, LearnGraphRoute, ReviewRoute, StatsRoute, SettingsRoute),
            TopLevelDestination.entries.map { it.route },
        )
    }

    @Test
    fun phoneBottomBarHasAtMostFiveTabs() {
        assertTrue(TopLevelDestination.entries.size <= 5)
    }

    @Test
    fun routesAndTestTagsAreUnique() {
        val routes = TopLevelDestination.entries.map { it.route }
        assertEquals(routes.size, routes.toSet().size)
        val tags = TopLevelDestination.entries.map { it.testTag }
        assertEquals(tags.size, tags.toSet().size)
    }

    @Test
    fun startDestinationIsHome() {
        assertEquals(TopLevelDestination.HOME, TopLevelDestination.START)
    }
}
